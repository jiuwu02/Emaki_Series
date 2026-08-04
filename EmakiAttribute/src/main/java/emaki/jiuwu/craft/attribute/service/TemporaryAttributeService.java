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
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.model.AttributeDefinition;
import emaki.jiuwu.craft.attribute.model.TemporaryStackMode;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.api.text.Texts;

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

    public TemporaryAttributeResult add(LivingEntity entity,
            String effectId,
            String attributeId,
            double value,
            long durationTicks) {
        return add(entity, effectId, attributeId, value, durationTicks, null);
    }

    public TemporaryAttributeResult add(LivingEntity entity,
            String effectId,
            String attributeId,
            double value,
            long durationTicks,
            TemporaryStackMode stackMode) {
        return upsert(entity, effectId, attributeId, value, durationTicks, TemporaryAttributeMode.ADD, stackMode);
    }

    public TemporaryAttributeResult set(LivingEntity entity,
            String effectId,
            String attributeId,
            double value,
            long durationTicks) {
        return set(entity, effectId, attributeId, value, durationTicks, null);
    }

    public TemporaryAttributeResult set(LivingEntity entity,
            String effectId,
            String attributeId,
            double value,
            long durationTicks,
            TemporaryStackMode stackMode) {
        return upsert(entity, effectId, attributeId, value, durationTicks, TemporaryAttributeMode.SET, stackMode);
    }

    public TemporaryAttributeResult remove(LivingEntity entity, String effectId) {
        if (entity == null || Texts.isBlank(effectId)) {
            return TemporaryAttributeResult.missing(Texts.normalizeId(effectId));
        }
        String normalizedEffectId = Texts.normalizeId(effectId);
        Map<String, TemporaryAttributeEntry> playerEntries = entries.get(entity.getUniqueId());
        if (playerEntries == null) {
            return TemporaryAttributeResult.missing(normalizedEffectId);
        }
        TemporaryAttributeEntry removed = playerEntries.remove(normalizedEffectId);
        if (playerEntries.isEmpty()) {
            entries.remove(entity.getUniqueId(), playerEntries);
        }
        if (removed != null) {
            invalidateEntity(entity);
            return TemporaryAttributeResult.of(true, removed);
        }
        return TemporaryAttributeResult.missing(normalizedEffectId);
    }






    public int addByTag(LivingEntity entity,
            String effectPrefix,
            String tag,
            double value,
            long durationTicks,
            TemporaryStackMode stackMode) {
        if (entity == null || durationTicks <= 0L) {
            return 0;
        }
        String normalizedTag = normalizeTag(tag);
        if (normalizedTag.isBlank()) {
            return 0;
        }
        String prefix = Texts.isBlank(effectPrefix) ? "tag:" + normalizedTag : Texts.normalizeId(effectPrefix);
        int applied = 0;
        for (AttributeDefinition definition : taggedDefinitions(normalizedTag)) {
            TemporaryStackMode effectiveMode = stackMode == null ? definition.temporaryStackMode() : stackMode;
            String effectId = prefix + ':' + definition.id();
            upsert(entity, effectId, definition.id(), value, durationTicks, TemporaryAttributeMode.ADD, effectiveMode);
            applied++;
        }
        return applied;
    }





    public int removeByTag(LivingEntity entity, String tag) {
        if (entity == null) {
            return 0;
        }
        String normalizedTag = normalizeTag(tag);
        if (normalizedTag.isBlank()) {
            return 0;
        }
        Map<String, TemporaryAttributeEntry> playerEntries = entries.get(entity.getUniqueId());
        if (playerEntries == null || playerEntries.isEmpty()) {
            return 0;
        }
        int removed = 0;
        List<String> toRemove = new ArrayList<>();
        for (TemporaryAttributeEntry entry : playerEntries.values()) {
            if (entry == null) {
                continue;
            }
            AttributeDefinition definition = resolveDefinition(entry.attributeId());
            if (definition != null && definition.hasTag(normalizedTag)) {
                toRemove.add(entry.effectId());
            }
        }
        for (String effectId : toRemove) {
            if (playerEntries.remove(effectId) != null) {
                removed++;
            }
        }
        if (playerEntries.isEmpty()) {
            entries.remove(entity.getUniqueId(), playerEntries);
        }
        if (removed > 0) {
            invalidateEntity(entity);
        }
        return removed;
    }

    Map<String, Double> additiveValues(LivingEntity entity) {
        if (entity == null) {
            return Map.of();
        }
        Map<String, TemporaryAttributeEntry> playerEntries = entries.get(entity.getUniqueId());
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

    Map<String, Double> setValues(LivingEntity entity) {
        if (entity == null) {
            return Map.of();
        }
        Map<String, TemporaryAttributeEntry> playerEntries = entries.get(entity.getUniqueId());
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

    String signature(LivingEntity entity) {
        if (entity == null) {
            return "";
        }
        Map<String, TemporaryAttributeEntry> playerEntries = entries.get(entity.getUniqueId());
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

    private TemporaryAttributeResult upsert(LivingEntity entity,
            String effectId,
            String attributeId,
            double value,
            long durationTicks,
            TemporaryAttributeMode mode,
            TemporaryStackMode requestedStackMode) {
        if (entity == null || Texts.isBlank(effectId) || Texts.isBlank(attributeId) || durationTicks <= 0L) {
            return TemporaryAttributeResult.missing(Texts.normalizeId(effectId));
        }
        String normalizedEffectId = Texts.normalizeId(effectId);
        String normalizedAttributeId = Texts.normalizeId(attributeId);
        TemporaryStackMode stackMode = resolveStackMode(requestedStackMode, normalizedAttributeId);
        long now = System.currentTimeMillis();
        long durationMillis = Math.max(1L, durationTicks) * 50L;
        Map<String, TemporaryAttributeEntry> playerEntries = entries.computeIfAbsent(entity.getUniqueId(), _ -> new ConcurrentHashMap<>());
        AtomicBoolean existed = new AtomicBoolean(false);
        TemporaryAttributeEntry next = playerEntries.compute(normalizedEffectId, (_, current) -> {
            boolean activeSameAttribute = current != null
                    && !current.expired(now)
                    && current.mode() == mode
                    && normalizedAttributeId.equals(current.attributeId());
            existed.set(current != null && !current.expired(now));
            double nextValue;
            long expiresAtMillis;
            if (stackMode == TemporaryStackMode.STACK && activeSameAttribute) {
                nextValue = mode == TemporaryAttributeMode.ADD ? current.value() + value : value;
                expiresAtMillis = Math.max(current.expiresAtMillis(), now) + durationMillis;
            } else {
                nextValue = value;
                expiresAtMillis = now + durationMillis;
            }
            if (Math.abs(nextValue) <= ZERO_EPSILON) {
                nextValue = 0D;
            }
            return new TemporaryAttributeEntry(normalizedEffectId, normalizedAttributeId, nextValue, mode, stackMode, expiresAtMillis);
        });
        invalidateEntity(entity);
        return TemporaryAttributeResult.of(existed.get(), next);
    }

    private TemporaryStackMode resolveStackMode(TemporaryStackMode requestedStackMode, String attributeId) {
        if (requestedStackMode != null) {
            return requestedStackMode;
        }
        AttributeDefinition definition = resolveDefinition(attributeId);
        return definition == null ? TemporaryStackMode.REPLACE : definition.temporaryStackMode();
    }

    private AttributeDefinition resolveDefinition(String attributeId) {
        if (attributeService == null || attributeService.attributeRegistry() == null || Texts.isBlank(attributeId)) {
            return null;
        }
        AttributeDefinition definition = attributeService.attributeRegistry().get(attributeId);
        return definition != null ? definition : attributeService.attributeRegistry().resolve(attributeId);
    }

    private List<AttributeDefinition> taggedDefinitions(String normalizedTag) {
        if (attributeService == null || attributeService.attributeRegistry() == null) {
            return List.of();
        }
        List<AttributeDefinition> tagged = new ArrayList<>();
        for (AttributeDefinition definition : attributeService.attributeRegistry().all().values()) {
            if (definition != null && definition.hasTag(normalizedTag)) {
                tagged.add(definition);
            }
        }
        return tagged;
    }

    private String normalizeTag(String tag) {
        return Texts.isBlank(tag) ? "" : tag.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private void cleanupExpiredSafely() {
        try {
            cleanupExpired();
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Temporary attribute cleanup failed: trackedEntities=" + entries.size()
                            + ", operation=cleanup_expired, cause=" + exception,
                    exception);
        }
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        List<UUID> changedEntities = new ArrayList<>();
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
                changedEntities.add(playerEntry.getKey());
            }
        }
        if (!changedEntities.isEmpty()) {
            scheduleInvalidation(changedEntities);
        }
    }

    private void scheduleInvalidation(List<UUID> entityIds) {
        if (plugin == null || !plugin.isEnabled()) {
            return;
        }
        ExecutionDispatcher dispatcher = dispatcher();
        if (dispatcher == null) {
            return;
        }
        dispatcher.runGlobal(plugin, () -> {
            for (UUID entityId : entityIds) {
                Entity entity = Bukkit.getEntity(entityId);
                if (entity instanceof LivingEntity livingEntity && livingEntity.isValid()) {
                    dispatcher.runEntity(plugin, livingEntity, () -> invalidateEntity(livingEntity));
                }
            }
        });
    }

    private ExecutionDispatcher dispatcher() {
        ExecutionDispatcher dispatcher = attributeService == null ? null : attributeService.executionDispatcher();
        return dispatcher != null ? dispatcher : plugin.executionDispatcher();
    }

    private void invalidateEntity(LivingEntity entity) {
        if (entity == null || attributeService == null) {
            return;
        }
        attributeService.stateRepository().clearCombatSnapshot(entity);
        attributeService.scheduleLivingEntitySync(entity);
    }

    public enum TemporaryAttributeMode {
        ADD,
        SET
    }

    public record TemporaryAttributeEntry(String effectId,
            String attributeId,
            double value,
            TemporaryAttributeMode mode,
            TemporaryStackMode stackMode,
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
                    new TemporaryAttributeEntry(effectId, "", 0D, TemporaryAttributeMode.ADD, TemporaryStackMode.REPLACE, 0L));
        }
    }
}
