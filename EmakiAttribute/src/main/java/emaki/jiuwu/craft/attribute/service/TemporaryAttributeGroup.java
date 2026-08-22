package emaki.jiuwu.craft.attribute.service;

import java.util.LinkedHashMap;
import java.util.Map;

record TemporaryAttributeGroup(String groupId,
        Map<String, TemporaryEffect> effects,
        TemporaryEffectSource source,
        long revision,
        long createdAtMillis,
        long updatedAtMillis) {

    TemporaryAttributeGroup {
        effects = effects == null || effects.isEmpty() ? Map.of() : Map.copyOf(effects);
        source = source == null ? TemporaryEffectSource.INTERNAL : source;
    }

    static TemporaryAttributeGroup opened(String groupId, TemporaryEffectSource source, long nowMillis, long revision) {
        return new TemporaryAttributeGroup(groupId, Map.of(), source, revision, nowMillis, nowMillis);
    }

    TemporaryAttributeGroup withEffect(TemporaryEffect effect,
            TemporaryEffectSource nextSource,
            long nowMillis,
            long nextRevision) {
        Map<String, TemporaryEffect> merged = new LinkedHashMap<>(effects);
        merged.put(effect.attributeId(), effect);
        return new TemporaryAttributeGroup(groupId,
                merged,
                nextSource == null ? source : nextSource,
                nextRevision,
                createdAtMillis,
                nowMillis);
    }

    TemporaryAttributeGroup withoutExpired(long nowMillis) {
        Map<String, TemporaryEffect> retained = new LinkedHashMap<>();
        for (TemporaryEffect effect : effects.values()) {
            if (effect != null && !effect.expired(nowMillis)) {
                retained.put(effect.attributeId(), effect);
            }
        }
        return retained.size() == effects.size()
                ? this
                : new TemporaryAttributeGroup(groupId, retained, source, revision, createdAtMillis, nowMillis);
    }

    TemporaryEffect effect(String attributeId) {
        return effects.get(attributeId);
    }

    boolean isEmpty() {
        return effects.isEmpty();
    }

    boolean fullyExpired(long nowMillis) {
        for (TemporaryEffect effect : effects.values()) {
            if (effect != null && !effect.expired(nowMillis)) {
                return false;
            }
        }
        return true;
    }
}
