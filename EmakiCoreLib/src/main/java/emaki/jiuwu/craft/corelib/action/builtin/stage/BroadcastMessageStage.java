package emaki.jiuwu.craft.corelib.action.builtin.stage;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import net.kyori.adventure.text.Component;

/**
 * Broadcasts a MiniMessage chat message to every online player and the console.
 *
 * <p>Target requirement {@code NONE} matters here: it tells the validator this stage needs no target, so a
 * pipeline that is nothing but {@code broadcast_message text="..."} does not get the implicit {@code self}
 * source that decision Q4 adds to every other stage. A broadcast has no subject.</p>
 *
 * <p>Domain {@code SERVER_GLOBAL}: iterates the server-wide player list.</p>
 */
public final class BroadcastMessageStage extends BaseStage {

    public BroadcastMessageStage() {
        super("broadcast_message", "message", "Broadcasts a MiniMessage chat message.",
                CoreTargetRequirement.NONE, CoreActionExecutionDomain.SERVER_GLOBAL,
                CoreStageParameter.required("text", CoreStageParameterType.STRING, "Message text"));
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Component component = MiniMessages.parse(arguments.getString("text"));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(component);
        }
        Bukkit.getConsoleSender().sendMessage(component);
        return CoreActionOutcome.success();
    }
}
