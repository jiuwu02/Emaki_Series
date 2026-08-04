package emaki.jiuwu.craft.station.api.model;

import java.util.Locale;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Where finished outputs are delivered, in preference order.
 *
 * <p>The {@code _FIRST} values fall back to the other target when the preferred one cannot take the
 * items; the {@code _ONLY} values never do. When no configured target can take the outputs the entry
 * becomes {@link QueueEntryState#PENDING_CLAIM} rather than dropping or destroying anything.
 */
public enum OutputRouting {

    /** Try the warehouse first, then the inventory. */
    STORAGE_FIRST,

    /** Try the inventory first, then the warehouse. */
    BACKPACK_FIRST,

    /** Only ever deliver to the warehouse. */
    STORAGE_ONLY,

    /** Only ever deliver to the inventory. */
    BACKPACK_ONLY;

    /** {@return the lower-case configuration token for this routing} */
    public @NotNull String token() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** {@return whether the warehouse may receive outputs under this routing} */
    public boolean allowsStorage() {
        return this != BACKPACK_ONLY;
    }

    /** {@return whether the inventory may receive outputs under this routing} */
    public boolean allowsBackpack() {
        return this != STORAGE_ONLY;
    }

    /** {@return whether the warehouse is the preferred target under this routing} */
    public boolean prefersStorage() {
        return this == STORAGE_FIRST || this == STORAGE_ONLY;
    }

    /**
     * Parses a configuration token, falling back when the value is absent or unknown.
     *
     * @param raw      the configured token; {@code null} and blank are treated as absent
     * @param fallback the value to use when {@code raw} does not name a routing
     * @return the parsed routing, or {@code fallback}
     */
    public static @NotNull OutputRouting parse(@Nullable String raw, @NotNull OutputRouting fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (OutputRouting routing : values()) {
            if (routing.token().equals(normalized)) {
                return routing;
            }
        }
        return fallback;
    }
}
