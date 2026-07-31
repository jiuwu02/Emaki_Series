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
 * Runs a command as the target player with operator status granted for its duration.
 *
 * <p>Operator status is restored in a {@code finally} block, including when the command throws, so a failed
 * dispatch cannot leave a player permanently opped. A player who was already an operator is left untouched.</p>
 *
 * <p>This grants full operator permissions for the length of one command. Prefer
 * {@code run_command_as_console} where the command does not have to appear to come from the player.</p>
 *
 * <p>Domain {@code CONTEXT_ENTITY}: reads and writes one player's operator flag.</p>
 */
public final class RunCommandAsOpStage extends BaseStage {

    public RunCommandAsOpStage() {
        super("run_command_as_op", "command", "Runs a command as the target with temporary operator status.",
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
        boolean alreadyOp = target.isOp();
        try {
            if (!alreadyOp) {
                target.setOp(true);
            }
            return Bukkit.dispatchCommand(target, command)
                    ? CoreActionOutcome.success()
                    : CoreActionOutcome.failure(CoreActionFailureKind.REJECTED,
                            "action.v2.stage.command.dispatch_failed");
        } finally {
            if (!alreadyOp) {
                target.setOp(false);
            }
        }
    }
}
