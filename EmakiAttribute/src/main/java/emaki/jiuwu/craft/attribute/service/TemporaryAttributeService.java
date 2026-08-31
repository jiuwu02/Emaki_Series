package emaki.jiuwu.craft.attribute.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.model.AttributeDefinition;
import emaki.jiuwu.craft.attribute.model.TemporaryStackMode;
import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class TemporaryAttributeService implements AutoCloseable {

    private static final long CLEANUP_INTERVAL_MILLIS = 250L;
    private static final double ZERO_EPSILON = 1.0E-9D;
    private static final long MAX_DURATION_TICKS = Long.MAX_VALUE / 50L;

    private final EmakiAttributePlugin plugin;
    private final AttributeService attributeService;
    private final Map<UUID, TemporaryEntityState> states = new ConcurrentHashMap<>();
    private final AtomicLong revisionSequence = new AtomicLong();
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
        TemporaryAttributeOutcome outcome = removeGroup(entity, effectId);
        return outcome.status() == TemporaryAttributeStatus.REMOVED
                ? TemporaryAttributeResult.of(true, legacyEntry(outcome))
                : TemporaryAttributeResult.missing(outcome.groupId());
    }

    public TemporaryAttributeOutcome applyGroupEffect(LivingEntity entity,
            String groupId,
            String attributeId,
            double value,
            long durationTicks,
            TemporaryAttributeMode mode,
            String rawStackMode,
            TemporaryEffectSource source) {
        if (Texts.isNotBlank(rawStackMode) && !TemporaryStackMode.isDeclared(rawStackMode)) {
            return TemporaryAttributeOutcome.rejected(TemporaryAttributeStatus.INVALID_INPUT,
                    Texts.normalizeId(groupId), Texts.normalizeId(attributeId), "unknown_stack_mode");
        }
        TemporaryStackMode stackMode = Texts.isBlank(rawStackMode)
                ? null
                : TemporaryStackMode.fromString(rawStackMode, null);
        return upsert(entity, groupId, attributeId, value, durationTicks, mode, stackMode, Set.of(), source);
    }

    public TemporaryAttributeOutcome addGroupByTag(LivingEntity entity,
            String effectPrefix,
            String tag,
            double value,
            long durationTicks,
            String rawStackMode,
            TemporaryEffectSource source) {
        if (Texts.isNotBlank(rawStackMode) && !TemporaryStackMode.isDeclared(rawStackMode)) {
            return TemporaryAttributeOutcome.rejected(TemporaryAttributeStatus.INVALID_INPUT,
                    Texts.normalizeId(effectPrefix), "", "unknown_stack_mode");
        }
        TemporaryStackMode stackMode = Texts.isBlank(rawStackMode)
                ? null
                : TemporaryStackMode.fromString(rawStackMode, null);
        return addGroupByTag(entity, effectPrefix, tag, value, durationTicks, stackMode, source);
    }

    public TemporaryAttributeOutcome removeGroup(LivingEntity entity, String effectId) {
        if (entity == null || Texts.isBlank(effectId)) {
            return TemporaryAttributeOutcome.rejected(TemporaryAttributeStatus.INVALID_INPUT,
                    Texts.normalizeId(effectId), "", "blank_group_id");
        }
        String normalizedEffectId = Texts.normalizeId(effectId);
        TemporaryEntityState state = states.get(entity.getUniqueId());
        if (state == null) {
            return TemporaryAttributeOutcome.notFound(normalizedEffectId);
        }
        TemporaryAttributeGroup removed = state.write(entityGroups -> entityGroups.remove(normalizedEffectId));
        discardIfEmpty(entity.getUniqueId(), state);
        if (removed == null || removed.isEmpty()) {
            return TemporaryAttributeOutcome.notFound(normalizedEffectId);
        }
        invalidateEntity(entity);
        return TemporaryAttributeOutcome.removed(removed, System.currentTimeMillis());
    }

    public int addByTag(LivingEntity entity,
            String effectPrefix,
            String tag,
            double value,
            long durationTicks,
            TemporaryStackMode stackMode) {
        return addGroupByTag(entity, effectPrefix, tag, value, durationTicks, stackMode,
                TemporaryEffectSource.INTERNAL).affectedCount();
    }

    TemporaryAttributeOutcome addGroupByTag(LivingEntity entity,
            String effectPrefix,
            String tag,
            double value,
            long durationTicks,
            TemporaryStackMode stackMode,
            TemporaryEffectSource source) {
        String normalizedTag = normalizeTag(tag);
        if (entity == null || durationTicks <= 0L || normalizedTag.isBlank()) {
            return TemporaryAttributeOutcome.rejected(TemporaryAttributeStatus.INVALID_INPUT,
                    Texts.normalizeId(effectPrefix), "", "blank_tag_or_non_positive_duration");
        }
        String groupId = Texts.isBlank(effectPrefix) ? "tag:" + normalizedTag : Texts.normalizeId(effectPrefix);
        Set<String> appliedTags = Set.of(normalizedTag);
        List<AttributeDefinition> tagged = taggedDefinitions(normalizedTag);
        if (tagged.isEmpty()) {
            return TemporaryAttributeOutcome.noMatch(groupId, normalizedTag);
        }
        int applied = 0;
        for (AttributeDefinition definition : tagged) {
            TemporaryStackMode effectiveMode = stackMode == null ? definition.temporaryStackMode() : stackMode;
            TemporaryAttributeOutcome outcome = upsert(entity, groupId, definition.id(), value, durationTicks,
                    TemporaryAttributeMode.ADD, effectiveMode, appliedTags, source);
            if (outcome.successful()) {
                applied++;
            }
        }
        return applied == 0
                ? TemporaryAttributeOutcome.noMatch(groupId, normalizedTag)
                : new TemporaryAttributeOutcome(TemporaryAttributeStatus.APPLIED, groupId, "", value, 0L,
                        applied, normalizedTag, TemporaryAttributeMode.ADD, TemporaryStackMode.REPLACE);
    }

    public int removeByTag(LivingEntity entity, String tag) {
        return removeGroupByTag(entity, tag).affectedCount();
    }

    public TemporaryAttributeOutcome removeGroupByTag(LivingEntity entity, String tag) {
        String normalizedTag = normalizeTag(tag);
        if (entity == null || normalizedTag.isBlank()) {
            return TemporaryAttributeOutcome.rejected(TemporaryAttributeStatus.INVALID_INPUT, "", "", "blank_tag");
        }
        TemporaryEntityState state = states.get(entity.getUniqueId());
        if (state == null || state.isEmpty()) {
            return TemporaryAttributeOutcome.noMatch("", normalizedTag);
        }
        int removed = state.write(entityGroups -> {
            int dropped = 0;
            for (Map.Entry<String, TemporaryAttributeGroup> groupEntry : entityGroups.entrySet()) {
                TemporaryAttributeGroup group = groupEntry.getValue();
                if (group == null || group.isEmpty()) {
                    continue;
                }
                Map<String, TemporaryEffect> retained = new LinkedHashMap<>();
                int droppedInGroup = 0;
                for (TemporaryEffect effect : group.effects().values()) {
                    if (effect != null && carriesTag(effect, normalizedTag)) {
                        droppedInGroup++;
                        continue;
                    }
                    retained.put(effect.attributeId(), effect);
                }
                if (droppedInGroup == 0) {
                    continue;
                }
                dropped += droppedInGroup;
                groupEntry.setValue(new TemporaryAttributeGroup(group.groupId(), retained, group.source(),
                        group.revision(), group.createdAtMillis(), System.currentTimeMillis()));
            }
            entityGroups.values().removeIf(group -> group == null || group.isEmpty());
            return dropped;
        });
        discardIfEmpty(entity.getUniqueId(), state);
        if (removed == 0) {
            return TemporaryAttributeOutcome.noMatch("", normalizedTag);
        }
        invalidateEntity(entity);
        return TemporaryAttributeOutcome.removedByTag(normalizedTag, removed);
    }

    Map<String, Double> additiveValues(LivingEntity entity) {
        if (entity == null) {
            return Map.of();
        }
        return capture(entity).additiveValues();
    }

    Map<String, Double> setValues(LivingEntity entity) {
        if (entity == null) {
            return Map.of();
        }
        return capture(entity).setValues();
    }

    String signature(LivingEntity entity) {
        if (entity == null) {
            return "";
        }
        return capture(entity).signature();
    }

    void clear() {
        states.clear();
    }

    public int discardEntity(UUID entityId) {
        if (entityId == null) {
            return 0;
        }
        TemporaryEntityState state = states.remove(entityId);
        if (state == null) {
            return 0;
        }
        return state.write(entityGroups -> {
            int discarded = 0;
            for (TemporaryAttributeGroup group : entityGroups.values()) {
                if (group != null) {
                    discarded += group.effects().size();
                }
            }
            entityGroups.clear();
            return discarded;
        });
    }

    boolean tracks(UUID entityId) {
        return entityId != null && states.containsKey(entityId);
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
        TemporaryAttributeOutcome outcome = upsert(entity, effectId, attributeId, value, durationTicks, mode,
                requestedStackMode, Set.of(), TemporaryEffectSource.INTERNAL);
        return outcome.successful()
                ? TemporaryAttributeResult.of(outcome.status() != TemporaryAttributeStatus.APPLIED,
                        legacyEntry(outcome))
                : TemporaryAttributeResult.missing(outcome.groupId());
    }

    private TemporaryAttributeOutcome upsert(LivingEntity entity,
            String groupId,
            String attributeId,
            double value,
            long durationTicks,
            TemporaryAttributeMode mode,
            TemporaryStackMode requestedStackMode,
            Set<String> appliedTags,
            TemporaryEffectSource source) {
        if (entity == null || Texts.isBlank(groupId) || Texts.isBlank(attributeId) || durationTicks <= 0L) {
            return TemporaryAttributeOutcome.rejected(TemporaryAttributeStatus.INVALID_INPUT,
                    Texts.normalizeId(groupId), Texts.normalizeId(attributeId), "blank_or_non_positive_duration");
        }
        String normalizedGroupId = Texts.normalizeId(groupId);
        String normalizedAttributeId = Texts.normalizeId(attributeId);
        if (!Double.isFinite(value)) {
            return TemporaryAttributeOutcome.rejected(TemporaryAttributeStatus.INVALID_INPUT,
                    normalizedGroupId, normalizedAttributeId, "non_finite_value");
        }
        if (durationTicks > MAX_DURATION_TICKS) {
            return TemporaryAttributeOutcome.rejected(TemporaryAttributeStatus.INVALID_INPUT,
                    normalizedGroupId, normalizedAttributeId, "duration_overflow");
        }
        AttributeDefinition definition = resolveDefinition(normalizedAttributeId);
        if (definition == null) {
            return TemporaryAttributeOutcome.rejected(TemporaryAttributeStatus.UNKNOWN_ATTRIBUTE,
                    normalizedGroupId, normalizedAttributeId, "unregistered_attribute");
        }
        TemporaryStackMode stackMode = requestedStackMode == null
                ? definition.temporaryStackMode()
                : requestedStackMode;
        long now = System.currentTimeMillis();
        long durationMillis = durationTicks * 50L;
        if (now > Long.MAX_VALUE - durationMillis) {
            return TemporaryAttributeOutcome.rejected(TemporaryAttributeStatus.INVALID_INPUT,
                    normalizedGroupId, normalizedAttributeId, "expiry_overflow");
        }
        long revision = revisionSequence.incrementAndGet();
        TemporaryEntityState state = states.computeIfAbsent(entity.getUniqueId(), _ -> new TemporaryEntityState());
        AtomicReference<TemporaryAttributeStatus> status =
                new AtomicReference<>(TemporaryAttributeStatus.APPLIED);
        TemporaryEffect stored = state.write(entityGroups -> {
            TemporaryAttributeGroup current = entityGroups.get(normalizedGroupId);
            TemporaryAttributeGroup base = current == null
                    ? TemporaryAttributeGroup.opened(normalizedGroupId, source, now, revision)
                    : current.withoutExpired(now);
            TemporaryEffect active = base.effect(normalizedAttributeId);
            boolean activeSameMode = active != null && !active.expired(now) && active.mode() == mode;
            double nextValue;
            long expiresAtMillis;
            if (stackMode == TemporaryStackMode.STACK && activeSameMode) {
                status.set(TemporaryAttributeStatus.STACKED);
                nextValue = mode == TemporaryAttributeMode.ADD ? active.value() + value : value;
                long anchor = Math.max(active.expiresAtMillis(), now);
                expiresAtMillis = anchor > Long.MAX_VALUE - durationMillis
                        ? Long.MAX_VALUE
                        : anchor + durationMillis;
            } else {
                status.set(active != null && !active.expired(now)
                        ? TemporaryAttributeStatus.REPLACED
                        : TemporaryAttributeStatus.APPLIED);
                nextValue = value;
                expiresAtMillis = now + durationMillis;
            }
            if (Math.abs(nextValue) <= ZERO_EPSILON) {
                nextValue = 0D;
            }
            Set<String> mergedTags = mergeTags(active, appliedTags);
            TemporaryEffect effect = new TemporaryEffect(normalizedAttributeId, nextValue, mode, stackMode,
                    expiresAtMillis, revision, mergedTags);
            entityGroups.put(normalizedGroupId, base.withEffect(effect, source, now, revision));
            return effect;
        });
        invalidateEntity(entity);
        return TemporaryAttributeOutcome.applied(status.get(), normalizedGroupId, stored, now);
    }

    private Set<String> mergeTags(TemporaryEffect active, Set<String> appliedTags) {
        if (appliedTags == null || appliedTags.isEmpty()) {
            return active == null ? Set.of() : active.appliedTags();
        }
        if (active == null || active.appliedTags().isEmpty()) {
            return appliedTags;
        }
        Set<String> merged = new LinkedHashSet<>(active.appliedTags());
        merged.addAll(appliedTags);
        return merged;
    }

    private boolean carriesTag(TemporaryEffect effect, String normalizedTag) {
        if (effect.carriesTag(normalizedTag)) {
            return true;
        }
        if (!effect.appliedTags().isEmpty()) {
            return false;
        }
        AttributeDefinition definition = resolveDefinition(effect.attributeId());
        return definition != null && definition.hasTag(normalizedTag);
    }

    TemporaryAttributeCapture capture(LivingEntity entity) {
        if (entity == null) {
            return TemporaryAttributeCapture.empty();
        }
        TemporaryEntityState state = states.get(entity.getUniqueId());
        if (state == null) {
            return TemporaryAttributeCapture.empty();
        }
        long now = System.currentTimeMillis();
        return state.readOrDefault(entityGroups -> TemporaryAttributeCapture.of(entityGroups, now),
                TemporaryAttributeCapture::empty);
    }

    private void discardIfEmpty(UUID entityId, TemporaryEntityState state) {
        if (state.isEmpty()) {
            states.remove(entityId, state);
        }
    }

    private TemporaryAttributeEntry legacyEntry(TemporaryAttributeOutcome outcome) {
        return new TemporaryAttributeEntry(outcome.groupId(), outcome.attributeId(), outcome.value(),
                outcome.mode(), outcome.stackMode(),
                System.currentTimeMillis() + outcome.remainingTicks() * 50L);
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
        return Texts.isBlank(tag) ? "" : tag.trim().toLowerCase(Locale.ROOT);
    }

    private void cleanupExpiredSafely() {
        try {
            cleanupExpired();
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Temporary attribute cleanup failed: trackedEntities=" + states.size()
                            + ", operation=cleanup_expired, cause=" + exception,
                    exception);
        }
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        List<UUID> changedEntities = new ArrayList<>();
        for (Map.Entry<UUID, TemporaryEntityState> entityEntry : states.entrySet()) {
            TemporaryEntityState state = entityEntry.getValue();
            if (state == null) {
                states.remove(entityEntry.getKey(), state);
                continue;
            }
            boolean changed = state.write(entityGroups -> {
                boolean mutated = false;
                for (Map.Entry<String, TemporaryAttributeGroup> groupEntry : entityGroups.entrySet()) {
                    TemporaryAttributeGroup group = groupEntry.getValue();
                    if (group == null) {
                        continue;
                    }
                    TemporaryAttributeGroup pruned = group.withoutExpired(now);
                    if (pruned != group) {
                        groupEntry.setValue(pruned);
                        mutated = true;
                    }
                }
                return mutated | entityGroups.values().removeIf(group -> group == null || group.isEmpty());
            });
            discardIfEmpty(entityEntry.getKey(), state);
            if (changed) {
                changedEntities.add(entityEntry.getKey());
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
        EmakiScheduling sched = scheduling();
        if (sched == null) {
            return;
        }
        sched.runGlobal(plugin, () -> {
            for (UUID entityId : entityIds) {
                Entity entity = Bukkit.getEntity(entityId);
                if (entity instanceof LivingEntity livingEntity && livingEntity.isValid()) {
                    sched.runForEntity(plugin, livingEntity, () -> invalidateEntity(livingEntity), null);
                }
            }
        });
    }

    private EmakiScheduling scheduling() {
        EmakiScheduling sched = attributeService == null ? null : attributeService.scheduling();
        return sched != null ? sched : plugin.scheduling();
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
