package emaki.jiuwu.craft.corelib.api.action;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One dispatch of a registered trigger: which business moment fired, for whom, with what values.
 *
 * <h2>No live entity references</h2>
 * <p>This carrier holds a caster {@link UUID} and a trigger <em>name</em>, never an
 * {@code org.bukkit.entity.Entity}. That is not a stylistic choice: a pipeline may be compiled long
 * before it runs, and CoreLib's existing {@code CoreActionKeys#TRIGGER} deliberately stores a name or
 * UUID string for the same reason — a live entity held in a context key cannot be collected. The
 * {@code trigger} source segment resolves the name back to an online player itself.</p>
 *
 * <h2>What it does not carry</h2>
 * <p>There is no target list. A trigger declares the moment; the pipeline's own source segment decides
 * what the moment acts on. Passing targets in would make the same trigger behave differently depending
 * on the caller and defeat the point of configurable pipelines.</p>
 *
 * @param phase phase name within the trigger, matched against the trigger's declared phase contracts
 * @param casterId the acting player's unique id, or {@code null} for server-side moments
 * @param triggerName name read by the {@code trigger} source segment; may differ from the caster
 * @param silent whether player-facing feedback is suppressed
 * @param variables values readable as {@code %var.name%}; keys are used verbatim
 * @param lines the configured pipeline lines to run
 */
@ApiStatus.Experimental
public record CoreTriggerDispatch(@NotNull String phase,
        @Nullable UUID casterId,
        @NotNull String triggerName,
        boolean silent,
        @NotNull Map<String, String> variables,
        @NotNull List<String> lines) {

    public CoreTriggerDispatch {
        phase = phase == null || phase.isBlank() ? "default" : phase.trim();
        triggerName = triggerName == null ? "" : triggerName.trim();
        variables = copyVariables(variables);
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    /**
     * Creates a dispatch for one player-driven moment.
     *
     * @param phase phase name within the trigger
     * @param casterId the acting player's unique id
     * @param lines the configured pipeline lines to run
     * @return the dispatch
     */
    public static @NotNull CoreTriggerDispatch of(@Nullable String phase,
            @Nullable UUID casterId,
            @Nullable List<String> lines) {
        return new CoreTriggerDispatch(phase, casterId, "", false, Map.of(), lines);
    }

    /**
     * Creates a dispatch for a server-side moment with no acting player.
     *
     * @param phase phase name within the trigger
     * @param lines the configured pipeline lines to run
     * @return the dispatch
     */
    public static @NotNull CoreTriggerDispatch server(@Nullable String phase,
            @Nullable List<String> lines) {
        return new CoreTriggerDispatch(phase, null, "", false, Map.of(), lines);
    }

    /**
     * Returns a copy carrying the given variables.
     *
     * @param values values readable as {@code %var.name%}
     * @return a new dispatch
     */
    public @NotNull CoreTriggerDispatch withVariables(@Nullable Map<String, String> values) {
        return new CoreTriggerDispatch(phase, casterId, triggerName, silent, values, lines);
    }

    /**
     * Returns a copy carrying the given trigger name.
     *
     * @param name name the {@code trigger} source segment resolves
     * @return a new dispatch
     */
    public @NotNull CoreTriggerDispatch withTriggerName(@Nullable String name) {
        return new CoreTriggerDispatch(phase, casterId, name, silent, variables, lines);
    }

    /**
     * Returns a copy with player-facing feedback suppressed or restored.
     *
     * @param suppressed whether feedback is suppressed
     * @return a new dispatch
     */
    public @NotNull CoreTriggerDispatch withSilent(boolean suppressed) {
        return new CoreTriggerDispatch(phase, casterId, triggerName, suppressed, variables, lines);
    }

    /** {@return whether a {@code trigger} name was supplied} */
    public boolean hasTriggerName() {
        return !triggerName.isEmpty();
    }

    private static Map<String, String> copyVariables(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, String> copy = new LinkedHashMap<>();
        source.forEach((name, value) -> {
            if (name != null && !name.isBlank() && value != null) {
                copy.put(name.trim(), value);
            }
        });
        return copy.isEmpty() ? Map.of() : Map.copyOf(copy);
    }
}
