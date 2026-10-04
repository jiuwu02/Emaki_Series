package emaki.jiuwu.craft.corelib.action.builtin.stage;

import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.action.pipeline.exec.PipelineTaskService;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class StopTaskStage extends BaseStage {

    private final PipelineTaskService tasks;

    public StopTaskStage(@Nullable PipelineTaskService tasks) {
        super("stop_task", "task", "按键取消正在运行的任务。",
                CoreTargetRequirement.NONE, CoreActionExecutionDomain.SERVER_GLOBAL,
                CoreStageParameter.required("key", CoreStageParameterType.STRING,
                        "要取消的任务键"),
                CoreStageParameter.optional("match", CoreStageParameterType.STRING, "exact",
                        "exact 或 prefix"));
        this.tasks = tasks;
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        if (tasks == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.MISSING_CONTEXT,
                    "action.stage.task.service_unavailable");
        }
        String key = arguments.getString("key");
        if (Texts.isBlank(key)) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.task.key_required");
        }
        boolean prefix = "prefix".equalsIgnoreCase(arguments.getString("match", "exact"));
        int cancelled = tasks.stop(key, prefix);
        if (cancelled == 0) {
            return CoreActionOutcome.skipped("action.stage.task.none_matched");
        }
        return CoreActionOutcome.success(Map.of("key", key, "cancelled", cancelled));
    }
}
