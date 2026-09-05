package emaki.jiuwu.craft.corelib.matcher;

import org.jetbrains.annotations.NotNull;

public record MaterialRequest(@NotNull ItemRequirement requirement,
        int quantity,
        @NotNull String materialId,
        @NotNull String requirementId,
        @NotNull String countKey,
        @NotNull String slotId,
        @NotNull String auditId) {

    public MaterialRequest {
        requirement = requirement == null ? new ItemRequirement(java.util.List.of(), null, "", "") : requirement;
        quantity = Math.max(0, quantity);
        materialId = normalize(materialId);
        requirementId = normalize(requirementId);
        countKey = normalize(countKey);
        slotId = normalize(slotId);
        auditId = normalize(auditId);
    }

    public MaterialRequest(@NotNull ItemRequirement requirement, int quantity) {
        this(requirement, quantity, requirement == null ? "" : requirement.canonicalIdentity(), "", "", "", "");
    }

    public MaterialRequest(@NotNull Matcher matcher, int quantity) {
        this((ItemRequirement) new ItemRequirement(java.util.List.of(), matcher, "", ""), quantity);
    }

    public @NotNull String identity() {
        if (!materialId.isBlank()) {
            return materialId;
        }
        if (!requirementId.isBlank()) {
            return requirementId;
        }
        return requirement.identity();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
