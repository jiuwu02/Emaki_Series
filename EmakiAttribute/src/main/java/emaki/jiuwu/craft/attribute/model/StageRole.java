package emaki.jiuwu.craft.attribute.model;

/**
 * Defines the functional role of a damage stage within a damage type's calculation chain.
 * <p>
 * The role determines when and how a stage is applied:
 * <ul>
 *     <li>{@link #NORMAL} — Default role; the stage is always included in the calculation chain.</li>
 *     <li>{@link #CRITICAL} — Critical hit stage; skipped when critical hits are disabled for the damage context.</li>
 *     <li>{@link #DEFENSE} — Defense/damage-reduction stage; skipped when target defense calculation is disabled.</li>
 *     <li>{@link #BLOCK} — Shield block stage; typically applied only when the target is actively blocking.</li>
 * </ul>
 */
public enum StageRole {
    /**
     * Default role — a standard damage stage with no special filtering logic.
     * Always included in the damage calculation chain.
     */
    NORMAL,

    /**
     * Critical hit stage — adds bonus damage when a critical hit occurs.
     * Skipped when the damage context has {@code allow_critical} set to false.
     */
    CRITICAL,

    /**
     * Defense or damage-reduction stage — reduces incoming damage based on target's defensive attributes.
     * Skipped when the damage context has {@code calculate_target_defense} set to false.
     */
    DEFENSE,

    /**
     * Shield block stage — reduces damage when the target is actively blocking with a shield.
     * The stage's contribution is typically controlled by the {@code target_blocking} context variable.
     */
    BLOCK
}
