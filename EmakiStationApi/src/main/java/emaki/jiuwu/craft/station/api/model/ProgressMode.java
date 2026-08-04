package emaki.jiuwu.craft.station.api.model;

import java.util.Locale;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * How a queue advances its head entry over time.
 *
 * <p>{@link #OFFLINE} counts wall-clock time and keeps running while the owner is away;
 * {@link #ONLINE} only accumulates while the owner is connected. Both modes share one persisted
 * shape so a station can switch between them without migrating data.
 *
 * <p><strong>Offline does not mean unattended delivery.</strong> EmakiStorage refuses every mutating
 * call for an offline player, so an entry that reaches its duration while the owner is away is merely
 * <em>due</em>; the actual settlement happens on their next join or when they open the station GUI.
 * Player-facing text must say this plainly, otherwise "offline progress" reads as "offline delivery".
 */
public enum ProgressMode {

    /** Wall-clock progress that continues while the owner is offline. */
    OFFLINE,

    /** Progress that only accumulates while the owner is online. */
    ONLINE;

    /** {@return the lower-case configuration token for this mode} */
    public @NotNull String token() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Parses a configuration token, falling back when the value is absent or unknown.
     *
     * @param raw      the configured token; {@code null} and blank are treated as absent
     * @param fallback the value to use when {@code raw} does not name a mode
     * @return the parsed mode, or {@code fallback}
     */
    public static @NotNull ProgressMode parse(@Nullable String raw, @NotNull ProgressMode fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (ProgressMode mode : values()) {
            if (mode.token().equals(normalized)) {
                return mode;
            }
        }
        return fallback;
    }
}
