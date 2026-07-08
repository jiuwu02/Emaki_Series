package emaki.jiuwu.craft.skills.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.yaml.AsyncYamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.skills.model.PlayerCastTimingState;
import emaki.jiuwu.craft.skills.model.PlayerLocalResourceState;
import emaki.jiuwu.craft.skills.model.PlayerSkillLevelState;
import emaki.jiuwu.craft.skills.model.PlayerSkillProfile;
import emaki.jiuwu.craft.skills.model.SkillSlotBinding;

public final class PlayerSkillDataStore {

    private final JavaPlugin plugin;
    private final int defaultSlotCount;
    private final Supplier<AsyncYamlFiles> asyncYamlFilesSupplier;
    private final Map<UUID, PlayerSkillProfile> cache = new ConcurrentHashMap<>();

    public PlayerSkillDataStore(JavaPlugin plugin, int defaultSlotCount) {
        this(plugin, defaultSlotCount, null);
    }

    public PlayerSkillDataStore(JavaPlugin plugin, int defaultSlotCount, Supplier<AsyncYamlFiles> asyncYamlFilesSupplier) {
        this.plugin = plugin;
        this.defaultSlotCount = Math.max(1, defaultSlotCount);
        this.asyncYamlFilesSupplier = asyncYamlFilesSupplier;
    }


    public CompletableFuture<PlayerSkillProfile> loadAsync(Player player) {
        if (player == null) {
            return CompletableFuture.completedFuture(null);
        }
        UUID uuid = player.getUniqueId();
        File file = profileFile(uuid);
        if (!file.exists()) {
            PlayerSkillProfile profile = createDefault(uuid);
            cache.put(uuid, profile);
            return CompletableFuture.completedFuture(profile);
        }

        AsyncYamlFiles asyncYamlFiles = asyncYamlFiles();
        if (asyncYamlFiles == null) {
            return CompletableFuture.completedFuture(load(player));
        }

        return asyncYamlFiles.load(file)
                .thenApply(section -> {
                    PlayerSkillProfile profile = readProfileFromSection(uuid, section);
                    cache.put(uuid, profile);
                    return profile;
                })
                .exceptionally(throwable -> {
                    plugin.getLogger().log(Level.WARNING,
                            "[SkillDataStore] Async load failed for " + uuid + ", falling back to default", unwrap(throwable));
                    PlayerSkillProfile fallback = createDefault(uuid);
                    cache.put(uuid, fallback);
                    return fallback;
                });
    }

    public CompletableFuture<Void> saveAsync(UUID uuid) {
        if (uuid == null) {
            return CompletableFuture.completedFuture(null);
        }
        PlayerSkillProfile profile = cache.get(uuid);
        if (profile == null || !profile.isDirty()) {
            return CompletableFuture.completedFuture(null);
        }

        Map<String, Object> dataSnapshot = serializeProfile(uuid, profile);
        File file = profileFile(uuid);

        AsyncYamlFiles asyncYamlFiles = asyncYamlFiles();
        if (asyncYamlFiles == null) {
            saveProfileSync(uuid, profile);
            return CompletableFuture.completedFuture(null);
        }

        profile.clearDirty();
        return asyncYamlFiles.save(file, dataSnapshot)
                .exceptionally(throwable -> {
                    plugin.getLogger().log(Level.WARNING,
                            "[SkillDataStore] Async save failed for " + uuid, unwrap(throwable));
                    profile.markDirty();
                    return null;
                });
    }

    public CompletableFuture<Void> saveAllAsync() {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (UUID uuid : cache.keySet()) {
            PlayerSkillProfile profile = cache.get(uuid);
            if (profile != null && profile.isDirty()) {
                futures.add(saveAsync(uuid));
            }
        }
        if (futures.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    public CompletableFuture<Void> unloadAsync(UUID uuid) {
        if (uuid == null) {
            return CompletableFuture.completedFuture(null);
        }
        PlayerSkillProfile profile = cache.remove(uuid);
        if (profile != null && profile.isDirty()) {
            cache.put(uuid, profile);
            return saveAsync(uuid).thenRun(() -> cache.remove(uuid));
        }
        return CompletableFuture.completedFuture(null);
    }

    public void waitForPendingSaves() {
        AsyncYamlFiles asyncYamlFiles = asyncYamlFiles();
        if (asyncYamlFiles != null) {
            asyncYamlFiles.waitForIdle().join();
        }
    }


    public PlayerSkillProfile load(Player player) {
        if (player == null) {
            return null;
        }
        UUID uuid = player.getUniqueId();
        File file = profileFile(uuid);
        if (!file.exists()) {
            PlayerSkillProfile profile = createDefault(uuid);
            cache.put(uuid, profile);
            return profile;
        }

        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            PlayerSkillProfile profile = readProfile(uuid, yaml);
            cache.put(uuid, profile);
            return profile;
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING,
                    "[SkillDataStore] Failed to load profile for " + uuid, exception);
            PlayerSkillProfile fallback = createDefault(uuid);
            cache.put(uuid, fallback);
            return fallback;
        }
    }

    public PlayerSkillProfile get(Player player) {
        if (player == null) {
            return null;
        }
        PlayerSkillProfile cached = cache.get(player.getUniqueId());
        return cached != null ? cached : load(player);
    }

    public void save(Player player) {
        if (player == null) {
            return;
        }
        PlayerSkillProfile profile = cache.get(player.getUniqueId());
        if (profile == null || !profile.isDirty()) {
            return;
        }
        saveProfileSync(player.getUniqueId(), profile);
    }

    public void saveAll() {
        for (Map.Entry<UUID, PlayerSkillProfile> entry : cache.entrySet()) {
            PlayerSkillProfile profile = entry.getValue();
            if (profile.isDirty()) {
                saveProfileSync(entry.getKey(), profile);
            }
        }
    }

    public void unload(UUID uuid) {
        if (uuid == null) {
            return;
        }
        PlayerSkillProfile profile = cache.remove(uuid);
        if (profile != null && profile.isDirty()) {
            saveProfileSync(uuid, profile);
        }
    }

    public void unloadAll() {
        saveAll();
        cache.clear();
    }

    public PlayerSkillProfile createDefault(UUID uuid) {
        PlayerSkillProfile profile = new PlayerSkillProfile(
                uuid == null ? "" : uuid.toString(), defaultSlotCount);
        profile.setCastModeEnabled(false);
        return profile;
    }


    private Map<String, Object> serializeProfile(UUID uuid, PlayerSkillProfile profile) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("uuid", uuid.toString());
        root.put("cast_mode_enabled", profile.castModeEnabled());

        List<Map<String, Object>> bindingsList = new ArrayList<>();
        for (SkillSlotBinding binding : profile.bindings()) {
            if (binding.isEmpty()) {
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
            Map<String, Object> resMap = new LinkedHashMap<>();
            PlayerLocalResourceState state = entry.getValue();
            resMap.put("current_value", state.currentValue());
            resMap.put("last_regen_at", state.lastRegenAt());
            resourcesMap.put(entry.getKey(), resMap);
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
            Map<String, Object> levelEntry = new LinkedHashMap<>();
            levelEntry.put("level", state.level());
            levelsMap.put(entry.getKey(), levelEntry);
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
        Map<String, Object> cooldownsMap = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : timing.skillCooldownUntilBySkillId().entrySet()) {
            cooldownsMap.put(entry.getKey(), entry.getValue());
        }
        if (!cooldownsMap.isEmpty()) {
            timingMap.put("skill_cooldowns", cooldownsMap);
        }
        root.put("timing", timingMap);

        return root;
    }


    private File profileFile(UUID uuid) {
        File dataDir = new File(plugin.getDataFolder(), "data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        return new File(dataDir, uuid.toString() + ".yml");
    }

    private PlayerSkillProfile readProfileFromSection(UUID uuid, emaki.jiuwu.craft.corelib.yaml.YamlSection section) {
        int slotCount = defaultSlotCount;
        PlayerSkillProfile profile = new PlayerSkillProfile(uuid.toString(), slotCount);

        profile.setCastModeEnabled(section.getBoolean("cast_mode_enabled", false));

        List<?> bindingsList = section.getList("bindings");
        if (bindingsList != null) {
            for (Object obj : bindingsList) {
                if (!(obj instanceof Map<?, ?> map)) {
                    continue;
                }
                int slot = toInt(map.get("slot"), -1);
                String skillId = toStringOrNull(map.get("skill_id"));
                String triggerId = toStringOrNull(map.get("trigger_id"));
                if (slot >= 0 && slot < slotCount) {
                    profile.setBinding(slot, new SkillSlotBinding(slot, skillId, triggerId));
                }
            }
        }

        emaki.jiuwu.craft.corelib.yaml.YamlSection resourcesSection = section.getSection("local_resources");
        if (resourcesSection != null) {
            for (String key : resourcesSection.getKeys(false)) {
                emaki.jiuwu.craft.corelib.yaml.YamlSection resSection = resourcesSection.getSection(key);
                if (resSection == null) {
                    continue;
                }
                double currentValue = resSection.getDouble("current_value", 0D);
                long lastRegenAt = toLong(resSection.get("last_regen_at"), 0L);
                profile.localResources().put(key,
                        new PlayerLocalResourceState(key, currentValue, lastRegenAt));
            }
        }

        emaki.jiuwu.craft.corelib.yaml.YamlSection skillLevelsSection = section.getSection("skill_levels");
        if (skillLevelsSection != null) {
            for (String skillId : skillLevelsSection.getKeys(false)) {
                emaki.jiuwu.craft.corelib.yaml.YamlSection levelSection = skillLevelsSection.getSection(skillId);
                int level = levelSection != null ? levelSection.getInt("level", 1) : 1;
                profile.skillLevels().put(skillId, new PlayerSkillLevelState(skillId, level));
            }
        }

        List<?> manualSkillIds = section.getList("manual_skill_ids");
        if (manualSkillIds != null) {
            for (Object obj : manualSkillIds) {
                String skillId = toStringOrNull(obj);
                if (skillId != null) {
                    profile.manualSkillIds().add(skillId);
                }
            }
        }

        emaki.jiuwu.craft.corelib.yaml.YamlSection timingSection = section.getSection("timing");
        if (timingSection != null) {
            PlayerCastTimingState timing = profile.timingState();
            timing.setForcedGlobalCastDelayUntil(
                    toLong(timingSection.get("forced_global_cast_delay_until"), 0L));
            timing.setGlobalCooldownUntil(
                    toLong(timingSection.get("global_cooldown_until"), 0L));
            emaki.jiuwu.craft.corelib.yaml.YamlSection cdSection = timingSection.getSection("skill_cooldowns");
            if (cdSection != null) {
                for (String skillId : cdSection.getKeys(false)) {
                    timing.skillCooldownUntilBySkillId().put(skillId, toLong(cdSection.get(skillId), 0L));
                }
            }
        }

        profile.clearDirty();
        return profile;
    }

    private PlayerSkillProfile readProfile(UUID uuid, YamlConfiguration yaml) {
        int slotCount = defaultSlotCount;
        PlayerSkillProfile profile = new PlayerSkillProfile(uuid.toString(), slotCount);

        profile.setCastModeEnabled(yaml.getBoolean("cast_mode_enabled", false));

        List<?> bindingsList = yaml.getList("bindings");
        if (bindingsList != null) {
            for (Object obj : bindingsList) {
                if (!(obj instanceof Map<?, ?> map)) {
                    continue;
                }
                int slot = toInt(map.get("slot"), -1);
                String skillId = toStringOrNull(map.get("skill_id"));
                String triggerId = toStringOrNull(map.get("trigger_id"));
                if (slot >= 0 && slot < slotCount) {
                    profile.setBinding(slot, new SkillSlotBinding(slot, skillId, triggerId));
                }
            }
        }

        ConfigurationSection resourcesSection = yaml.getConfigurationSection("local_resources");
        if (resourcesSection != null) {
            for (String key : resourcesSection.getKeys(false)) {
                ConfigurationSection resSection = resourcesSection.getConfigurationSection(key);
                if (resSection == null) {
                    continue;
                }
                double currentValue = resSection.getDouble("current_value", 0D);
                long lastRegenAt = resSection.getLong("last_regen_at", 0L);
                profile.localResources().put(key,
                        new PlayerLocalResourceState(key, currentValue, lastRegenAt));
            }
        }

        ConfigurationSection skillLevelsSection = yaml.getConfigurationSection("skill_levels");
        if (skillLevelsSection != null) {
            for (String skillId : skillLevelsSection.getKeys(false)) {
                int level = skillLevelsSection.getInt(skillId + ".level", 1);
                profile.skillLevels().put(skillId, new PlayerSkillLevelState(skillId, level));
            }
        }

        List<?> manualSkillIds = yaml.getList("manual_skill_ids");
        if (manualSkillIds != null) {
            for (Object obj : manualSkillIds) {
                String skillId = toStringOrNull(obj);
                if (skillId != null) {
                    profile.manualSkillIds().add(skillId);
                }
            }
        }

        ConfigurationSection timingSection = yaml.getConfigurationSection("timing");
        if (timingSection != null) {
            PlayerCastTimingState timing = profile.timingState();
            timing.setForcedGlobalCastDelayUntil(
                    timingSection.getLong("forced_global_cast_delay_until", 0L));
            timing.setGlobalCooldownUntil(
                    timingSection.getLong("global_cooldown_until", 0L));
            ConfigurationSection cdSection = timingSection.getConfigurationSection("skill_cooldowns");
            if (cdSection != null) {
                for (String skillId : cdSection.getKeys(false)) {
                    timing.skillCooldownUntilBySkillId().put(skillId, cdSection.getLong(skillId, 0L));
                }
            }
        }

        profile.clearDirty();
        return profile;
    }

    private void saveProfileSync(UUID uuid, PlayerSkillProfile profile) {
        File file = profileFile(uuid);
        YamlConfiguration yaml = new YamlConfiguration();

        yaml.set("uuid", uuid.toString());
        yaml.set("cast_mode_enabled", profile.castModeEnabled());

        List<Map<String, Object>> bindingsList = new ArrayList<>();
        for (SkillSlotBinding binding : profile.bindings()) {
            if (binding.isEmpty()) {
                continue;
            }
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("slot", binding.slotIndex());
            map.put("skill_id", binding.skillId());
            map.put("trigger_id", binding.triggerId());
            bindingsList.add(map);
        }
        yaml.set("bindings", bindingsList);

        for (Map.Entry<String, PlayerLocalResourceState> entry : profile.localResources().entrySet()) {
            String path = "local_resources." + entry.getKey();
            PlayerLocalResourceState state = entry.getValue();
            yaml.set(path + ".current_value", state.currentValue());
            yaml.set(path + ".last_regen_at", state.lastRegenAt());
        }

        for (Map.Entry<String, PlayerSkillLevelState> entry : profile.skillLevels().entrySet()) {
            PlayerSkillLevelState state = entry.getValue();
            if (state == null || state.level() <= 1) {
                continue;
            }
            yaml.set("skill_levels." + entry.getKey() + ".level", state.level());
        }

        if (!profile.manualSkillIds().isEmpty()) {
            yaml.set("manual_skill_ids", new ArrayList<>(profile.manualSkillIds()));
        }

        PlayerCastTimingState timing = profile.timingState();
        yaml.set("timing.forced_global_cast_delay_until", timing.forcedGlobalCastDelayUntil());
        yaml.set("timing.global_cooldown_until", timing.globalCooldownUntil());
        for (Map.Entry<String, Long> entry : timing.skillCooldownUntilBySkillId().entrySet()) {
            yaml.set("timing.skill_cooldowns." + entry.getKey(), entry.getValue());
        }

        try {
            yaml.save(file);
            profile.clearDirty();
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "[SkillDataStore] Failed to save profile for " + uuid, exception);
        }
    }


    private AsyncYamlFiles asyncYamlFiles() {
        return asyncYamlFilesSupplier == null ? null : asyncYamlFilesSupplier.get();
    }

    private Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException ce && ce.getCause() != null) {
            return ce.getCause();
        }
        return throwable;
    }

    private static int toInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return fallback;
    }

    private static long toLong(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return fallback;
    }

    private static String toStringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String str = value.toString();
        return str.isBlank() ? null : str;
    }
}
