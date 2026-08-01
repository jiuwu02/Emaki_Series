package emaki.jiuwu.craft.corelib.api.action;

import java.util.List;

import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionTarget;
import org.jetbrains.annotations.NotNull;

/**
 * A pipeline source: produces the target flow that later stages act on.
 *
 * <p>Sources sit at the start of a pipeline. Omitting the source is equivalent to writing
 * {@code self}, matching v1 semantics where an action with no target parameter affected the caster.</p>
 *
 * <p>Implementations must declare {@link #executionTarget(CoreStagePlanningContext)}; there is no
 * default, because an undeclared domain is a crash on Folia rather than an error.</p>
 */
public interface CoreActionSource {

    /** {@return the stage name used in pipeline text} */
    @NotNull
    String id();

    /** {@return a short human-readable description} */
    @NotNull
    String description();

    /** {@return a category used for listing and documentation} */
    @NotNull
    default String category() {
        return "source";
    }

    /** {@return declared arguments accepted by this source} */
    @NotNull
    default List<CoreStageParameter> parameters() {
        return List.of();
    }

    /**
     * Declares the scheduler domain this source needs.
     *
     * <p>No default is provided on purpose: every source must state its domain at registration time.</p>
     *
     * @param context planning information about the pending invocation
     * @return the declared domain
     */
    @NotNull
    CoreActionExecutionTarget executionTarget(@NotNull CoreStagePlanningContext context);

    /**
     * Selects the subjects this source produces.
     *
     * @param context read-only pipeline context
     * @param arguments resolved arguments
     * @return the selection result
     */
    @NotNull
    CoreSourceResult select(@NotNull CoreStageContext context, @NotNull CoreResolvedArguments arguments);
}
