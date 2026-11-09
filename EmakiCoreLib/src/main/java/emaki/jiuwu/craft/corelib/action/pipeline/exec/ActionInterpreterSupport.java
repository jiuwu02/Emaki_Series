package emaki.jiuwu.craft.corelib.action.pipeline.exec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.ValueParsers;
import emaki.jiuwu.craft.corelib.action.pipeline.PipelineContext;
import emaki.jiuwu.craft.corelib.action.pipeline.ResolvedArguments;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.ActionAst;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.CompiledPipeline;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.PipelineLimits;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.PipelineParser;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.StaticValidator;
import emaki.jiuwu.craft.corelib.random.WeightedPool;
import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreActionKeys;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreGateResult;
import emaki.jiuwu.craft.corelib.api.action.CoreSourceResult;
import emaki.jiuwu.craft.corelib.api.action.CoreStageKind;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.runtime.ExecutionDomain;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import java.util.LinkedHashSet;

final class ActionInterpreterSupport {

    static long groupTimeout(StageGroup group) {

        long timeout = 0L;
        for (StageGroup.Member member : group.members()) {
            timeout = Math.max(timeout, member.handle().timeoutMillis());
        }
        return timeout <= 0L ? 30_000L : timeout;
}

    static String groupName(StageGroup group) {
        StringBuilder builder = new StringBuilder();
        for (StageGroup.Member member : group.members()) {
            if (!builder.isEmpty()) {
                builder.append('+');
            }
            builder.append(member.handle().id());
        }
        return builder.toString();
}

    static CoreActionOutcome throwableOutcome(Throwable throwable) {
        Throwable cause = throwable;
        while (cause instanceof CompletionException
                || cause instanceof ExecutionException) {
            cause = cause.getCause();
        }
        if (cause instanceof TimeoutException) {
            return CoreActionOutcome.failure(CoreActionFailureKind.TIMEOUT, "action.run.timeout");
        }
        if (cause instanceof CancellationException) {
            return CoreActionOutcome.failure(CoreActionFailureKind.OWNER_DISABLED, "action.run.cancelled");
        }
        if (cause instanceof StageDispatcher.OwnerDisabledException) {
            return CoreActionOutcome.failure(CoreActionFailureKind.OWNER_DISABLED,
                    "action.run.owner_disabled", Map.of("error", Texts.toStringSafe(cause.getMessage())));
        }
        if (cause instanceof StageDispatcher.StageRetiredException) {
            return CoreActionOutcome.failure(CoreActionFailureKind.MISSING_CONTEXT,
                    "action.run.target_retired", Map.of("error", Texts.toStringSafe(cause.getMessage())));
        }
        return CoreActionOutcome.failure(CoreActionFailureKind.INTERNAL_ERROR, "action.run.exception",
                Map.of("error", cause == null ? "" : Texts.toStringSafe(cause.getMessage())));
}

    static CoreActionOutcome.Failure firstFailure(List<CoreActionOutcome> outcomes) {
        for (CoreActionOutcome outcome : outcomes) {
            if (outcome instanceof CoreActionOutcome.Failure failure) {
                return failure;
            }
        }
        return new CoreActionOutcome.Failure(CoreActionFailureKind.INTERNAL_ERROR,
                "action.run.exception", Map.of());
}

    static ResolvedArguments arguments(StageInvoker.Handle handle, Map<String, String> rendered) {
        return ResolvedArguments.of(rendered, handle.parameters());
}

    static Map<String, String> render(PipelineContext context, Map<String, String> raw) {
        if (raw.isEmpty()) {
            return Map.of();
        }
        Map<String, String> rendered = new LinkedHashMap<>();
        raw.forEach((key, value) -> rendered.put(key, context.render(value)));
        return Map.copyOf(rendered);
}

    static Entity entityTarget(PipelineContext context, CoreActionSubject subject) {
        Entity entity = subject.entityOrNull();
        return entity == null ? context.caster().entityOrNull() : entity;
}
}
