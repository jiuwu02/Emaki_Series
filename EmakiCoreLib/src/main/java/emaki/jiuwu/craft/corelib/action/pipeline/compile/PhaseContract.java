package emaki.jiuwu.craft.corelib.action.pipeline.compile;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreActionKey;
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * What one triggering phase promises to provide.
 *
 * <p>Matching this against each stage's {@code requiredContext()} and each {@code %var.name%}
 * reference turns a runtime null or unresolved variable into a config-load error. Registered in
 * CoreLib alongside the stages (requirement R1), so the check is a local table lookup rather than a
 * cross-plugin query.</p>
 *
 * @param phaseId phase or trigger id
 * @param providedKeys typed context keys the trigger fills in
 * @param providedVariables pipeline variable names the trigger sets, without the {@code var.} prefix
 * @param inheritsTargets whether this phase can supply an inherited target flow
 * @param permissive when true, every context and variable requirement is treated as satisfied. This
 *        is the state of a phase that never declared a contract; declaring one is how a module opts
 *        into the stricter check, so adding the check cannot break configuration that already works.
 */
public record PhaseContract(@NotNull String phaseId,
        @NotNull Set<CoreActionKey<?>> providedKeys,
        @NotNull Set<String> providedVariables,
        boolean inheritsTargets,
        boolean permissive) {

    public PhaseContract {
        phaseId = Texts.isBlank(phaseId) ? "default" : Texts.trim(phaseId);
        providedKeys = copyKeys(providedKeys);
        providedVariables = copyVariables(providedVariables);
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
        return new PhaseContract(Texts.isBlank(phaseId) ? "default" : Texts.trim(phaseId),
                Set.of(), Set.of(), true, true);
    }

    /**
     * Tests whether this contract satisfies a typed stage requirement.
     *
     * @param key the required context key
     * @return whether the trigger provides it
     */
    public boolean provides(@Nullable CoreActionKey<?> key) {
        return permissive || (key != null && providedKeys.contains(key));
    }

    /**
     * Tests whether this contract satisfies a pipeline variable requirement.
     *
     * @param variableName variable name, with or without {@code var.} and percent delimiters
     * @return whether the trigger provides it
     */
    public boolean providesVariable(@Nullable String variableName) {
        String normalized = normalizeVariableName(variableName);
        return permissive || (!normalized.isEmpty() && providedVariables.contains(normalized));
    }

    /** {@return whether {@code inherited} can read a caller-supplied target flow} */
    public boolean providesInheritedTargets() {
        return permissive || inheritsTargets;
    }

    /**
     * Cache discriminator for one contract.
     *
     * <p>Compilation output depends on the available keys, variables and inherited-target flag, not just
     * the phase name. Including all of them prevents a permissive or richer first compile from hiding a
     * stricter later invocation.</p>
     *
     * @return stable cache key text
     */
    public @NotNull String cacheKey() {
        String keys = providedKeys.stream()
                .sorted(Comparator.comparing(CoreActionKey::name))
                .map(key -> key.name() + ":" + key.type().getName())
                .collect(Collectors.joining(","));
        String variables = providedVariables.stream().sorted().collect(Collectors.joining(","));
        return phaseId + "|p=" + permissive + "|targets=" + inheritsTargets
                + "|keys=" + keys + "|vars=" + variables;
    }

    /** Normalises a variable name to the canonical storage form. */
    public static @NotNull String normalizeVariableName(@Nullable String value) {
        if (Texts.isBlank(value)) {
            return "";
        }
        String name = Texts.trim(value);
        if (name.length() >= 2 && name.charAt(0) == '%' && name.charAt(name.length() - 1) == '%') {
            name = name.substring(1, name.length() - 1);
        }
        if (name.regionMatches(true, 0, "var.", 0, 4)) {
            name = name.substring(4);
        }
        return Texts.lower(name);
    }

    private static Set<CoreActionKey<?>> copyKeys(Set<CoreActionKey<?>> keys) {
        if (keys == null || keys.isEmpty()) {
            return Set.of();
        }
        Set<CoreActionKey<?>> copy = new LinkedHashSet<>();
        for (CoreActionKey<?> key : keys) {
            if (key != null) {
                copy.add(key);
            }
        }
        return copy.isEmpty() ? Set.of() : Set.copyOf(copy);
    }

    private static Set<String> copyVariables(Set<String> variables) {
        if (variables == null || variables.isEmpty()) {
            return Set.of();
        }
        Set<String> copy = new LinkedHashSet<>();
        for (String variable : variables) {
            String normalized = normalizeVariableName(variable);
            if (!normalized.isEmpty()) {
                copy.add(normalized);
            }
        }
        return copy.isEmpty() ? Set.of() : Set.copyOf(copy);
    }
}
