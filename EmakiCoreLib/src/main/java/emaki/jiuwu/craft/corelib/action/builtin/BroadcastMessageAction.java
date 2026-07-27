package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.Map;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class BroadcastMessageAction extends BaseAction {

    public BroadcastMessageAction() {
        super("broadcastmessage", "message", "Broadcast a MiniMessage chat message.", ActionParameter.required("text", ActionParameterType.STRING, "Message text"));
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        Component component = MiniMessages.parse(stringArg(arguments, "text"));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(component);
        }
        Bukkit.getConsoleSender().sendMessage(component);
        return ActionResult.ok();
    }
}
