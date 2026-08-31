/**
 * Public EmakiCoreLib facade and shared API contracts.
 *
 * <p>Use the scheduling view for Bukkit owner-thread access. Unavailable services degrade to explicit
 * failures, empty immutable values, or no-op views. Depend as {@code provided}/{@code compileOnly};
 * do not shade this API.
 */
package emaki.jiuwu.craft.corelib.api;
