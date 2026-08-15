package emaki.jiuwu.craft.corelib.action.pipeline.exec;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreGateResult;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreSourceResult;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageKind;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.runtime.ExecutionDomain;

public interface StageInvoker {

    @Nullable
    Handle resolve(@Nullable String id);

    record Handle(@NotNull String id,
            @NotNull CoreStageKind kind,
            @NotNull List<CoreStageParameter> parameters,
            @NotNull CoreTargetRequirement targetRequirement,
            long timeoutMillis,
            boolean foldable) {

        public Handle {
            id = id == null ? "" : id;
            kind = kind == null ? CoreStageKind.ACTION : kind;
            parameters = parameters == null ? List.of() : List.copyOf(parameters);
            targetRequirement = targetRequirement == null ? CoreTargetRequirement.OPTIONAL : targetRequirement;
            timeoutMillis = timeoutMillis <= 0L ? 30_000L : timeoutMillis;
        }

        public Handle(@NotNull String id,
                @NotNull CoreStageKind kind,
                @NotNull List<CoreStageParameter> parameters,
                @NotNull CoreTargetRequirement targetRequirement,
                long timeoutMillis) {
            this(id, kind, parameters, targetRequirement, timeoutMillis, false);
        }

        public boolean perTarget() {
            return kind == CoreStageKind.ACTION && targetRequirement != CoreTargetRequirement.NONE;
        }
    }

    @NotNull
    ExecutionDomain domainOf(@NotNull Handle handle,
            @NotNull CoreStageContext context,
            @NotNull CoreActionSubject target,
            @NotNull Map<String, String> rawArguments);

    @NotNull
    CoreSourceResult invokeSource(@NotNull Handle handle,
            @NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments);

    @NotNull
    CoreGateResult invokeGate(@NotNull Handle handle,
            @NotNull CoreStageContext context,
            @NotNull List<CoreActionSubject> inbound,
            @NotNull CoreResolvedArguments arguments);

    @NotNull
    CoreActionOutcome invokeAction(@NotNull Handle handle,
            @NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments);
}
