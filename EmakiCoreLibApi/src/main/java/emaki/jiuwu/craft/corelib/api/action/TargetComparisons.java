package emaki.jiuwu.craft.corelib.api.action;

import java.util.Optional;
import java.util.OptionalDouble;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reads a numeric comparison written as {@code op} plus {@code value}.
 *
 * <p>Shared by target conditions so every numeric field answers an unknown operator or an unreadable
 * value the same way: an empty optional, which callers turn into {@link CoreTargetOutcome#UNKNOWN}
 * instead of a permissive default.</p>
 */
public final class TargetComparisons {

    private TargetComparisons() {
    }

    /**
     * Compares two doubles.
     *
     * @param actual   the value read from the target
     * @param operator one of {@code ==}, {@code !=}, {@code <}, {@code <=}, {@code >}, {@code >=}
     * @param expected the configured value
     * @return the comparison result, or {@code null} when the operator is unknown
     */
    public static @Nullable Boolean compare(double actual, @Nullable String operator, double expected) {
        if (operator == null) {
            return null;
        }
        return switch (operator.trim()) {
            case "==" -> actual == expected;
            case "!=" -> actual != expected;
            case "<" -> actual < expected;
            case "<=" -> actual <= expected;
            case ">" -> actual > expected;
            case ">=" -> actual >= expected;
            default -> null;
        };
    }

    /**
     * Reads {@code op} and {@code value} from a condition node and compares them against {@code actual}.
     *
     * @param arguments the node fields
     * @param actual    the value read from the target
     * @return the comparison result, or empty when either field is missing or unreadable
     */
    public static @NotNull Optional<Boolean> evaluate(@NotNull CoreTargetConditionArguments arguments,
            double actual) {
        OptionalDouble expected = arguments.doubleValue("value");
        if (expected.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(compare(actual, arguments.string("op", ""), expected.getAsDouble()));
    }
}