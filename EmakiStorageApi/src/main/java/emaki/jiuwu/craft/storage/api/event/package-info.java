/**
 * Bukkit events emitted for EmakiStorage deposits, withdrawals, and slot unlocks.
 *
 * <h2>Stability</h2>
 * Stable event contracts. They are published by EmakiStorage and are not third-party implementation
 * interfaces.
 *
 * <h2>Threading</h2>
 * Events fire synchronously on the owning player's entity thread immediately around the storage
 * transaction. Listeners may inspect the supplied defensive item copies there and may cancel pre-events.
 *
 * <h2>Degradation</h2>
 * When the EmakiStorage bridge is absent, no storage event is emitted; facade operations complete with
 * {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult#unavailable()} instead of simulating a
 * transaction.
 */
package emaki.jiuwu.craft.storage.api.event;
