package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.Map;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class IgniteAction extends BaseAction {

    public IgniteAction() {
        super(
                "ignite",
                "player",
                "Set the player on fire for a duration.",
                ActionParameter.optional("duration", ActionParameterType.TIME, "5s", "Fire duration")
        );
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        ActionResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        String duration = stringArg(arguments, "duration");
        long ticks = Math.max(0L, ActionParsers.parseTicks(Texts.isBlank(duration) ? "5s" : duration));
        Player player = context.player();
        player.setFireTicks((int) Math.min(Integer.MAX_VALUE, ticks));
        return ActionResult.ok(Map.of("fire_ticks", player.getFireTicks()));
    }
}
