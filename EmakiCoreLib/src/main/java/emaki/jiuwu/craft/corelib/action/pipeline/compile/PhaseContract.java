package emaki.jiuwu.craft.corelib.action.pipeline.compile;

import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreActionKey;

/**
 * What a triggering phase promises to provide.
 *
 * <p>Matching this against each stage's {@code requiredContext()} turns a runtime null into a
 * config-load error. Registered in CoreLib alongside the stages (requirement R1), so the check is a
 * local table lookup rather than a cross-plugin query.</p>
 *
 * @param phaseId phase or trigger id
 * @param providedKeys typed context keys the trigger fills in
 * @param providedVariables pipeline variable names the trigger sets
 * @param inheritsTargets whether this phase can supply an inherited target flow
 * @param permissive when true, every context requirement is treated as satisfied. This is the state
 *        of a phase that never declared a contract; declaring one is how a module opts into the
 *        stricter check, so adding the check cannot break configuration that already works.
 */
public record PhaseContract(@NotNull String phaseId,
        @NotNull Set<CoreActionKey<?>> providedKeys,
        @NotNull Set<String> providedVariables,
        boolean inheritsTargets,
        boolean permissive) {

    public PhaseContract {
        phaseId = phaseId == null ? "" : phaseId;
        providedKeys = providedKeys == null ? Set.of() : Set.copyOf(providedKeys);
        providedVariables = providedVariables == null ? Set.of() : Set.copyOf(providedVariables);
    }

    /**
     * Creates a declared contract.
     *
     * @param phaseId phase id
     * @param providedKeys context keys the trigger fills in
     * @param providedVariables variable names the trigger sets
     * @param inheritsTargets whether the phase can supply inherited targets
     * @return the contract
     */
    public static @NotNull PhaseContract declared(@NotNull String phaseId,
            @Nullable Set<CoreActionKey<?>> providedKeys,
            @Nullable Set<String> providedVariables,
            boolean inheritsTargets) {
        return new PhaseContract(phaseId, providedKeys, providedVariables, inheritsTargets, false);
    }

    /**
     * A permissive contract, used where no trigger declared itself.
     *
     * @param phaseId phase id
     * @return a contract that satisfies any requirement
     */
    public static @NotNull PhaseContract permissive(@Nullable String phaseId) {
        return new PhaseContract(phaseId == null ? "default" : phaseId, Set.of(), Set.of(), true, true);
    }

    /**
     * Tests whether this contract satisfies a stage requirement.
     *
     * @param key the required context key
     * @return whether the trigger provides it
     */
    public boolean provides(@Nullable CoreActionKey<?> key) {
        return permissive || (key != null && providedKeys.contains(key));
    }
}
