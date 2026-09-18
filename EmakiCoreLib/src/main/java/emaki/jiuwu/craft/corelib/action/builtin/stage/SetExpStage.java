package emaki.jiuwu.craft.corelib.action.builtin.stage;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.debug.ActionAuditLogger;
import emaki.jiuwu.craft.corelib.debug.ActionAuditLogger.OperationType;

public final class SetExpStage extends ExperienceStage {

    public SetExpStage(ActionAuditLogger auditLogger) {
        super("set_exp", "Sets the target's experience to an absolute value.", auditLogger, OperationType.SET);
    }

    @Override
    CoreActionOutcome apply(Player target, int amount, boolean levels) {
        if (levels) {
            target.setLevel(amount);
        } else {
            setTotalExperience(target, amount);
        }
        return CoreActionOutcome.success();
    }
}
