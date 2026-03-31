package emaki.jiuwu.craft.corelib.action.builtin;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import java.util.Map;

public final class SetExpAction extends AbstractExperienceAction {

    public SetExpAction() {
        super("setexp", "Set experience.");
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        ActionResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        if (levels(arguments)) {
            context.player().setLevel(amount(arguments));
            return ActionResult.ok();
        }
        ExperienceSupport.setTotalExperience(context.player(), amount(arguments));
        return ActionResult.ok();
    }
}
