package emaki.jiuwu.craft.corelib.api.data;

/**
 * One row of shared cross-server state.
 *
 * @param key entry key inside its namespace
 * @param value stored text value; never {@code null}
 * @param counter counter attached to the same key when read through a counter view; {@code 0} otherwise
 */
public record SharedEntry(String key, String value, long counter) {
}
