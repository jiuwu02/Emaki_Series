package emaki.jiuwu.craft.corelib.action.builtin;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import java.util.Map;

public final class TakeExpAction extends AbstractExperienceAction {

    public TakeExpAction() {
        super("takeexp", "Take experience.");
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        ActionResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        if (levels(arguments)) {
            context.player().setLevel(Math.max(0, context.player().getLevel() - amount(arguments)));
            return ActionResult.ok();
        }
        ExperienceSupport.setTotalExperience(context.player(), Math.max(0, context.player().getTotalExperience() - amount(arguments)));
        return ActionResult.ok();
    }
}
