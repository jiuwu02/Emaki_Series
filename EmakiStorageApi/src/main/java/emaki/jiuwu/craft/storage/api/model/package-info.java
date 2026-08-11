/**
 * Detached storage snapshots, requested/applied amounts, and capacity views.
 *
 * <h2>Stability</h2>
 * Stable API values. Snapshot entries identify a logical storage slot and carry defensive item templates,
 * not mutable storage-table internals. Runtime transaction status is mapped into {@code EmakiResult} and is
 * deliberately not part of this package.
 *
 * <h2>Threading</h2>
 * Returned snapshots and scalar result values may be read from any thread. A caller placing a template into
 * an inventory must still return to that inventory holder's owner thread.
 *
 * <h2>Degradation</h2>
 * Models are delivered only through storage operations. An unavailable runtime completes those operations
 * with {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult#unavailable()} rather than returning an
 * empty snapshot that could be mistaken for a real empty storage.
 */
package emaki.jiuwu.craft.storage.api.model;
