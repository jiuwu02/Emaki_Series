package emaki.jiuwu.craft.skills.action.v2;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionSubject;

/** Shared helpers for this module's pipeline stages. */
final class SkillsStageSupport {

    private SkillsStageSupport() {
    }

    /**
     * Narrows a target subject to a player.
     *
     * <p>Every skill stage writes a per-player skill record, so an entity target that is not a player has
     * nothing to act on. Returning {@code null} lets each stage skip rather than fail: an arrow or a mob in
     * the target flow is not a configuration mistake.</p>
     *
     * @param subject the current target
     * @return the player, or {@code null} when the subject is not one
     */
    static @Nullable Player player(@Nullable CoreActionSubject subject) {
        return subject != null && subject.entityOrNull() instanceof Player resolved ? resolved : null;
    }

    /** {@return a failure stating that a required EmakiSkills service is not running} */
    static @NotNull CoreActionOutcome serviceUnavailable() {
        return CoreActionOutcome.failure(CoreActionFailureKind.MISSING_CONTEXT,
                "action.v2.stage.skills.service_unavailable");
    }
}
