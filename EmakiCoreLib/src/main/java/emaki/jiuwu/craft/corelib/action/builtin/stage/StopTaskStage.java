package emaki.jiuwu.craft.corelib.action.builtin.v2.stage;

import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.action.builtin.v2.BaseStage;
import emaki.jiuwu.craft.corelib.action.v2.exec.PipelineTaskService;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * Cancels running tasks by key.
 *
 * <p>Replaces the v1 {@code cancelloop} action. Cancelling nothing is {@code Skipped} rather than a
 * failure: configuration routinely cancels a task that may not be running, such as clearing a buff loop
 * on an event that can fire either way, and treating that as an error would fill the log with noise from
 * correct configuration.</p>
 *
 * <p>Domain {@code SERVER_GLOBAL}: the task table is server-wide state, and cancelling only touches that
 * table plus the scheduler handles it owns.</p>
 */
public final class StopTaskStage extends BaseStage {

    private final PipelineTaskService tasks;

    /**
     * Creates the stage.
     *
     * @param tasks the task service, may be {@code null} before the action system is built
     */
    public StopTaskStage(@Nullable PipelineTaskService tasks) {
        super("stop_task", "task", "Cancels running tasks by key.",
                CoreTargetRequirement.NONE, CoreActionExecutionDomain.SERVER_GLOBAL,
                CoreStageParameter.required("key", CoreStageParameterType.STRING,
                        "Key of the task to cancel"),
                CoreStageParameter.optional("match", CoreStageParameterType.STRING, "exact",
                        "exact or prefix"));
        this.tasks = tasks;
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        if (tasks == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.MISSING_CONTEXT,
                    "action.v2.stage.task.service_unavailable");
        }
        String key = arguments.getString("key");
        if (Texts.isBlank(key)) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.v2.stage.task.key_required");
        }
        boolean prefix = "prefix".equalsIgnoreCase(arguments.getString("match", "exact"));
        int cancelled = tasks.stop(key, prefix);
        if (cancelled == 0) {
            return CoreActionOutcome.skipped("action.v2.stage.task.none_matched");
        }
        return CoreActionOutcome.success(Map.of("key", key, "cancelled", cancelled));
    }
}
