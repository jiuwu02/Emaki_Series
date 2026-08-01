package emaki.jiuwu.craft.corelib.api.action;

import java.util.List;
import java.util.Set;

import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionTarget;
import org.jetbrains.annotations.NotNull;

/**
 * A pipeline action: consumes the target flow to produce an effect.
 *
 * <p>This replaces {@code CoreAction}. The differences that matter:</p>
 * <ul>
 *   <li>{@link #targetRequirement()} is declared, so CoreLib checks it once instead of every
 *       implementation repeating a player check;</li>
 *   <li>{@link #requiredContext()} is declared, so a missing context key is a config-load error
 *       rather than a runtime null;</li>
     *   <li>{@link #executionTarget(CoreStagePlanningContext)} has no default, so no stage can stay
     *       undeclared and be refused on Folia at runtime;</li>
 *   <li>arguments arrive as {@link CoreResolvedArguments} rather than a raw string map.</li>
 * </ul>
 */
public interface CoreActionStage {

    /** Default per-stage timeout in milliseconds. */
    long DEFAULT_TIMEOUT_MILLIS = 30_000L;

    /** {@return the stage name used in pipeline text} */
    @NotNull
    String id();

    /** {@return a short human-readable description} */
    @NotNull
    String description();

    /** {@return a category used for listing and documentation} */
    @NotNull
    String category();

    /** {@return the stage implementation version} */
    @NotNull
    default String version() {
        return "1.0.0";
    }

    /** {@return declared arguments accepted by this stage} */
    @NotNull
    default List<CoreStageParameter> parameters() {
        return List.of();
    }

    /** {@return what this stage needs from the target flow} */
    @NotNull
    default CoreTargetRequirement targetRequirement() {
        return CoreTargetRequirement.OPTIONAL;
    }

    /**
     * Context keys this stage reads.
     *
     * <p>CoreLib matches these against what the triggering phase declares it provides, so an
     * unsatisfiable pipeline is rejected at load time.</p>
     *
     * @return the required keys
     */
    @NotNull
    default Set<CoreActionKey<?>> requiredContext() {
        return Set.of();
    }

    /**
     * Declares the scheduler domain this stage needs.
     *
     * <p>No default is provided on purpose. A stage declaring
     * {@code CoreActionExecutionDomain#ASYNC_COMPUTE} must also declare
     * {@link CoreTargetRequirement#NONE}, otherwise CoreLib refuses the registration.</p>
     *
     * @param context planning information about the pending invocation
     * @return the declared domain
     */
    @NotNull
    CoreActionExecutionTarget executionTarget(@NotNull CoreStagePlanningContext context);

    /** {@return timeout in milliseconds for one invocation of this stage} */
    default long timeoutMillis() {
        return DEFAULT_TIMEOUT_MILLIS;
    }

    /**
     * Executes this stage against {@link CoreStageContext#currentTarget()}.
     *
     * @param context read-only pipeline context
     * @param arguments resolved arguments
     * @return the outcome
     */
    @NotNull
    CoreActionOutcome execute(@NotNull CoreStageContext context, @NotNull CoreResolvedArguments arguments);
}
