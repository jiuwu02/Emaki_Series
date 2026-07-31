package emaki.jiuwu.craft.corelib.action.builtin.v2.stage;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.corelib.action.builtin.v2.BaseStage;
import emaki.jiuwu.craft.corelib.action.builtin.v2.StageSupport;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreTargetRequirement;

/**
 * Runs a command as the target player, with the target's own permissions.
 *
 * <p>A leading slash is stripped, so both {@code command="/spawn"} and {@code command="spawn"} work.</p>
 *
 * <p>Domain {@code CONTEXT_ENTITY}: dispatched on behalf of one player, and most commands act on them.</p>
 */
public final class RunCommandAsPlayerStage extends BaseStage {

    public RunCommandAsPlayerStage() {
        super("run_command_as_player", "command", "Runs a command as the target player.",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.required("command", CoreStageParameterType.STRING, "Command line"));
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Player target = StageSupport.player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.v2.stage.common.not_player");
        }
        String command = ActionParsers.stripLeadingSlash(arguments.getString("command"));
        return Bukkit.dispatchCommand(target, command)
                ? CoreActionOutcome.success()
                : CoreActionOutcome.failure(CoreActionFailureKind.REJECTED,
                        "action.v2.stage.command.dispatch_failed");
    }
}
