package emaki.jiuwu.craft.corelib.action.v2.exec;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreGateResult;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreSourceResult;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageKind;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.runtime.ExecutionDomain;

/**
 * Everything the interpreter needs in order to run one stage.
 *
 * <p>A seam over the registry rather than a direct dependency, so the interpreter's target iteration,
 * grouping, and failure handling can be exercised without a Bukkit server.</p>
 */
public interface StageInvoker {

    /**
     * Resolves a stage for execution.
     *
     * @param id stage id
     * @return the handle, or {@code null} when the stage is not currently usable
     */
    @Nullable
    Handle resolve(@Nullable String id);

    /**
     * One executable stage.
     *
     * @param id stage id
     * @param kind stage role
     * @param parameters declared parameters, used to apply defaults
     * @param targetRequirement what it needs from the flow
     * @param timeoutMillis its declared per-invocation timeout
     * @param foldable whether this stage touches no Bukkit state and may therefore be merged into
     *        either neighbouring domain without a dispatch of its own. True only for
     *        {@code CoreGateThread#PURE} gates.
     */
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

        /** Creates a handle that needs its own dispatch. */
        public Handle(@NotNull String id,
                @NotNull CoreStageKind kind,
                @NotNull List<CoreStageParameter> parameters,
                @NotNull CoreTargetRequirement targetRequirement,
                long timeoutMillis) {
            this(id, kind, parameters, targetRequirement, timeoutMillis, false);
        }

        /** {@return whether this stage acts once per target rather than once per flow} */
        public boolean perTarget() {
            return kind == CoreStageKind.ACTION && targetRequirement != CoreTargetRequirement.NONE;
        }
    }

    /**
     * Reads the scheduler domain a stage declared for this invocation.
     *
     * @param handle the stage
     * @param context current context
     * @param target the subject the stage is about to act on
     * @param rawArguments the stage's arguments before placeholder substitution
     * @return the declared domain, never {@code null} because registration rejected undeclared stages
     */
    @NotNull
    ExecutionDomain domainOf(@NotNull Handle handle,
            @NotNull CoreStageContext context,
            @NotNull CoreActionSubject target,
            @NotNull java.util.Map<String, String> rawArguments);

    /**
     * Invokes a source stage.
     *
     * @param handle the stage
     * @param context current context
     * @param arguments resolved arguments
     * @return the source result
     */
    @NotNull
    CoreSourceResult invokeSource(@NotNull Handle handle,
            @NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments);

    /**
     * Invokes a gate stage.
     *
     * @param handle the stage
     * @param context current context
     * @param inbound the flow entering the gate
     * @param arguments resolved arguments
     * @return the gate result
     */
    @NotNull
    CoreGateResult invokeGate(@NotNull Handle handle,
            @NotNull CoreStageContext context,
            @NotNull List<CoreActionSubject> inbound,
            @NotNull CoreResolvedArguments arguments);

    /**
     * Invokes an action stage against {@link CoreStageContext#currentTarget()}.
     *
     * @param handle the stage
     * @param context current context
     * @param arguments resolved arguments
     * @return the outcome
     */
    @NotNull
    CoreActionOutcome invokeAction(@NotNull Handle handle,
            @NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments);
}
