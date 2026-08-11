package emaki.jiuwu.craft.corelib.api.action;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Result of one stage or one whole pipeline.
 *
 * <p>Aligned with the project-wide {@code EmakiResult} semantics (Success / Partial / Failure) and
 * adds {@link Skipped}, which the v1 {@code CoreActionResult} could not express: "no enemy in range"
 * is neither a success nor a configuration error.</p>
 */
public sealed interface CoreActionOutcome {

    /** The stage did what it was asked to do. */
    record Success(@NotNull Map<String, Object> data) implements CoreActionOutcome {

        public Success {
            data = data == null ? Map.of() : Map.copyOf(data);
        }
    }

    /** The stage had nothing to do. Not an error. */
    record Skipped(@NotNull String reasonKey) implements CoreActionOutcome {

        public Skipped {
            reasonKey = reasonKey == null ? "" : reasonKey;
        }
    }

    /** Some target iterations succeeded and some did not. */
    record Partial(@NotNull List<CoreActionOutcome> parts) implements CoreActionOutcome {

        public Partial {
            parts = parts == null ? List.of() : List.copyOf(parts);
        }
    }

    /** The stage could not run correctly. */
    record Failure(@NotNull CoreActionFailureKind kind,
            @NotNull String reasonKey,
            @NotNull Map<String, Object> args) implements CoreActionOutcome {

        public Failure {
            kind = kind == null ? CoreActionFailureKind.INTERNAL_ERROR : kind;
            reasonKey = reasonKey == null ? "" : reasonKey;
            args = args == null ? Map.of() : Map.copyOf(args);
        }
    }

    /** {@return a success carrying no data} */
    static @NotNull CoreActionOutcome success() {
        return new Success(Map.of());
    }

    /** {@return a success carrying {@code data}} */
    static @NotNull CoreActionOutcome success(@Nullable Map<String, Object> data) {
        return new Success(data == null ? Map.of() : data);
    }

    /** {@return a skip with the given language key} */
    static @NotNull CoreActionOutcome skipped(@Nullable String reasonKey) {
        return new Skipped(reasonKey == null ? "" : reasonKey);
    }

    /** {@return a failure with the given kind and language key} */
    static @NotNull CoreActionOutcome failure(@Nullable CoreActionFailureKind kind, @Nullable String reasonKey) {
        return new Failure(kind == null ? CoreActionFailureKind.INTERNAL_ERROR : kind,
                reasonKey == null ? "" : reasonKey, Map.of());
    }

    /** {@return a failure with the given kind, language key and diagnostic arguments} */
    static @NotNull CoreActionOutcome failure(@Nullable CoreActionFailureKind kind,
            @Nullable String reasonKey,
            @Nullable Map<String, Object> args) {
        return new Failure(kind == null ? CoreActionFailureKind.INTERNAL_ERROR : kind,
                reasonKey == null ? "" : reasonKey, args == null ? Map.of() : args);
    }

    /** {@return whether this outcome represents at least partial success} */
    default boolean successful() {
        return switch (this) {
            case Success ignored -> true;
            case Partial partial -> partial.parts().stream().anyMatch(CoreActionOutcome::successful);
            case Skipped ignored -> false;
            case Failure ignored -> false;
        };
    }

    /** {@return whether this outcome is a hard failure} */
    default boolean failed() {
        return this instanceof Failure;
    }
}
