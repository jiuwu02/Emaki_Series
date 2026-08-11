package emaki.jiuwu.craft.station.api.model;

import java.util.Locale;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Where a submission takes its materials from, and where a refund goes back to.
 *
 * <p>The two channels are not interchangeable: {@link #BACKPACK} is bounded by vanilla stack sizes
 * and needs the player online and holding the items in the station's input slots, while
 * {@link #STORAGE} reads {@code long} amounts straight out of EmakiStorage and never routes items
 * through the inventory. A refund always returns to the channel the materials were taken from, which
 * is why every consumed-material record carries its own channel rather than inheriting one.
 */
public enum MaterialChannel {

    /** Materials the player physically placed in the station's input slots. */
    BACKPACK,

    /** Materials debited directly from the player's EmakiStorage warehouse. */
    STORAGE;

    /** {@return the lower-case configuration token for this channel} */
    public @NotNull String token() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Parses a configuration token, falling back when the value is absent or unknown.
     *
     * @param raw      the configured token; {@code null} and blank are treated as absent
     * @param fallback the value to use when {@code raw} does not name a channel
     * @return the parsed channel, or {@code fallback}
     */
    public static @NotNull MaterialChannel parse(@Nullable String raw, @NotNull MaterialChannel fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (MaterialChannel channel : values()) {
            if (channel.token().equals(normalized)) {
                return channel;
            }
        }
        return fallback;
    }
}
