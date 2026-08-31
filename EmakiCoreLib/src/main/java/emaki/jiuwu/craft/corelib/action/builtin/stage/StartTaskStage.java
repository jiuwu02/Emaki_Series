package emaki.jiuwu.craft.corelib.action.builtin.stage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.action.pipeline.PipelineContext;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.CompiledPipeline;
import emaki.jiuwu.craft.corelib.action.pipeline.exec.ConfiguredSequenceRepository;
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

public final class StartTaskStage extends BaseStage {

    private final PipelineTaskService tasks;
    private final SequenceSource sequences;

    public StartTaskStage(@Nullable PipelineTaskService tasks, @Nullable SequenceSource sequences) {
        super("start_task", "task", "Starts a named sequence repeating on an interval.",
                CoreTargetRequirement.OPTIONAL, CoreActionExecutionDomain.SERVER_GLOBAL,
                CoreStageParameter.required("sequence", CoreStageParameterType.STRING,
                        "Name of the sequence to repeat"),
                CoreStageParameter.optional("times", CoreStageParameterType.INTEGER, "1",
                        "How many iterations; capped by action.pipeline.max_repeat_times"),
                CoreStageParameter.optional("interval", CoreStageParameterType.DURATION, "20t",
                        "Delay between iterations"),
                CoreStageParameter.optional("initial_delay", CoreStageParameterType.DURATION, "0t",
                        "Delay before the first iteration"),
                CoreStageParameter.optional("key", CoreStageParameterType.STRING, "",
                        "De-duplication key; defaults to a generated one"),
                CoreStageParameter.optional("on_conflict", CoreStageParameterType.STRING, "replace",
                        "replace, ignore or allow_duplicate"),
                CoreStageParameter.optional("stop_when_offline", CoreStageParameterType.BOOLEAN, "true",
                        "Stop once the owning player is offline"),
                CoreStageParameter.optional("stop_when_dead", CoreStageParameterType.BOOLEAN, "false",
                        "Stop once the owning entity is dead"),
                CoreStageParameter.optional("stop_when", CoreStageParameterType.STRING, "",
                        "Stop once this condition stops holding"),
                CoreStageParameter.optional("stop_on_failure", CoreStageParameterType.BOOLEAN, "false",
                        "Stop when an iteration fails"));
        this.tasks = tasks;
        this.sequences = sequences;
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        if (tasks == null || sequences == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.MISSING_CONTEXT,
                    "action.stage.task.service_unavailable");
        }
        if (!(context instanceof PipelineContext pipeline)) {
            return CoreActionOutcome.failure(CoreActionFailureKind.MISSING_CONTEXT,
                    "action.stage.task.context_unavailable");
        }
        String sequence = arguments.getString("sequence");
        if (Texts.isBlank(sequence)) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.task.sequence_required");
        }
        List<CompiledPipeline> body = sequences.bodyOf(sequence);
        if (body == null || body.isEmpty()) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.task.unknown_sequence", Map.of("sequence", sequence));
        }
        PipelineTaskService.Request request = new PipelineTaskService.Request(
                body,
                pipeline,
                arguments.getString("key", ""),
                PipelineTaskService.Conflict.parse(arguments.getString("on_conflict", "replace")),
                arguments.getInt("times", 1),
                arguments.getDurationTicks("interval", 20L),
                arguments.getDurationTicks("initial_delay", 0L),
                arguments.getBoolean("stop_when_offline", true),
                arguments.getBoolean("stop_when_dead", false),
                arguments.getString("stop_when", ""),
                arguments.getBoolean("stop_on_failure", false),
                passthroughParameters(arguments));
        PipelineTaskService.Result result = tasks.start(request);
        if (!result.successful()) {
            return CoreActionOutcome.failure(CoreActionFailureKind.REJECTED,
                    String.valueOf(result.reasonKey()), Map.of("sequence", sequence));
        }
        return CoreActionOutcome.success(Map.of("sequence", sequence, "key",
                String.valueOf(result.key())));
    }

    private Map<String, String> passthroughParameters(CoreResolvedArguments arguments) {
        Map<String, String> values = new LinkedHashMap<>();
        arguments.raw().forEach((name, value) -> {
            if (!DECLARED.contains(name)) {
                values.put(name, value);
            }
        });
        return values;
    }

    private static final List<String> DECLARED = List.of("sequence", "times", "interval",
            "initial_delay", "key", "on_conflict", "stop_when_offline", "stop_when_dead",
            "stop_when", "stop_on_failure");

    public interface SequenceSource {

        @Nullable
        List<CompiledPipeline> bodyOf(@Nullable String name);

        static @NotNull SequenceSource of(@Nullable ConfiguredSequenceRepository repository) {
            return name -> repository == null ? null : repository.bodyOf(name);
        }
    }
}
