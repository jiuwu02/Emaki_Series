package emaki.jiuwu.craft.corelib.api.capability;

import java.util.Locale;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Stable optional-API identifier in {@code namespace:id} form.
 *
 * <p>Build soft-dependency ids with {@link #of(String)} from this API jar, not typed constants from
 * the optional provider jar. Keep optional calls inside the capability guard; eager references can
 * fail class loading with {@link NoClassDefFoundError} or {@link NoSuchMethodError} before the guard.
 */
public record ApiCapability(@NotNull String namespace, @NotNull String id) {

    /**
     * Normalises both segments with {@link Locale#ROOT}.
     *
     * @throws IllegalArgumentException when either segment is blank or contains {@code :}
     */
    public ApiCapability {
        namespace = normalize(namespace, "namespace");
        id = normalize(id, "id");
    }

    /** {@return the canonical {@code namespace:id} form} */
    public @NotNull String key() {
        return namespace + ':' + id;
    }

    /**
     * Parses a canonical {@code namespace:id} identifier.
     *
     * @param key the identifier text
     * @return the parsed capability
     * @throws IllegalArgumentException when {@code key} is blank or is not exactly two non-blank
     *         segments separated by one {@code :}
     */
    public static @NotNull ApiCapability of(@Nullable String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("capability key must not be blank");
        }
        String trimmed = key.trim();
        int separator = trimmed.indexOf(':');
        if (separator < 0 || trimmed.indexOf(':', separator + 1) >= 0) {
            throw new IllegalArgumentException("capability key must be \"namespace:id\": " + key);
        }
        return new ApiCapability(trimmed.substring(0, separator), trimmed.substring(separator + 1));
    }

    @Override
    public String toString() {
        return key();
    }

    private static String normalize(String segment, String label) {
        if (segment == null || segment.isBlank()) {
            throw new IllegalArgumentException("capability " + label + " must not be blank");
        }
        String normalized = segment.trim().toLowerCase(Locale.ROOT);
        if (normalized.indexOf(':') >= 0) {
            throw new IllegalArgumentException("capability " + label + " must not contain ':': " + segment);
        }
        return normalized;
    }
}
