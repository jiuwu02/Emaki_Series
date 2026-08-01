package emaki.jiuwu.craft.corelib.action.builtin.stage;

import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.pipeline.compile.ValueParsers;
import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;

/**
 * Runs a command as the console.
 *
 * <p>Target requirement {@code NONE}: the console is the sender, so this stage has no subject and does not get
 * decision Q4's implicit {@code self} source. A pipeline that needs the player's name in the command line still
 * writes it as a placeholder, which is rendered against the context before dispatch.</p>
 *
 * <p>Domain {@code SERVER_GLOBAL}: console commands are dispatched on the main server thread.</p>
 */
public final class RunCommandAsConsoleStage extends BaseStage {

    public RunCommandAsConsoleStage() {
        super("run_command_as_console", "command", "Runs a command as the console.",
                CoreTargetRequirement.NONE, CoreActionExecutionDomain.SERVER_GLOBAL,
                CoreStageParameter.required("command", CoreStageParameterType.STRING, "Command line"));
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        String command = ValueParsers.stripLeadingSlash(arguments.getString("command"));
        return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
                ? CoreActionOutcome.success()
                : CoreActionOutcome.failure(CoreActionFailureKind.REJECTED,
                        "action.stage.command.dispatch_failed");
    }
}
