package emaki.jiuwu.craft.corelib.api.action;

import java.util.List;

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
