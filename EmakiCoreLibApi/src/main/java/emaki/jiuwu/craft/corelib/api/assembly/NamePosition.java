package emaki.jiuwu.craft.corelib.api.assembly;

import java.util.Locale;

import emaki.jiuwu.craft.corelib.api.text.Texts;

/**
 * Side of the base display name an {@link EmakiNameContribution} is rendered on.
 *
 * <p>The renderer groups contributions by position, sorts each group by the contribution's
 * {@code order}, and appends the prefix group before and the postfix group after the base name.
 * {@link #fromValue(Object)} parses configuration values permissively: {@code null}, blank and
 * unrecognized input all fall back to {@link #POSTFIX}.
 */
public enum NamePosition {
    /** Rendered before the base name. */
    PREFIX,
    /** Rendered after the base name; also the fallback when a position is missing or unknown. */
    POSTFIX;

    /**
     * Parses a configuration value into a position.
     *
     * <p>The value is stringified, trimmed and matched against the constant names using
     * {@link Locale#ROOT} case folding.
     *
     * @param value raw configuration value; {@code null} and blank are accepted
     * @return the named constant, or {@link #POSTFIX} when the value is blank or does not name a
     *         known constant
     */
    public static NamePosition fromValue(Object value) {
        String normalized = Texts.toStringSafe(value).trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return POSTFIX;
        }
        try {
            return NamePosition.valueOf(normalized);
        } catch (IllegalArgumentException _) {
            return POSTFIX;
        }
    }
}
