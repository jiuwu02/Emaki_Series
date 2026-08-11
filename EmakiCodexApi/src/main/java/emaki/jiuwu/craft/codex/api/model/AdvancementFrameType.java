package emaki.jiuwu.craft.codex.api.model;

/**
 * The display frame a Minecraft advancement uses.
 *
 * <p>Mirrors Minecraft's own advancement frame types. The corresponding internal enum in EmakiCodex is
 * {@code emaki.jiuwu.craft.codex.advancement.model.AdvancementFrame}; this enum is the API copy.
 */
public enum AdvancementFrameType {

    /** The default rectangular frame, used for most advancements. */
    TASK,

    /** A circular frame, for harder advancements. */
    GOAL,

    /** A notched star frame, for the hardest advancements. */
    CHALLENGE
}
