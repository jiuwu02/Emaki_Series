package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.Map;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionResult;

public final class ExtinguishAction extends BaseAction {

    public ExtinguishAction() {
        super("extinguish", "player", "Extinguish the player.");
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        ActionResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        int before = context.player().getFireTicks();
        context.player().setFireTicks(0);
        return ActionResult.ok(Map.of("fire_ticks_before", before, "fire_ticks_after", 0));
    }
}
