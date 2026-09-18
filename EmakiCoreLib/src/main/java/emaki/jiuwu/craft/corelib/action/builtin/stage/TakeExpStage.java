package emaki.jiuwu.craft.corelib.action.builtin.stage;

import java.util.Map;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.debug.ActionAuditLogger;
import emaki.jiuwu.craft.corelib.debug.ActionAuditLogger.OperationType;

public final class TakeExpStage extends ExperienceStage {

    public TakeExpStage(ActionAuditLogger auditLogger) {
        super("take_exp", "Removes experience from the target.", auditLogger, OperationType.DECREASE);
    }

    @Override
    CoreActionOutcome apply(Player target, int amount, boolean levels) {
        int current = levels ? target.getLevel() : target.getTotalExperience();
        if (current < amount) {
            return CoreActionOutcome.failure(CoreActionFailureKind.REJECTED,
                    "action.stage.exp.insufficient",
                    Map.of(
                            "mode", levels ? "levels" : "points",
                            "required", amount,
                            "current", current));
        }
        if (levels) {
            target.setLevel(current - amount);
        } else {
            setTotalExperience(target, current - amount);
        }
        return CoreActionOutcome.success();
    }
}
