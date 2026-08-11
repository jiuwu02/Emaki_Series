/**
 * Immutable payloads returned by the EmakiItem public API.
 *
 * <p>These values are stable snapshots and may be read from any thread. Bukkit {@code ItemStack} values are
 * not made thread-safe by being carried in a result; ownership rules still apply to live inventory stacks.
 * Migration payloads report only files the runtime actually inspected or committed, and refresh summaries
 * preserve compare-conflict counts so callers can distinguish full success from
 * {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult.Partial}.
 */
package emaki.jiuwu.craft.item.api.model;
