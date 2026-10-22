package emaki.jiuwu.craft.dungeon.api;

import java.util.Map;

import org.jetbrains.annotations.Nullable;

/**
 * Outcome envelope returned by every facade call instead of exceptions.
 *
 * <p>A result is successful when {@link #isSuccess()} returns {@code true}
 * (the {@code code} is {@code null} or empty); otherwise {@code code} carries
 * a stable error identifier from {@link ErrorCodes} and {@code messageKey}
 * names the language key used to render the failure to players.
 *
 * @param code the error code, or {@code null} or empty on success
 * @param messageKey the language key describing the outcome; empty on success
 * @param args replacement values passed to the message; never {@code null}
 * @param value the success payload, or {@code null} when the call failed
 */
public record Result<T>(String code, String messageKey, Map<String, Object> args, T value) {

    /**
     * {@return a successful result carrying {@code value}}
     *
     * @param value the payload to report, may be {@code null}
     */
    public static <T> Result<T> ok(T value) {
        return new Result<>(null, "", Map.of(), value);
    }

    /**
     * {@return a failed result without a payload}
     *
     * @param code a stable error identifier, typically from {@link ErrorCodes}
     * @param messageKey the language key describing the failure
     */
    public static <T> Result<T> err(String code, String messageKey) {
        return new Result<>(code, messageKey, Map.of(), null);
    }

    /**
     * {@return a failed result whose message takes replacement arguments}
     *
     * @param code a stable error identifier, typically from {@link ErrorCodes}
     * @param messageKey the language key describing the failure
     * @param args replacement values for the message; a {@code null} map is
     *         treated as empty
     */
    public static <T> Result<T> err(String code, String messageKey, Map<String, Object> args) {
        return new Result<>(code, messageKey, args == null ? Map.of() : Map.copyOf(args), null);
    }

    /**
     * {@return whether this result reports success}, which holds when
     * {@code code} is {@code null} or empty
     */
    public boolean isSuccess() {
        return code == null || code.isEmpty();
    }

    /**
     * {@return the success payload}, or {@code null} when the call failed or
     * carried no value
     */
    public @Nullable T value() {
        return value;
    }
}
