package emaki.jiuwu.craft.corelib.api.action.execution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreActionKey;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;

/**
 * Immutable input supplied to an action execution request.
 *
 * <p>The origin is copied independently and is never derived from a live entity while this object is
 * built. Variable names are trimmed and normalised with {@link Locale#ROOT}; typed data is validated
 * against every {@link CoreActionKey} before the context is created.</p>
 */
public final class CoreActionExecutionContext {

    private final CoreActionSubject caster;
    private final List<CoreActionSubject> targets;
    private final Location origin;
    private final String phase;
    private final boolean silent;
    private final Map<String, String> variables;
    private final Map<CoreActionKey<?>, Object> data;

    private CoreActionExecutionContext(Builder builder) {
        caster = copySubject(builder.caster);
        targets = copySubjects(builder.targets);
        origin = copyLocation(builder.origin);
        phase = normalisePhase(builder.phase);
        silent = builder.silent;
        variables = copyVariables(builder.variables);
        data = copyData(builder.data);
    }

    /** {@return a new execution-context builder} */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /** {@return who requested the execution; {@code Absent} when none was supplied} */
    public @NotNull CoreActionSubject caster() {
        return copySubject(caster);
    }

    /** {@return the immutable initial target flow} */
    public @NotNull List<CoreActionSubject> targets() {
        return copySubjects(targets);
    }

    /** {@return an independent copy of the explicitly supplied origin, when present} */
    public @NotNull Optional<Location> origin() {
        return Optional.ofNullable(copyLocation(origin));
    }

    /** {@return the execution phase name} */
    public @NotNull String phase() {
        return phase;
    }

    /** {@return whether player-facing feedback should be suppressed} */
    public boolean silent() {
        return silent;
    }

    /** {@return immutable variables keyed by their normalised names} */
    public @NotNull Map<String, String> variables() {
        return variables;
    }

    /** {@return immutable typed context data} */
    public @NotNull Map<CoreActionKey<?>, Object> data() {
        return data;
    }

    /**
     * Reads a pipeline variable by its normalised name.
     *
     * @param name variable name without the {@code %var.} prefix
     * @return the value, empty when the name is blank or absent
     */
    public @NotNull Optional<String> variable(@Nullable String name) {
        String key = normaliseName(name);
        return key.isEmpty() ? Optional.empty() : Optional.ofNullable(variables.get(key));
    }

    /**
     * Reads one typed context value.
     *
     * @param key context key
     * @param <T> value type
     * @return the value, empty when absent
     */
    public <T> @NotNull Optional<T> get(@NotNull CoreActionKey<T> key) {
        if (key == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(key.cast(data.get(key)));
    }

    private static String normalisePhase(String value) {
        return value == null || value.isBlank() ? "default" : value.trim();
    }

    private static String normaliseName(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static Location copyLocation(Location value) {
        return value == null ? null : value.clone();
    }

    private static CoreActionSubject copySubject(CoreActionSubject subject) {
        if (subject == null) {
            return CoreActionSubject.absent();
        }
        if (subject instanceof CoreActionSubject.OfLocation located) {
            return new CoreActionSubject.OfLocation(located.location());
        }
        return subject;
    }

    private static List<CoreActionSubject> copySubjects(Collection<? extends CoreActionSubject> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<CoreActionSubject> copy = new ArrayList<>(source.size());
        for (CoreActionSubject subject : source) {
            copy.add(copySubject(subject));
        }
        return List.copyOf(copy);
    }

    private static Map<String, String> copyVariables(Map<String, ?> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, String> copy = new LinkedHashMap<>();
        source.forEach((name, value) -> {
            String key = normaliseName(name);
            if (!key.isEmpty() && value != null) {
                copy.put(key, String.valueOf(value));
            }
        });
        return copy.isEmpty() ? Map.of() : Map.copyOf(copy);
    }

    private static Map<CoreActionKey<?>, Object> copyData(Map<CoreActionKey<?>, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<CoreActionKey<?>, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key == null || value == null || !key.type().isInstance(value)) {
                throw new IllegalArgumentException("Execution context data does not match its typed key.");
            }
            copy.put(key, value);
        });
        return Map.copyOf(copy);
    }

    /** Builds an immutable {@link CoreActionExecutionContext}. */
    public static final class Builder {

        private CoreActionSubject caster = CoreActionSubject.absent();
        private Collection<? extends CoreActionSubject> targets = List.of();
        private Location origin;
        private String phase = "default";
        private boolean silent;
        private Map<String, String> variables = Map.of();
        private Map<CoreActionKey<?>, Object> data = Map.of();

        private Builder() {
        }

        /**
         * Sets the subject that requested the execution.
         *
         * @param value caster subject; {@code null} selects {@code Absent}
         * @return this builder
         */
        public @NotNull Builder caster(@Nullable CoreActionSubject value) {
            caster = copySubject(value);
            return this;
        }

        /**
         * Sets a live entity as the execution caster without reading its location.
         *
         * @param value caster entity; {@code null} selects {@code Absent}
         * @return this builder
         */
        public @NotNull Builder caster(@Nullable Entity value) {
            caster = CoreActionSubject.of(value);
            return this;
        }

        /**
         * Replaces the initial target flow.
         *
         * @param values target subjects
         * @return this builder
         */
        public @NotNull Builder targets(@Nullable Collection<? extends CoreActionSubject> values) {
            targets = copySubjects(values);
            return this;
        }

        /**
         * Sets the explicit spatial origin.
         *
         * @param value origin, or {@code null} to leave it absent
         * @return this builder
         */
        public @NotNull Builder origin(@Nullable Location value) {
            origin = copyLocation(value);
            return this;
        }


        /**
         * Sets the execution phase.
         *
         * @param value phase name; blank values select {@code default}
         * @return this builder
         */
        public @NotNull Builder phase(@Nullable String value) {
            phase = normalisePhase(value);
            return this;
        }

        /**
         * Sets whether player-facing feedback should be suppressed.
         *
         * @param value whether feedback is suppressed
         * @return this builder
         */
        public @NotNull Builder silent(boolean value) {
            silent = value;
            return this;
        }

        /**
         * Replaces all pipeline variables.
         *
         * @param values variables keyed without the {@code %var.} prefix
         * @return this builder
         */
        public @NotNull Builder variables(@Nullable Map<String, ?> values) {
            variables = copyVariables(values);
            return this;
        }

        /**
         * Adds or replaces one pipeline variable.
         *
         * @param name variable name without the {@code %var.} prefix
         * @param value variable value; {@code null} removes the variable
         * @return this builder
         */
        public @NotNull Builder variable(@Nullable String name, @Nullable Object value) {
            Map<String, String> copy = new LinkedHashMap<>(variables);
            String key = normaliseName(name);
            if (!key.isEmpty()) {
                if (value == null) {
                    copy.remove(key);
                } else {
                    copy.put(key, String.valueOf(value));
                }
            }
            variables = copy.isEmpty() ? Map.of() : Map.copyOf(copy);
            return this;
        }

        /**
         * Replaces all typed context data.
         *
         * @param values typed values
         * @return this builder
         * @throws IllegalArgumentException when a value does not match its key
         */
        public @NotNull Builder data(@Nullable Map<CoreActionKey<?>, Object> values) {
            data = copyData(values);
            return this;
        }

        /**
         * Adds or replaces one typed context value.
         *
         * @param key context key
         * @param value value matching the key's declared type
         * @param <T> value type
         * @return this builder
         * @throws IllegalArgumentException when the key or value is null, or the value is mistyped
         */
        public <T> @NotNull Builder data(@NotNull CoreActionKey<T> key, @NotNull T value) {
            if (key == null || value == null || !key.type().isInstance(value)) {
                throw new IllegalArgumentException("Execution context data does not match its typed key.");
            }
            Map<CoreActionKey<?>, Object> copy = new LinkedHashMap<>(data);
            copy.put(key, value);
            data = Map.copyOf(copy);
            return this;
        }

        /** {@return a new immutable execution context} */
        public @NotNull CoreActionExecutionContext build() {
            return new CoreActionExecutionContext(this);
        }
    }
}
