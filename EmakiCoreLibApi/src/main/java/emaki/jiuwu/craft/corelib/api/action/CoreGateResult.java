package emaki.jiuwu.craft.corelib.api.action.v2;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * What a gate stage decided.
 *
 * <p>{@link Halted} and {@link Invalid} are separate for the same reason as in
 * {@link CoreSourceResult}: {@code chance 50%} losing its roll is a normal result, while
 * {@code chance abc} is a configuration error.</p>
 */
public sealed interface CoreGateResult {

    /**
     * The gate passed this flow on, possibly filtering it or deriving a new immutable context.
     *
     * @param outbound resulting target flow
     * @param variables pipeline variable updates, used by {@code set}
     * @param data typed context updates, used by gates such as {@code keep}
     */
    record Passed(@NotNull List<CoreActionSubject> outbound,
            @NotNull Map<String, String> variables,
            @NotNull Map<CoreActionKey<?>, Object> data) implements CoreGateResult {

        public Passed {
            outbound = outbound == null ? List.of() : List.copyOf(outbound);
            variables = variables == null ? Map.of() : Map.copyOf(variables);
            data = data == null ? Map.of() : Map.copyOf(data);
            for (Map.Entry<CoreActionKey<?>, Object> entry : data.entrySet()) {
                CoreActionKey<?> key = entry.getKey();
                Object value = entry.getValue();
                if (key == null || value == null || !key.type().isInstance(value)) {
                    throw new IllegalArgumentException("Gate context update does not match its typed key.");
                }
            }
        }

        /** Creates a passing result with no context updates. */
        public Passed(@Nullable List<CoreActionSubject> outbound) {
            this(outbound == null ? List.of() : outbound, Map.of(), Map.of());
        }
    }

    /** The gate stopped the rest of the pipeline. Produced by {@code chance} and {@code stop}. */
    record Halted(@NotNull String reasonKey) implements CoreGateResult {

        public Halted {
            reasonKey = reasonKey == null ? "" : reasonKey;
        }
    }

    /** The gate arguments are wrong. */
    record Invalid(@NotNull String reasonKey, @NotNull Map<String, Object> args) implements CoreGateResult {

        public Invalid {
            reasonKey = reasonKey == null ? "" : reasonKey;
            args = args == null ? Map.of() : Map.copyOf(args);
        }
    }

    /** {@return a passing result carrying {@code outbound}} */
    static @NotNull CoreGateResult passed(@Nullable List<CoreActionSubject> outbound) {
        return new Passed(outbound == null ? List.of() : outbound);
    }

    /**
     * Creates a passing result that also derives immutable context values.
     *
     * @param outbound resulting flow
     * @param variables variable updates
     * @param data typed data updates
     * @return the result
     */
    static @NotNull CoreGateResult passed(@Nullable List<CoreActionSubject> outbound,
            @Nullable Map<String, String> variables,
            @Nullable Map<CoreActionKey<?>, Object> data) {
        return new Passed(outbound == null ? List.of() : outbound,
                variables == null ? Map.of() : variables,
                data == null ? Map.of() : data);
    }

    /** {@return a halt with the given language key} */
    static @NotNull CoreGateResult halted(@Nullable String reasonKey) {
        return new Halted(reasonKey == null ? "" : reasonKey);
    }

    /** {@return an invalid result with the given language key} */
    static @NotNull CoreGateResult invalid(@Nullable String reasonKey) {
        return new Invalid(reasonKey == null ? "" : reasonKey, Map.of());
    }

    /** {@return an invalid result with the given language key and diagnostic arguments} */
    static @NotNull CoreGateResult invalid(@Nullable String reasonKey, @Nullable Map<String, Object> args) {
        return new Invalid(reasonKey == null ? "" : reasonKey, args == null ? Map.of() : args);
    }
}
