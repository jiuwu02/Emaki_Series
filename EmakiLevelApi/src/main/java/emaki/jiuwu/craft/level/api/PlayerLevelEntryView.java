package emaki.jiuwu.craft.level.api;

/**
 * Read-only snapshot of one player's progress in one level type.
 *
 * <p>One entry per level type inside {@link PlayerLevelView#levels()}. The compact constructor
 * normalizes a {@code null} {@code typeId} to an empty string. Instances are immutable and safe
 * to hold from any thread; a snapshot is not refreshed after reloads or further writes.
 *
 * @param typeId       the level type this entry belongs to; {@code null} becomes an empty string
 * @param level        the current level
 * @param exp          progress accumulated within the current level
 * @param totalExp     lifetime experience accumulated for this type across all levels
 * @param requiredExp  experience needed for the next level, with the target capped at the type's
 *                     max level
 * @param progress     {@code exp / requiredExp} clamped to at most {@code 1}, and exactly
 *                     {@code 1} when the requirement is not positive
 */
public record PlayerLevelEntryView(String typeId,
        int level,
        double exp,
        double totalExp,
        double requiredExp,
        double progress) {

    /**
     * Normalizes a null type id.
     *
     * @param typeId       the level type id; {@code null} becomes an empty string
     * @param level        the current level
     * @param exp          progress within the current level
     * @param totalExp     lifetime experience for this type
     * @param requiredExp  experience needed for the next level
     * @param progress     the progress ratio, at most {@code 1}
     */
    public PlayerLevelEntryView {
        typeId = typeId == null ? "" : typeId;
    }
}
