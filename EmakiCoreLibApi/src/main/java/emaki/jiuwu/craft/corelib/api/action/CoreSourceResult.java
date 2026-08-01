package emaki.jiuwu.craft.corelib.api.action;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * What a source stage produced.
 *
 * <p>{@link Empty} and {@link Invalid} are deliberately separate: finding no target is a normal
 * gameplay state, while a bad source argument is a configuration error. The v1
 * {@code KillEntityAction} reported both as skipped, so server owners could not see their own typos.</p>
 */
public sealed interface CoreSourceResult {

    /** The source selected these subjects, in a stable order. */
    record Selected(@NotNull List<CoreActionSubject> subjects) implements CoreSourceResult {

        public Selected {
            subjects = subjects == null ? List.of() : List.copyOf(subjects);
        }
    }

    /** The source ran correctly and found nothing. Leads to {@code Skipped}. */
    record Empty(@NotNull String reasonKey) implements CoreSourceResult {

        public Empty {
            reasonKey = reasonKey == null ? "" : reasonKey;
        }
    }

    /** The source arguments are wrong. Leads to {@code Failure}. */
    record Invalid(@NotNull String reasonKey, @NotNull Map<String, Object> args) implements CoreSourceResult {

        public Invalid {
            reasonKey = reasonKey == null ? "" : reasonKey;
            args = args == null ? Map.of() : Map.copyOf(args);
        }
    }

    /** {@return a result carrying {@code subjects}} */
    static @NotNull CoreSourceResult selected(@Nullable List<CoreActionSubject> subjects) {
        return new Selected(subjects == null ? List.of() : subjects);
    }

    /** {@return a result carrying one subject} */
    static @NotNull CoreSourceResult one(@Nullable CoreActionSubject subject) {
        return subject == null ? new Empty("") : new Selected(List.of(subject));
    }

    /** {@return an empty result with the given language key} */
    static @NotNull CoreSourceResult empty(@Nullable String reasonKey) {
        return new Empty(reasonKey == null ? "" : reasonKey);
    }

    /** {@return an invalid result with the given language key} */
    static @NotNull CoreSourceResult invalid(@Nullable String reasonKey) {
        return new Invalid(reasonKey == null ? "" : reasonKey, Map.of());
    }

    /** {@return an invalid result with the given language key and diagnostic arguments} */
    static @NotNull CoreSourceResult invalid(@Nullable String reasonKey, @Nullable Map<String, Object> args) {
        return new Invalid(reasonKey == null ? "" : reasonKey, args == null ? Map.of() : args);
    }
}
