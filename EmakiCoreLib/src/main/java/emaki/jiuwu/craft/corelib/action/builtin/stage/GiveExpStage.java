package emaki.jiuwu.craft.corelib.action.builtin.stage;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.debug.ActionAuditLogger;
import emaki.jiuwu.craft.corelib.debug.ActionAuditLogger.OperationType;

public final class GiveExpStage extends ExperienceStage {

    public GiveExpStage(ActionAuditLogger auditLogger) {
        super("give_exp", "Grants experience to the target.", auditLogger, OperationType.INCREASE);
    }

    @Override
    CoreActionOutcome apply(Player target, int amount, boolean levels) {
        if (levels) {
            target.giveExpLevels(amount);
        } else {
            target.giveExp(amount);
        }
        return CoreActionOutcome.success();
    }
}
