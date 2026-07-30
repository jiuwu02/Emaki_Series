/**
 * Public API for EmakiStorage snapshots, owner-dispatched mutations, and storage GUI access.
 *
 * <h2>Stability</h2>
 * Stable. {@link emaki.jiuwu.craft.storage.api.EmakiStorageApi} is the entry point and its operation layer
 * is supplied only by the EmakiStorage runtime.
 *
 * <h2>Threading</h2>
 * Future-returning operations may be submitted from any thread, but their callbacks are not guaranteed to
 * run on a Bukkit owner thread. {@code openGui} must run on the viewer's owner thread and never schedules a
 * delayed open on the caller's behalf.
 *
 * <h2>Degradation</h2>
 * With no storage bridge, {@code status()} reports not installed and every asynchronous operation completes
 * with {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult#unavailable()}; synchronous GUI access
 * returns the same explicit failure.
 */
package emaki.jiuwu.craft.storage.api;
