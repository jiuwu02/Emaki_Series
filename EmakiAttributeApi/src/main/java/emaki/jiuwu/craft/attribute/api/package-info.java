/**
 * Public API for EmakiAttribute's definitions, resource state, damage operations, and extension points.
 *
 * <h2>Stability</h2>
 * Stable. {@link emaki.jiuwu.craft.attribute.api.EmakiAttributeApi} is the entry point. Its catalog,
 * operations, extension, and PDC layers are runtime-owned. Public Bukkit events live in
 * {@code api.event}; contribution providers, item-wide contribution gates, and registration contracts live
 * in {@code api.extension}.
 *
 * <h2>Threading</h2>
 * Definition queries may run anywhere. Calls that inspect or change a live player, equipped item, or
 * entity state must run on that Bukkit object's owner thread. Provider and gate registration is
 * thread-safe, while provider and gate callbacks run on EmakiAttribute's equipment collection path.
 *
 * <h2>Degradation</h2>
 * With no installed bridge, {@code status()} reports not installed, catalog queries are empty, mutating
 * calls return {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult#unavailable()}, PDC calls use
 * their documented no-op/result fallback, and registration returns an inactive closeable handle.
 */
package emaki.jiuwu.craft.attribute.api;
