package emaki.jiuwu.craft.attribute.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class TemporaryAttributeService implements AutoCloseable {

    private static final long CLEANUP_INTERVAL_MILLIS = 250L;
    private static final double ZERO_EPSILON = 1.0E-9D;

    private final EmakiAttributePlugin plugin;
    private final AttributeService attributeService;
    private final Map<UUID, Map<String, TemporaryAttributeEntry>> entries = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor;

    TemporaryAttributeService(EmakiAttributePlugin plugin, AttributeService attributeService) {
        this.plugin = plugin;
        this.attributeService = attributeService;
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "emaki-attribute-temp-attribute-timer");
            thread.setDaemon(true);
            return thread;
        });
        this.cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredSafely,
                CLEANUP_INTERVAL_MILLIS,
                CLEANUP_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS);
    }

    public TemporaryAttributeResult add(Player player, String effectId, String attributeId, double value, long durationTicks) {
        return upsert(player, effectId, attributeId, value, durationTicks, TemporaryAttributeMode.ADD);
    }

    public TemporaryAttributeResult set(Player player, String effectId, String attributeId, double value, long durationTicks) {
        return upsert(player, effectId, attributeId, value, durationTicks, TemporaryAttributeMode.SET);
    }

    public TemporaryAttributeResult remove(Player player, String effectId) {
        if (player == null || Texts.isBlank(effectId)) {
            return TemporaryAttributeResult.missing(Texts.normalizeId(effectId));
        }
        String normalizedEffectId = Texts.normalizeId(effectId);
        Map<String, TemporaryAttributeEntry> playerEntries = entries.get(player.getUniqueId());
        if (playerEntries == null) {
            return TemporaryAttributeResult.missing(normalizedEffectId);
        }
        TemporaryAttributeEntry removed = playerEntries.remove(normalizedEffectId);
        if (playerEntries.isEmpty()) {
            entries.remove(player.getUniqueId(), playerEntries);
        }
        if (removed != null) {
            invalidatePlayer(player);
            return TemporaryAttributeResult.of(true, removed);
        }
        return TemporaryAttributeResult.missing(normalizedEffectId);
    }

    Map<String, Double> additiveValues(Player player) {
        if (player == null) {
            return Map.of();
        }
        Map<String, TemporaryAttributeEntry> playerEntries = entries.get(player.getUniqueId());
        if (playerEntries == null || playerEntries.isEmpty()) {
            return Map.of();
        }
        long now = System.currentTimeMillis();
        Map<String, Double> values = new LinkedHashMap<>();
        for (TemporaryAttributeEntry entry : playerEntries.values()) {
            if (entry == null || entry.mode() != TemporaryAttributeMode.ADD || entry.expired(now)) {
                continue;
            }
            values.merge(entry.attributeId(), entry.value(), Double::sum);
        }
        return values.isEmpty() ? Map.of() : Map.copyOf(values);
    }

    Map<String, Double> setValues(Player player) {
        if (player == null) {
            return Map.of();
        }
        Map<String, TemporaryAttributeEntry> playerEntries = entries.get(player.getUniqueId());
        if (playerEntries == null || playerEntries.isEmpty()) {
            return Map.of();
        }
        long now = System.currentTimeMillis();
        Map<String, Double> values = new LinkedHashMap<>();
        for (TemporaryAttributeEntry entry : playerEntries.values()) {
            if (entry == null || entry.mode() != TemporaryAttributeMode.SET || entry.expired(now)) {
                continue;
            }
            values.put(entry.attributeId(), entry.value());
        }
        return values.isEmpty() ? Map.of() : Map.copyOf(values);
    }

    String signature(Player player) {
        if (player == null) {
            return "";
        }
        Map<String, TemporaryAttributeEntry> playerEntries = entries.get(player.getUniqueId());
        if (playerEntries == null || playerEntries.isEmpty()) {
            return "";
        }
        long now = System.currentTimeMillis();
        List<String> parts = new ArrayList<>();
        for (TemporaryAttributeEntry entry : playerEntries.values()) {
            if (entry == null || entry.expired(now)) {
                continue;
            }
            parts.add(entry.effectId() + ':' + entry.mode().name() + ':' + entry.attributeId() + ':' + entry.value() + ':' + entry.expiresAtMillis());
        }
        parts.sort(String::compareTo);
        return String.join("|", parts);
    }

    void clear() {
        entries.clear();
    }

    @Override
    public void close() {
        cleanupExecutor.shutdownNow();
        clear();
    }

    private TemporaryAttributeResult upsert(Player player,
            String effectId,
            String attributeId,
            double value,
            long durationTicks,
            TemporaryAttributeMode mode) {
        if (player == null || Texts.isBlank(effectId) || Texts.isBlank(attributeId) || durationTicks <= 0L) {
            return TemporaryAttributeResult.missing(Texts.normalizeId(effectId));
        }
        String normalizedEffectId = Texts.normalizeId(effectId);
        String normalizedAttributeId = Texts.normalizeId(attributeId);
        long expiresAtMillis = System.currentTimeMillis() + Math.max(1L, durationTicks) * 50L;
        Map<String, TemporaryAttributeEntry> playerEntries = entries.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>());
        AtomicBoolean existed = new AtomicBoolean(false);
        TemporaryAttributeEntry next = playerEntries.compute(normalizedEffectId, (ignored, current) -> {
            existed.set(current != null && !current.expired(System.currentTimeMillis()));
            double nextValue = mode == TemporaryAttributeMode.ADD
                    && current != null
                    && current.mode() == TemporaryAttributeMode.ADD
                    && normalizedAttributeId.equals(current.attributeId())
                            ? current.value() + value
                            : value;
            if (Math.abs(nextValue) <= ZERO_EPSILON) {
                nextValue = 0D;
            }
            return new TemporaryAttributeEntry(normalizedEffectId, normalizedAttributeId, nextValue, mode, expiresAtMillis);
        });
        invalidatePlayer(player);
        return TemporaryAttributeResult.of(existed.get(), next);
    }

    private void cleanupExpiredSafely() {
        try {
            cleanupExpired();
        } catch (Exception ignored) {
        }
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        List<UUID> changedPlayers = new ArrayList<>();
        for (Map.Entry<UUID, Map<String, TemporaryAttributeEntry>> playerEntry : entries.entrySet()) {
            Map<String, TemporaryAttributeEntry> playerEntries = playerEntry.getValue();
            if (playerEntries == null || playerEntries.isEmpty()) {
                entries.remove(playerEntry.getKey(), playerEntries);
                continue;
            }
            boolean changed = playerEntries.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().expired(now));
            if (playerEntries.isEmpty()) {
                entries.remove(playerEntry.getKey(), playerEntries);
            }
            if (changed) {
                changedPlayers.add(playerEntry.getKey());
            }
        }
        if (!changedPlayers.isEmpty()) {
            scheduleInvalidation(changedPlayers);
        }
    }

    private void scheduleInvalidation(List<UUID> playerIds) {
        if (plugin == null || !plugin.isEnabled()) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (UUID playerId : playerIds) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    invalidatePlayer(player);
                }
            }
        });
    }

    private void invalidatePlayer(Player player) {
        if (player == null || attributeService == null) {
            return;
        }
        attributeService.stateRepository().clearCombatSnapshot(player);
        attributeService.scheduleLivingEntitySync(player);
    }

    public enum TemporaryAttributeMode {
        ADD,
        SET
    }

    public record TemporaryAttributeEntry(String effectId,
            String attributeId,
            double value,
            TemporaryAttributeMode mode,
            long expiresAtMillis) {

        boolean expired(long nowMillis) {
            return expiresAtMillis <= nowMillis;
        }

        public long remainingTicks(long nowMillis) {
            return Math.max(0L, (long) Math.ceil((expiresAtMillis - nowMillis) / 50D));
        }
    }

    public record TemporaryAttributeResult(boolean existed, TemporaryAttributeEntry entry) {

        static TemporaryAttributeResult of(boolean existed, TemporaryAttributeEntry entry) {
            return new TemporaryAttributeResult(existed, entry);
        }

        static TemporaryAttributeResult missing(String effectId) {
            return new TemporaryAttributeResult(false,
                    new TemporaryAttributeEntry(effectId, "", 0D, TemporaryAttributeMode.ADD, 0L));
        }
    }
}
