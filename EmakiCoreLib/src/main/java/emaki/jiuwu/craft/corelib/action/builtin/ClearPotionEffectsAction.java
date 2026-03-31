package emaki.jiuwu.craft.corelib.action.builtin;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import java.util.Map;

public final class ClearPotionEffectsAction extends BaseAction {

    public ClearPotionEffectsAction() {
        super("clearpotioneffects", "player", "Clear all potion effects.");
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        ActionResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        context.player().getActivePotionEffects().forEach(effect -> context.player().removePotionEffect(effect.getType()));
        return ActionResult.ok();
    }
}
