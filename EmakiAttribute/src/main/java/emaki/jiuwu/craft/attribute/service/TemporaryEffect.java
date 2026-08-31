package emaki.jiuwu.craft.attribute.service;

import java.util.List;
import java.util.Set;

import emaki.jiuwu.craft.attribute.model.TemporaryStackMode;
import emaki.jiuwu.craft.attribute.service.TemporaryAttributeService.TemporaryAttributeMode;

record TemporaryEffect(String attributeId,
        double value,
        TemporaryAttributeMode mode,
        TemporaryStackMode stackMode,
        long expiresAtMillis,
        long revision,
        Set<String> appliedTags) {

    TemporaryEffect {
        appliedTags = appliedTags == null || appliedTags.isEmpty() ? Set.of() : Set.copyOf(appliedTags);
    }

    static TemporaryEffect of(String attributeId,
            double value,
            TemporaryAttributeMode mode,
            TemporaryStackMode stackMode,
            long expiresAtMillis,
            long revision) {
        return new TemporaryEffect(attributeId, value, mode, stackMode, expiresAtMillis, revision, Set.of());
    }

    boolean expired(long nowMillis) {
        return expiresAtMillis <= nowMillis;
    }

    long remainingTicks(long nowMillis) {
        return Math.max(0L, (long) Math.ceil((expiresAtMillis - nowMillis) / 50D));
    }

    boolean carriesTag(String normalizedTag) {
        return appliedTags.contains(normalizedTag);
    }

    List<String> sortedTags() {
        return appliedTags.stream().sorted().toList();
    }
}
