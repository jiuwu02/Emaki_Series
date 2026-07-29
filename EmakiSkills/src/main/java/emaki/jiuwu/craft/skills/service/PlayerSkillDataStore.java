package emaki.jiuwu.craft.skills.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.async.AsyncFailures;
import emaki.jiuwu.craft.corelib.async.AsyncFileService.DrainResult;
import emaki.jiuwu.craft.corelib.yaml.AsyncYamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import emaki.jiuwu.craft.skills.model.PlayerCastTimingState;
import emaki.jiuwu.craft.skills.model.PlayerLocalResourceState;
import emaki.jiuwu.craft.skills.model.PlayerSkillLevelState;
import emaki.jiuwu.craft.skills.model.PlayerSkillProfile;
import emaki.jiuwu.craft.skills.model.SkillSlotBinding;

public final class PlayerSkillDataStore {

    public record SessionTicket(UUID playerId, long generation) {
    }

    public record FlushResult(int savedEntries,
            int failedEntries,
            int remainingDirtyEntries,
            DrainResult drainResult) {

        public boolean clean() {
            return failedEntries == 0
                    && remainingDirtyEntries == 0
                    && drainResult != null
                    && drainResult.drained()
                    && drainResult.failures().isEmpty();
        }
    }

    private final JavaPlugin plugin;
    private final int defaultSlotCount;
    private final Supplier<AsyncYamlFiles> asyncYamlFilesSupplier;
    private final PlayerSkillProfileCache cache = new PlayerSkillProfileCache();
    private FlushResult flushResult;

    public PlayerSkillDataStore(JavaPlugin plugin, int defaultSlotCount) {
        this(plugin, defaultSlotCount, null);
    }

    public PlayerSkillDataStore(JavaPlugin plugin,
            int defaultSlotCount,
            Supplier<AsyncYamlFiles> asyncYamlFilesSupplier) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.defaultSlotCount = Math.max(1, defaultSlotCount);
        this.asyncYamlFilesSupplier = asyncYamlFilesSupplier;
    }

    public CompletableFuture<PlayerSkillProfile> loadAsync(Player player) {
        return player == null
                ? CompletableFuture.completedFuture(null)
                : beginSession(player.getUniqueId());
    }

    public CompletableFuture<PlayerSkillProfile> beginSession(UUID playerId) {
        if (playerId == null) {
            return CompletableFuture.completedFuture(null);
        }
        File file = profileFile(playerId);
        boolean existingFile = file.isFile();
        PlayerSkillProfileCache.SessionTicket ticket = cache.beginSession(
                playerId,
                createDefault(playerId),
                !existingFile
        );
        if (ticket == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (!existingFile) {
            PlayerSkillProfile created = createDefault(playerId);
            cache.installLoaded(ticket, created);
            return CompletableFuture.completedFuture(created);
        }

        AsyncYamlFiles asyncYamlFiles = asyncYamlFiles();
        if (asyncYamlFiles == null) {
            return CompletableFuture.completedFuture(loadSynchronously(ticket, file));
        }
        return asyncYamlFiles.load(file)
                .thenApply(section -> {
                    PlayerSkillProfile loaded = readProfileFromSection(playerId, section);
                    return cache.installLoaded(ticket, loaded) == PlayerSkillProfileCache.CommitResult.COMMITTED
                            ? loaded
                            : cache.profile(playerId);
                })
                .exceptionally(throwable -> {
                    logLoadFailure(playerId, AsyncFailures.unwrapOnce(throwable));
                    cache.installLoadFailure(ticket, createDefault(playerId));
                    return null;
                });
    }

    public long currentGeneration(UUID playerId) {
        return playerId == null ? 0L : cache.generation(playerId);
    }

    public SessionTicket currentSession(UUID playerId) {
        PlayerSkillProfileCache.SessionTicket ticket = playerId == null ? null : cache.currentTicket(playerId);
        return ticket == null ? null : new SessionTicket(playerId, ticket.generation());
    }

    public boolean isCurrent(SessionTicket ticket) {
        return cache.isCurrent(resolve(ticket));
    }

    public boolean isCurrentGeneration(UUID playerId, long generation) {
        SessionTicket current = currentSession(playerId);
        return current != null && current.generation() == generation;
    }

    public boolean isSessionWritable(UUID playerId) {
        return currentSession(playerId) != null;
    }

    public PlayerSkillProfile get(Player player) {
        return player == null ? null : cache.profile(player.getUniqueId());
    }

    public PlayerSkillProfile get(SessionTicket ticket) {
        return cache.profile(resolve(ticket));
    }

    public <R> R readIfCurrent(SessionTicket ticket, Function<PlayerSkillProfile, R> reader) {
        return cache.readIfCurrent(resolve(ticket), reader);
    }

    public boolean mutateIfCurrent(SessionTicket ticket, Consumer<PlayerSkillProfile> mutation) {
        return cache.mutateIfCurrent(resolve(ticket), mutation);
    }

    public boolean mutate(Player player, Consumer<PlayerSkillProfile> mutation) {
        return player != null && mutateIfCurrent(currentSession(player.getUniqueId()), mutation);
    }

    public CompletableFuture<Void> saveAsync(UUID playerId) {
        if (playerId == null || cache.isSealed()) {
            return CompletableFuture.completedFuture(null);
        }
        PlayerSkillProfileCache.SaveTicket ticket = cache.snapshotForSave(playerId, 0L, false);
        return saveTicket(ticket).thenApply(ignored -> null);
    }

    public CompletableFuture<Void> saveAllAsync() {
        if (cache.isSealed()) {
            return CompletableFuture.completedFuture(null);
        }
        List<PlayerSkillProfileCache.SaveTicket> tickets = cache.snapshotDirtyEntries();
        List<CompletableFuture<Boolean>> futures = saveTickets(tickets);
        return futures.isEmpty()
                ? CompletableFuture.completedFuture(null)
                : CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    public CompletableFuture<Void> unloadAsync(UUID playerId) {
        return unloadAsync(playerId, 0L);
    }

    public CompletableFuture<Void> unloadAsync(UUID playerId, long expectedGeneration) {
        if (playerId == null || cache.isSealed()) {
            return CompletableFuture.completedFuture(null);
        }
        PlayerSkillProfileCache.SaveTicket ticket = cache.snapshotForSave(
                playerId,
                expectedGeneration,
                true
        );
        return saveTicket(ticket).thenApply(ignored -> null);
    }

    public CompletableFuture<Void> waitForPendingSaves() {
        CompletableFuture<Void> logicalIdle = cache.waitForIdle();
        AsyncYamlFiles asyncYamlFiles = asyncYamlFiles();
        return asyncYamlFiles == null
                ? logicalIdle
                : CompletableFuture.allOf(logicalIdle, asyncYamlFiles.waitForIdle());
    }

    public synchronized FlushResult flushAndSeal(long timeout, TimeUnit unit) {
        if (flushResult != null) {
            return flushResult;
        }
        Objects.requireNonNull(unit, "unit");
        long deadline = System.nanoTime() + Math.max(0L, unit.toNanos(timeout));
        cache.seal();

        List<PlayerSkillProfileCache.SaveTicket> tickets = cache.snapshotDirtyEntries();
        List<CompletableFuture<Boolean>> saveFutures = saveTickets(tickets);
        CompletableFuture<Void> savesComplete = saveFutures.isEmpty()
                ? CompletableFuture.completedFuture(null)
                : CompletableFuture.allOf(saveFutures.toArray(CompletableFuture[]::new));
        awaitUntil(savesComplete, deadline);

        int savedEntries = 0;
        for (CompletableFuture<Boolean> saveFuture : saveFutures) {
            if (saveFuture.isDone()
                    && !saveFuture.isCompletedExceptionally()
                    && Boolean.TRUE.equals(saveFuture.getNow(false))) {
                savedEntries++;
            }
        }
        int failedEntries = tickets.size() - savedEntries;

        AsyncYamlFiles asyncYamlFiles = asyncYamlFiles();
        DrainResult drainResult;
        if (asyncYamlFiles == null) {
            int pending = (int) saveFutures.stream().filter(future -> !future.isDone()).count();
            drainResult = new DrainResult(pending == 0, pending, List.of());
        } else {
            long remainingNanos = Math.max(0L, deadline - System.nanoTime());
            drainResult = asyncYamlFiles.sealAndDrain(remainingNanos, TimeUnit.NANOSECONDS);
        }
        flushResult = new FlushResult(savedEntries, failedEntries, cache.dirtyCount(), drainResult);
        return flushResult;
    }

    public PlayerSkillProfile load(Player player) {
        if (player == null || cache.isSealed()) {
            return null;
        }
        UUID playerId = player.getUniqueId();
        File file = profileFile(playerId);
        boolean existingFile = file.isFile();
        PlayerSkillProfileCache.SessionTicket ticket = cache.beginSession(
                playerId,
                createDefault(playerId),
                !existingFile
        );
        if (ticket == null) {
            return null;
        }
        if (!existingFile) {
            PlayerSkillProfile created = createDefault(playerId);
            cache.installLoaded(ticket, created);
            return created;
        }
        return loadSynchronously(ticket, file);
    }

    public void save(Player player) {
        if (player != null) {
            saveAsync(player.getUniqueId());
        }
    }

    public void saveAll() {
        saveAllAsync();
    }

    public void unload(UUID playerId) {
        unloadAsync(playerId);
    }

    public void unloadAll() {
        saveAllAsync();
    }

    public PlayerSkillProfile createDefault(UUID playerId) {
        PlayerSkillProfile profile = new PlayerSkillProfile(
                playerId == null ? "" : playerId.toString(),
                defaultSlotCount
        );
        profile.setCastModeEnabled(false);
        profile.clearDirty();
        return profile;
    }

    private List<CompletableFuture<Boolean>> saveTickets(List<PlayerSkillProfileCache.SaveTicket> tickets) {
        List<CompletableFuture<Boolean>> futures = new ArrayList<>(tickets.size());
        for (PlayerSkillProfileCache.SaveTicket ticket : tickets) {
            futures.add(saveTicket(ticket));
        }
        return futures;
    }

    private CompletableFuture<Boolean> saveTicket(PlayerSkillProfileCache.SaveTicket ticket) {
        if (ticket == null) {
            return CompletableFuture.completedFuture(true);
        }
        return cache.enqueueSave(ticket, current -> writeSnapshot(
                current.playerId(),
                serializeProfile(current.playerId(), current.snapshot())
        ));
    }

    private CompletableFuture<Boolean> writeSnapshot(UUID playerId, Map<String, Object> snapshot) {
        File file = profileFile(playerId);
        AsyncYamlFiles asyncYamlFiles = asyncYamlFiles();
        if (asyncYamlFiles == null) {
            try {
                YamlFiles.save(file, snapshot);
                return CompletableFuture.completedFuture(true);
            } catch (IOException exception) {
                logSaveFailure(playerId, exception);
                return CompletableFuture.completedFuture(false);
            }
        }
        return asyncYamlFiles.save(file, snapshot)
                .thenApply(ignored -> true)
                .exceptionally(throwable -> {
                    logSaveFailure(playerId, AsyncFailures.unwrapOnce(throwable));
                    return false;
                });
    }

    private PlayerSkillProfile loadSynchronously(PlayerSkillProfileCache.SessionTicket ticket, File file) {
        try {
            PlayerSkillProfile loaded = readProfileFromSection(ticket.playerId(), YamlFiles.load(file));
            return cache.installLoaded(ticket, loaded) == PlayerSkillProfileCache.CommitResult.COMMITTED
                    ? loaded
                    : cache.profile(ticket.playerId());
        } catch (RuntimeException exception) {
            logLoadFailure(ticket.playerId(), exception);
            cache.installLoadFailure(ticket, createDefault(ticket.playerId()));
            return null;
        }
    }

    private Map<String, Object> serializeProfile(UUID playerId, PlayerSkillProfile profile) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("uuid", playerId.toString());
        root.put("cast_mode_enabled", profile.castModeEnabled());

        List<Map<String, Object>> bindingsList = new ArrayList<>();
        for (SkillSlotBinding binding : profile.bindings()) {
            if (binding == null || binding.isEmpty()) {
                continue;
            }
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("slot", binding.slotIndex());
            map.put("skill_id", binding.skillId());
            map.put("trigger_id", binding.triggerId());
            bindingsList.add(map);
        }
        root.put("bindings", bindingsList);

        Map<String, Object> resourcesMap = new LinkedHashMap<>();
        for (Map.Entry<String, PlayerLocalResourceState> entry : profile.localResources().entrySet()) {
            PlayerLocalResourceState state = entry.getValue();
            if (state == null) {
                continue;
            }
            Map<String, Object> resource = new LinkedHashMap<>();
            resource.put("current_value", state.currentValue());
            resource.put("last_regen_at", state.lastRegenAt());
            resourcesMap.put(entry.getKey(), resource);
        }
        if (!resourcesMap.isEmpty()) {
            root.put("local_resources", resourcesMap);
        }

        Map<String, Object> levelsMap = new LinkedHashMap<>();
        for (Map.Entry<String, PlayerSkillLevelState> entry : profile.skillLevels().entrySet()) {
            PlayerSkillLevelState state = entry.getValue();
            if (state == null || state.level() <= 1) {
                continue;
            }
            levelsMap.put(entry.getKey(), Map.of("level", state.level()));
        }
        if (!levelsMap.isEmpty()) {
            root.put("skill_levels", levelsMap);
        }

        if (!profile.manualSkillIds().isEmpty()) {
            root.put("manual_skill_ids", new ArrayList<>(profile.manualSkillIds()));
        }

        PlayerCastTimingState timing = profile.timingState();
        Map<String, Object> timingMap = new LinkedHashMap<>();
        timingMap.put("forced_global_cast_delay_until", timing.forcedGlobalCastDelayUntil());
        timingMap.put("global_cooldown_until", timing.globalCooldownUntil());
        if (!timing.skillCooldownUntilBySkillId().isEmpty()) {
            timingMap.put("skill_cooldowns", new LinkedHashMap<>(timing.skillCooldownUntilBySkillId()));
        }
        root.put("timing", timingMap);
        return root;
    }

    private PlayerSkillProfile readProfileFromSection(UUID playerId, YamlSection section) {
        PlayerSkillProfile profile = new PlayerSkillProfile(playerId.toString(), defaultSlotCount);
        profile.setCastModeEnabled(section.getBoolean("cast_mode_enabled", false));

        List<?> bindingsList = section.getList("bindings");
        if (bindingsList != null) {
            for (Object value : bindingsList) {
                if (!(value instanceof Map<?, ?> map)) {
                    continue;
                }
                int slot = toInt(map.get("slot"), -1);
                String skillId = toStringOrNull(map.get("skill_id"));
                String triggerId = toStringOrNull(map.get("trigger_id"));
                if (slot >= 0 && slot < defaultSlotCount) {
                    profile.setBinding(slot, new SkillSlotBinding(slot, skillId, triggerId));
                }
            }
        }

        YamlSection resourcesSection = section.getSection("local_resources");
        if (resourcesSection != null) {
            for (String key : resourcesSection.getKeys(false)) {
                YamlSection resource = resourcesSection.getSection(key);
                if (resource != null) {
                    profile.localResources().put(key, new PlayerLocalResourceState(
                            key,
                            resource.getDouble("current_value", 0D),
                            toLong(resource.get("last_regen_at"), 0L)
                    ));
                }
            }
        }

        YamlSection skillLevelsSection = section.getSection("skill_levels");
        if (skillLevelsSection != null) {
            for (String skillId : skillLevelsSection.getKeys(false)) {
                YamlSection levelSection = skillLevelsSection.getSection(skillId);
                int level = levelSection == null ? 1 : levelSection.getInt("level", 1);
                profile.skillLevels().put(skillId, new PlayerSkillLevelState(skillId, level));
            }
        }

        List<?> manualSkillIds = section.getList("manual_skill_ids");
        if (manualSkillIds != null) {
            for (Object value : manualSkillIds) {
                String skillId = toStringOrNull(value);
                if (skillId != null) {
                    profile.manualSkillIds().add(skillId);
                }
            }
        }

        YamlSection timingSection = section.getSection("timing");
        if (timingSection != null) {
            PlayerCastTimingState timing = profile.timingState();
            timing.setForcedGlobalCastDelayUntil(
                    toLong(timingSection.get("forced_global_cast_delay_until"), 0L));
            timing.setGlobalCooldownUntil(
                    toLong(timingSection.get("global_cooldown_until"), 0L));
            YamlSection cooldowns = timingSection.getSection("skill_cooldowns");
            if (cooldowns != null) {
                for (String skillId : cooldowns.getKeys(false)) {
                    timing.skillCooldownUntilBySkillId().put(
                            skillId,
                            toLong(cooldowns.get(skillId), 0L)
                    );
                }
            }
        }

        profile.clearDirty();
        return profile;
    }

    private PlayerSkillProfileCache.SessionTicket resolve(SessionTicket ticket) {
        if (ticket == null || ticket.playerId() == null) {
            return null;
        }
        PlayerSkillProfileCache.SessionTicket current = cache.currentTicket(ticket.playerId());
        return current != null && current.generation() == ticket.generation() ? current : null;
    }

    private boolean awaitUntil(CompletableFuture<?> future, long deadline) {
        if (future.isDone()) {
            return true;
        }
        long remainingNanos = deadline - System.nanoTime();
        if (remainingNanos <= 0L) {
            return false;
        }
        CountDownLatch latch = new CountDownLatch(1);
        future.whenComplete((ignored, throwable) -> latch.countDown());
        try {
            return future.isDone() || latch.await(remainingNanos, TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private File profileFile(UUID playerId) {
        File dataDirectory = new File(plugin.getDataFolder(), "data");
        if (!dataDirectory.exists() && !dataDirectory.mkdirs()) {
            plugin.getLogger().warning("[SkillDataStore] Unable to create data directory: " + dataDirectory);
        }
        return new File(dataDirectory, playerId + ".yml");
    }

    private AsyncYamlFiles asyncYamlFiles() {
        return asyncYamlFilesSupplier == null ? null : asyncYamlFilesSupplier.get();
    }

    private void logLoadFailure(UUID playerId, Throwable throwable) {
        plugin.getLogger().log(Level.WARNING,
                "[SkillDataStore] Failed to load " + playerId
                        + "; this session remains read-only to protect the existing file",
                throwable);
    }

    private void logSaveFailure(UUID playerId, Throwable throwable) {
        plugin.getLogger().log(Level.WARNING,
                "[SkillDataStore] Failed to save profile for " + playerId,
                throwable);
    }

    private static int toInt(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static long toLong(Object value, long fallback) {
        return value instanceof Number number ? number.longValue() : fallback;
    }

    private static String toStringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String string = value.toString();
        return string.isBlank() ? null : string;
    }
}
