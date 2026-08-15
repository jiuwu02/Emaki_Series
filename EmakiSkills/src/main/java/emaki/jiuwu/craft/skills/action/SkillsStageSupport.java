package emaki.jiuwu.craft.skills.action;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;

final class SkillsStageSupport {

    private SkillsStageSupport() {
    }

    static @Nullable Player player(@Nullable CoreActionSubject subject) {
        return subject != null && subject.entityOrNull() instanceof Player resolved ? resolved : null;
    }

    static @NotNull CoreActionOutcome serviceUnavailable() {
        return CoreActionOutcome.failure(CoreActionFailureKind.MISSING_CONTEXT,
                "action.stage.skills.service_unavailable");
    }
}
