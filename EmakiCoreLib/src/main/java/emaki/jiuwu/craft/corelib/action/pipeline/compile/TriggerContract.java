package emaki.jiuwu.craft.corelib.action.pipeline.compile;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.action.pipeline.compile.PhaseContract;

/**
 * Contract declared by one trigger across all of its phases.
 *
 * <p>A simple trigger usually has exactly one phase, but skill casting, recipe processing and other
 * multi-step systems can expose different context at different points. This record keeps those phase
 * declarations together and gives runtime callers one stable lookup API.</p>
 *
 * @param triggerId owning trigger id
 * @param phases declared phase contracts keyed by phase id
 * @param fallback contract used when an unknown phase is requested
 */
public record TriggerContract(@NotNull String triggerId,
        @NotNull Map<String, PhaseContract> phases,
        @NotNull PhaseContract fallback) {

    public TriggerContract {
        triggerId = Texts.isBlank(triggerId) ? "default" : Texts.trim(triggerId);
        phases = copyPhases(phases);
        fallback = fallback == null ? PhaseContract.permissive(triggerId) : fallback;
    }

    /**
     * Creates a trigger contract from declared phases.
     *
     * @param triggerId trigger id
     * @param phases phase contracts
     * @return the trigger contract
     */
    public static @NotNull TriggerContract declared(@NotNull String triggerId,
            @Nullable Collection<PhaseContract> phases) {
        Map<String, PhaseContract> mapped = new LinkedHashMap<>();
        if (phases != null) {
            for (PhaseContract phase : phases) {
                if (phase != null) {
                    mapped.put(key(phase.phaseId()), phase);
                }
            }
        }
        return new TriggerContract(triggerId, mapped, PhaseContract.permissive(triggerId));
    }

    /**
     * Creates a permissive trigger contract.
     *
     * @param triggerId trigger id
     * @return the trigger contract
     */
    public static @NotNull TriggerContract permissive(@Nullable String triggerId) {
        return new TriggerContract(Texts.isBlank(triggerId) ? "default" : Texts.trim(triggerId),
                Map.of(), PhaseContract.permissive(triggerId));
    }

    /**
     * Resolves the phase contract for one phase id.
     *
     * @param phaseId phase id
     * @return a declared phase, or the trigger fallback when none exists
     */
    public @NotNull PhaseContract phase(@Nullable String phaseId) {
        PhaseContract contract = phases.get(key(phaseId));
        return contract == null ? fallback : contract;
    }

    private static Map<String, PhaseContract> copyPhases(Map<String, PhaseContract> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, PhaseContract> copy = new LinkedHashMap<>();
        source.forEach((name, contract) -> {
            if (contract != null) {
                copy.put(key(Texts.isBlank(name) ? contract.phaseId() : name), contract);
            }
        });
        return copy.isEmpty() ? Map.of() : Map.copyOf(copy);
    }

    private static String key(@Nullable String value) {
        return Texts.isBlank(value) ? "default" : Texts.lower(Texts.trim(value));
    }
}
