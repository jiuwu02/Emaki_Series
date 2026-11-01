package emaki.jiuwu.craft.corelib.api.assembly;

import java.util.Locale;

import emaki.jiuwu.craft.corelib.api.text.Texts;

/**
 * Policy deciding which text becomes an item's base display name before name contributions are
 * layered onto it.
 *
 * <p>When several {@link EmakiStructuredPresentation} layers contribute to one item, an explicit
 * template wins over a translatable source name, which in turn wins over the item's effective
 * name. {@link #fromValue(Object)} parses configuration values permissively: {@code null}, blank
 * and unrecognized input all fall back to {@link #SOURCE_EFFECTIVE_NAME}.
 */
public enum BaseNamePolicy {
    /** Base name comes from the item's effective display name, ignoring presentation templates. */
    SOURCE_EFFECTIVE_NAME,
    /** Base name is the item's vanilla name as a translatable component, so the client localizes it. */
    SOURCE_TRANSLATABLE,
    /** Base name is rendered from {@link EmakiStructuredPresentation#baseNameTemplate()}. */
    EXPLICIT_TEMPLATE;

    /**
     * Parses a configuration value into a policy.
     *
     * <p>The value is stringified, trimmed and matched against the constant names using
     * {@link Locale#ROOT} case folding.
     *
     * @param value raw configuration value; {@code null} and blank are accepted
     * @return the named constant, or {@link #SOURCE_EFFECTIVE_NAME} when the value is blank or
     *         does not name a known constant
     */
    public static BaseNamePolicy fromValue(Object value) {
        String normalized = Texts.toStringSafe(value).trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return SOURCE_EFFECTIVE_NAME;
        }
        try {
            return BaseNamePolicy.valueOf(normalized);
        } catch (IllegalArgumentException _) {
            return SOURCE_EFFECTIVE_NAME;
        }
    }
}
