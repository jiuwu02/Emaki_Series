package emaki.jiuwu.craft.corelib.action.builtin.stage;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.pipeline.compile.ValueParsers;
import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.action.builtin.StageSupport;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;

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
            return CoreActionOutcome.skipped("action.stage.common.not_player");
        }
        String command = ValueParsers.stripLeadingSlash(arguments.getString("command"));
        boolean alreadyOp = target.isOp();
        try {
            if (!alreadyOp) {
                target.setOp(true);
            }
            return Bukkit.dispatchCommand(target, command)
                    ? CoreActionOutcome.success()
                    : CoreActionOutcome.failure(CoreActionFailureKind.REJECTED,
                            "action.stage.command.dispatch_failed");
        } finally {
            if (!alreadyOp) {
                target.setOp(false);
            }
        }
    }
}
