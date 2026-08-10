package emaki.jiuwu.craft.corelib.api.action;

import java.util.Locale;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Information a stage may use while choosing its scheduler domain.
 *
 * <p>Deliberately does not reuse the v1 {@code CoreActionPlanningContext}, which wraps the v1 context
 * type. A stage picking a domain needs only the two subjects and its own arguments.</p>
 *
 * <p>CoreLib also calls this once at registration time with a probe instance, to verify the stage
 * declares a real domain. Implementations must therefore not assume the subjects are present.</p>
 *
 * @param caster who is about to run the pipeline
 * @param target the subject the stage is about to act on
 * @param phase the phase name
 * @param arguments the stage's raw arguments, before placeholder substitution
 */
public record CoreStagePlanningContext(@NotNull CoreActionSubject caster,
        @NotNull CoreActionSubject target,
        @NotNull String phase,
        @NotNull Map<String, String> arguments) {

    public CoreStagePlanningContext {
        caster = caster == null ? CoreActionSubject.absent() : caster;
        target = target == null ? CoreActionSubject.absent() : target;
        phase = phase == null || phase.trim().isEmpty() ? "default" : phase.trim();
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }

    /**
     * The probe instance CoreLib passes at registration time.
     *
     * @return a context with no subjects and no arguments
     */
    public static @NotNull CoreStagePlanningContext probe() {
        return new CoreStagePlanningContext(CoreActionSubject.absent(), CoreActionSubject.absent(),
                "default", Map.of());
    }

    /**
     * Reads one raw argument.
     *
     * @param name argument name
     * @return the raw value, or an empty string when absent
     */
    public @NotNull String argument(@Nullable String name) {
        if (name == null) {
            return "";
        }
        String value = arguments.get(name.trim().toLowerCase(Locale.ROOT));
        return value == null ? "" : value;
    }
}
