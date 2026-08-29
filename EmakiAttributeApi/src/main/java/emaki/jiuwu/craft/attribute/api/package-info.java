/**
 * Public API for attribute definitions, resources, damage and third-party contributions.
 *
 * <p>Live Bukkit state requires the relevant owner thread; detached definition snapshots and registration
 * are safe from any thread. Without an installed bridge, queries are empty, result-bearing operations are
 * unavailable, PDC access uses its documented fallback, and registrations return inactive closeable handles.
 */
package emaki.jiuwu.craft.attribute.api;
