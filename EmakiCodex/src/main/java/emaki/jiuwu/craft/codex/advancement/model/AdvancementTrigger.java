package emaki.jiuwu.craft.codex.advancement.model;

import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * An automatic grant trigger attached to an advancement node.
 *
 * <p>When the player performs the matching {@code event} (a normalized trigger key such as
 * {@code entity_kill} or {@code block_break}) and the optional {@code condition} evaluates to
 * true, EmakiCodex grants the owning advancement's manual {@code codex} criterion. The
 * completion then flows through the existing {@code PlayerAdvancementDoneEvent} pipeline and
 * runs the node's {@code on_complete} actions.
 *
 * @param event     the normalized trigger key (lower-case, spaces as underscores)
 * @param condition a single boolean expression string; blank means "always pass"
 */
public record AdvancementTrigger(String event, String condition) {

    public AdvancementTrigger {
        event = Texts.normalizeId(event);
        condition = Texts.toStringSafe(condition);
    }

    /** {@return whether this trigger carries a condition that must be satisfied} */
    public boolean hasCondition() {
        return Texts.isNotBlank(condition);
    }
}
