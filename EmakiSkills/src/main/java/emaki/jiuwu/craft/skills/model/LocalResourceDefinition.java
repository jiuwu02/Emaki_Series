package emaki.jiuwu.craft.skills.model;

public record LocalResourceDefinition(String id,
        String displayName,
        double max,
        double defaultCurrent,
        double regenAmount,
        long regenIntervalTicks,
        double clampMin,
        double clampMax) {

    public LocalResourceDefinition {
        id = id == null ? "" : id;
        displayName = displayName == null || displayName.isBlank() ? id : displayName;
        max = Math.max(0D, max);
        defaultCurrent = Math.max(0D, defaultCurrent);
        regenAmount = Math.max(0D, regenAmount);
        regenIntervalTicks = Math.max(0L, regenIntervalTicks);
        clampMin = Math.max(0D, clampMin);
        clampMax = clampMax <= 0D ? max : Math.max(clampMin, clampMax);
    }
}
