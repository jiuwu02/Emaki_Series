package emaki.jiuwu.craft.corelib.action.builtin.v2.stage;

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.v2.BaseStage;
import emaki.jiuwu.craft.corelib.action.builtin.v2.StageSupport;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.text.MiniMessages;

/**
 * Sends a MiniMessage chat message to the target.
 *
 * <p>Domain {@code CONTEXT_ENTITY}: sending to a player writes to that player's connection.</p>
 */
public final class SendMessageStage extends BaseStage {

    public SendMessageStage() {
        super("send_message", "message", "Sends a MiniMessage chat message.",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.required("text", CoreStageParameterType.STRING, "Message text"));
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Entity target = StageSupport.entity(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.v2.stage.common.not_entity");
        }
        target.sendMessage(MiniMessages.parse(arguments.getString("text")));
        return CoreActionOutcome.success();
    }
}
