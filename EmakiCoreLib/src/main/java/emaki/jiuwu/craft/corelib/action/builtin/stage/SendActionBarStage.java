package emaki.jiuwu.craft.corelib.action.builtin.stage;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.action.builtin.StageSupport;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;

/**
 * Sends an action bar message to the target.
 *
 * <p>Needs a {@code Player}, not merely an entity: the action bar is a client-side HUD element, so a
 * non-player target is {@code Skipped}.</p>
 *
 * <p>Domain {@code CONTEXT_ENTITY}: writes to that player's connection.</p>
 */
public final class SendActionBarStage extends BaseStage {

    public SendActionBarStage() {
        super("send_action_bar", "message", "Sends an action bar message.",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.required("text", CoreStageParameterType.STRING, "Action bar text"));
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Player target = StageSupport.player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.stage.common.not_player");
        }
        target.sendActionBar(MiniMessages.parse(arguments.getString("text")));
        return CoreActionOutcome.success();
    }
}
