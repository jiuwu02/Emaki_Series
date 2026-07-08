package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.Map;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.corelib.action.ActionResult;

public final class FeedAction extends BaseAction {

    public FeedAction() {
        super(
                "feed",
                "player",
                "Restore player food and optional saturation.",
                ActionParameter.optional("amount", ActionParameterType.INTEGER, "20", "Food points to restore"),
                ActionParameter.optional("saturation", ActionParameterType.DOUBLE, "0", "Saturation to restore")
        );
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        ActionResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        Player player = context.player();
        int amount = Math.max(0, ActionParsers.parseInt(arguments.get("amount"), 20));
        float saturation = (float) Math.max(0D, ActionParsers.parseDouble(arguments.get("saturation"), 0D));
        int beforeFood = player.getFoodLevel();
        float beforeSaturation = player.getSaturation();
        player.setFoodLevel(Math.min(20, beforeFood + amount));
        if (saturation > 0F) {
            player.setSaturation(Math.min(20F, beforeSaturation + saturation));
        }
        return ActionResult.ok(Map.of(
                "food_before", beforeFood,
                "food_after", player.getFoodLevel(),
                "saturation_before", beforeSaturation,
                "saturation_after", player.getSaturation()
        ));
    }
}
