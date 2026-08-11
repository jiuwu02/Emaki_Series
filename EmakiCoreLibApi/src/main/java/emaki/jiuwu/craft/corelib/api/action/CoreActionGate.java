package emaki.jiuwu.craft.corelib.api.action;

import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

/**
 * A pipeline gate: filters, reorders, or controls the target flow.
 *
 * <p>Gates are what {@code where}, {@code chance}, {@code limit}, {@code sort_by}, {@code set},
 * {@code keep} and {@code stop} are built on. Unlike a source, a gate receives an inbound flow and
 * returns an outbound one.</p>
 */
public interface CoreActionGate {

    /** {@return the stage name used in pipeline text} */
    @NotNull
    String id();

    /** {@return a short human-readable description} */
    @NotNull
    String description();

    /** {@return a category used for listing and documentation} */
    @NotNull
    default String category() {
        return "gate";
    }

    /** {@return declared arguments accepted by this gate} */
    @NotNull
    default List<CoreStageParameter> parameters() {
        return List.of();
    }

    /**
     * Typed context keys this gate publishes when it passes.
     *
     * <p>CoreLib uses this at compile time to make a later action's {@code requiredContext()} check
     * understand producer gates such as {@code create_item}. Dynamic failures are still reported at
     * runtime by the gate itself.</p>
     *
     * @return the keys this gate can add to the pipeline context
     */
    @NotNull
    default Set<CoreActionKey<?>> providedContext() {
        return Set.of();
    }

    /**
     * Pipeline variable names this gate publishes when it passes.
     *
     * <p>Names are declared without the {@code var.} prefix; callers read them as {@code %var.name%}.
     * Gates with author-chosen names, such as {@code set}, can return an empty set and let the compiler
     * derive written assignments from the current invocation.</p>
     *
     * @return the variables this gate can add to the pipeline context
     */
    @NotNull
    default Set<String> providedVariables() {
        return Set.of();
    }

    /**
     * Declares what thread access this gate needs.
     *
     * <p>Unlike sources and actions this has a default, because {@link CoreGateThread#PURE} is both
     * the common case and the safe one: a pure gate touches no Bukkit state, so it cannot be wrong on
     * Folia. Declaring a stricter value is a widening of requirements, never a narrowing.</p>
     *
     * @return the thread requirement
     */
    @NotNull
    default CoreGateThread threadNeed() {
        return CoreGateThread.PURE;
    }

    /**
     * Applies this gate.
     *
     * @param context read-only pipeline context
     * @param inbound the flow entering this gate
     * @param arguments resolved arguments
     * @return the gate decision
     */
    @NotNull
    CoreGateResult apply(@NotNull CoreStageContext context,
            @NotNull List<CoreActionSubject> inbound,
            @NotNull CoreResolvedArguments arguments);
}
