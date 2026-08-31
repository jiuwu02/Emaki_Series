package emaki.jiuwu.craft.corelib.api.contract;

import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Result carrier for fallible Emaki APIs.
 *
 * <p>{@link FailureKind#UNAVAILABLE} is never encoded as a business-shaped value. {@link Success}
 * and {@link Partial} carry non-null payloads; {@link Failure} carries a stable kind, reason key, and
 * an immutable defensive copy of rendering placeholders.
 *
 * @param <T> payload type
 */
public sealed interface EmakiResult<T> {

    /**
     * The operation completed fully.
     *
     * @param value the resulting payload, never {@code null}
     * @param <T>   the payload type
     */
    record Success<T>(@NotNull T value) implements EmakiResult<T> {

        /**
         * Creates a successful result.
         *
         * @param value the resulting payload
         * @throws NullPointerException when {@code value} is {@code null}
         */
        public Success {
            if (value == null) {
                throw new NullPointerException("value");
            }
        }
    }

    /**
     * Partial completion. {@code value} is what was achieved; {@code reasonKey} explains the shortfall.
     */
    record Partial<T>(@NotNull T value, @NotNull String reasonKey) implements EmakiResult<T> {

        /**
         * Creates a partial result.
         *
         * @param value     the achieved payload
         * @param reasonKey stable machine-readable key describing the shortfall
         * @throws NullPointerException when any argument is {@code null}
         */
        public Partial {
            if (value == null) {
                throw new NullPointerException("value");
            }
            if (reasonKey == null) {
                throw new NullPointerException("reasonKey");
            }
        }
    }

    /**
     * The operation did not happen.
     *
     * @param kind         stable classification of the failure
     * @param reasonKey    stable machine-readable key identifying the concrete situation; it is not
     *                     player-facing text
     * @param placeholders immutable substitution data for callers that want to render their own
     *                     message; empty when the situation carries no context
     * @param <T>          the payload type that would have been produced
     */
    record Failure<T>(@NotNull FailureKind kind,
                      @NotNull String reasonKey,
                      @NotNull Map<String, Object> placeholders) implements EmakiResult<T> {

        /**
         * Creates a failure result with a defensively copied, immutable placeholder map.
         *
         * @param kind         stable classification of the failure
         * @param reasonKey    stable machine-readable key identifying the concrete situation
         * @param placeholders substitution data; copied defensively
         * @throws NullPointerException when {@code kind} or {@code reasonKey} is {@code null}
         */
        public Failure {
            if (kind == null) {
                throw new NullPointerException("kind");
            }
            if (reasonKey == null) {
                throw new NullPointerException("reasonKey");
            }
            placeholders = placeholders == null ? Map.of() : Map.copyOf(placeholders);
        }
    }

    /**
     * Creates a fully successful result.
     *
     * @param value the payload
     * @param <T>   the payload type
     * @return a {@link Success}
     */
    static <T> @NotNull EmakiResult<T> success(@NotNull T value) {
        return new Success<>(value);
    }

    /**
     * Creates a successful result for operations that carry no payload.
     *
     * @return a {@link Success} carrying {@link Unit#INSTANCE}
     */
    static @NotNull EmakiResult<Unit> ok() {
        return new Success<>(Unit.INSTANCE);
    }

    /**
     * Creates a partially completed result.
     *
     * @param value     the achieved payload
     * @param reasonKey stable machine-readable key describing the shortfall
     * @param <T>       the payload type
     * @return a {@link Partial}
     */
    static <T> @NotNull EmakiResult<T> partial(@NotNull T value, @NotNull String reasonKey) {
        return new Partial<>(value, reasonKey);
    }

    /**
     * Creates a failed result without placeholders.
     *
     * @param kind      stable classification of the failure
     * @param reasonKey stable machine-readable key identifying the concrete situation
     * @param <T>       the payload type that would have been produced
     * @return a {@link Failure}
     */
    static <T> @NotNull EmakiResult<T> failure(@NotNull FailureKind kind, @NotNull String reasonKey) {
        return new Failure<>(kind, reasonKey, Map.of());
    }

    /**
     * Creates a failed result with placeholders.
     *
     * @param kind         stable classification of the failure
     * @param reasonKey    stable machine-readable key identifying the concrete situation
     * @param placeholders substitution data; copied defensively
     * @param <T>          the payload type that would have been produced
     * @return a {@link Failure}
     */
    static <T> @NotNull EmakiResult<T> failure(@NotNull FailureKind kind,
                                               @NotNull String reasonKey,
                                               @NotNull Map<String, Object> placeholders) {
        return new Failure<>(kind, reasonKey, placeholders);
    }

    /**
     * Creates the canonical result used by every facade when its bridge is absent.
     *
     * @param <T> the payload type that would have been produced
     * @return a {@link Failure} of kind {@link FailureKind#UNAVAILABLE}
     */
    static <T> @NotNull EmakiResult<T> unavailable() {
        return new Failure<>(FailureKind.UNAVAILABLE, "emaki.api.unavailable", Map.of());
    }

    /**
     * Creates a failure describing a missing identifier.
     *
     * @param reasonKey stable machine-readable key identifying what was missing
     * @param <T>       the payload type that would have been produced
     * @return a {@link Failure} of kind {@link FailureKind#NOT_FOUND}
     */
    static <T> @NotNull EmakiResult<T> notFound(@NotNull String reasonKey) {
        return new Failure<>(FailureKind.NOT_FOUND, reasonKey, Map.of());
    }

    /**
     * Creates a failure describing an illegal argument.
     *
     * @param reasonKey stable machine-readable key identifying the offending argument
     * @param <T>       the payload type that would have been produced
     * @return a {@link Failure} of kind {@link FailureKind#INVALID_INPUT}
     */
    static <T> @NotNull EmakiResult<T> invalidInput(@NotNull String reasonKey) {
        return new Failure<>(FailureKind.INVALID_INPUT, reasonKey, Map.of());
    }

    /**
     * Creates a failure describing a business-rule rejection.
     *
     * @param reasonKey stable machine-readable key identifying the unmet rule
     * @param <T>       the payload type that would have been produced
     * @return a {@link Failure} of kind {@link FailureKind#REJECTED}
     */
    static <T> @NotNull EmakiResult<T> rejected(@NotNull String reasonKey) {
        return new Failure<>(FailureKind.REJECTED, reasonKey, Map.of());
    }

    /**
     * Creates a failure describing an offline target player.
     *
     * @param <T> the payload type that would have been produced
     * @return a {@link Failure} of kind {@link FailureKind#TARGET_OFFLINE}
     */
    static <T> @NotNull EmakiResult<T> targetOffline() {
        return new Failure<>(FailureKind.TARGET_OFFLINE, "emaki.api.target_offline", Map.of());
    }

    /**
     * Creates a failure describing a thread-ownership violation.
     *
     * @param <T> the payload type that would have been produced
     * @return a {@link Failure} of kind {@link FailureKind#WRONG_THREAD}
     */
    static <T> @NotNull EmakiResult<T> wrongThread() {
        return new Failure<>(FailureKind.WRONG_THREAD, "emaki.api.wrong_thread", Map.of());
    }

    /**
     * Creates a failure describing an unexpected implementation exception.
     *
     * @param reasonKey stable machine-readable key identifying the failing operation
     * @param <T>       the payload type that would have been produced
     * @return a {@link Failure} of kind {@link FailureKind#INTERNAL_ERROR}
     */
    static <T> @NotNull EmakiResult<T> internalError(@NotNull String reasonKey) {
        return new Failure<>(FailureKind.INTERNAL_ERROR, reasonKey, Map.of());
    }

    /** {@return whether this result is a fully successful one} */
    default boolean isSuccess() {
        return this instanceof Success<T>;
    }

    /** {@return whether this result is a partially completed one} */
    default boolean isPartial() {
        return this instanceof Partial<T>;
    }

    /** {@return whether this result carries no payload because the operation did not happen} */
    default boolean isFailure() {
        return this instanceof Failure<T>;
    }

    /** {@return whether a payload is present, which is true for both success and partial results} */
    default boolean hasValue() {
        return !(this instanceof Failure<T>);
    }

    /**
     * {@return the payload when present, otherwise an empty optional}
     *
     * <p>Named {@code optionalValue} rather than {@code value} because {@link Success} and
     * {@link Partial} already expose a record accessor called {@code value()} that returns the
     * payload directly.
     */
    default @NotNull Optional<T> optionalValue() {
        return switch (this) {
            case Success<T> success -> Optional.of(success.value());
            case Partial<T> partial -> Optional.of(partial.value());
            case Failure<T> ignored -> Optional.empty();
        };
    }

    /**
     * Returns the payload, or {@code fallback} when this result carries none.
     *
     * @param fallback the value to use for failures; may be {@code null}
     * @return the payload or {@code fallback}
     */
    default T orElse(T fallback) {
        return switch (this) {
            case Success<T> success -> success.value();
            case Partial<T> partial -> partial.value();
            case Failure<T> ignored -> fallback;
        };
    }

    /** {@return the failure classification, or {@code null} when a payload is present} */
    default @Nullable FailureKind failureKind() {
        return this instanceof Failure<T> failure ? failure.kind() : null;
    }

    /**
     * {@return the stable machine-readable reason key; an empty string for {@link Success}, the
     * shortfall key for {@link Partial}, and the failure key for {@link Failure}}
     */
    default @NotNull String reasonKey() {
        return switch (this) {
            case Success<T> ignored -> "";
            case Partial<T> partial -> partial.reasonKey();
            case Failure<T> failure -> failure.reasonKey();
        };
    }

    /**
     * {@return the placeholder map, which is empty unless this result is a {@link Failure} carrying
     * substitution data}
     *
     * <p>Named {@code reasonPlaceholders} rather than {@code placeholders} because {@link Failure}
     * already exposes a record accessor called {@code placeholders()}.
     */
    default @NotNull Map<String, Object> reasonPlaceholders() {
        return this instanceof Failure<T> failure ? failure.placeholders() : Map.of();
    }

    /**
     * Re-types a failure so it can be returned from a method with a different payload type. Calling
     * this on a result that carries a payload is a programming error.
     *
     * @param <R> the target payload type
     * @return the same failure, re-typed
     * @throws IllegalStateException when this result carries a payload
     */
    @SuppressWarnings("unchecked")
    default <R> @NotNull EmakiResult<R> retypeFailure() {
        if (this instanceof Failure<T> failure) {
            return (EmakiResult<R>) new Failure<R>(failure.kind(), failure.reasonKey(), failure.placeholders());
        }
        throw new IllegalStateException("retypeFailure() called on a result that carries a value");
    }
}
