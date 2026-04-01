package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.Map;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionResult;

public final class GiveExpAction extends AbstractExperienceAction {

    public GiveExpAction() {
        super("giveexp", "Give experience.");
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        ActionResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        if (levels(arguments)) {
            context.player().giveExpLevels(amount(arguments));
        } else {
            context.player().giveExp(amount(arguments));
        }
        return ActionResult.ok();
    }
}
