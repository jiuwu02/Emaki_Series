/**
 * Stable Bukkit event contracts emitted by EmakiGem.
 *
 * <h2>Pairing</h2>
 * {@link emaki.jiuwu.craft.gem.api.event.GemInlayEvent} and
 * {@link emaki.jiuwu.craft.gem.api.event.GemExtractEvent} carry an operation id shared with their
 * completed event. {@link emaki.jiuwu.craft.gem.api.event.GemSocketOpenEvent} also carries a unique
 * invocation id. Pre-events are cancellable; inlay listeners may replace the success chance.
 *
 * <h2>Completion</h2>
 * Completed events cover the public API, GUI, held-item action, and direct extraction service paths.
 * They do not fire merely after a pending commit starts. They fire after configured success actions and
 * persistent journal completion. Inlay completion may report a terminal rolled failure and its input
 * consumption result.
 *
 * <h2>Threading</h2>
 * Events are synchronous and are fired on the affected player's entity-owner thread. If an asynchronous
 * completion finishes after the player has gone offline, the runtime logs and skips the event rather than
 * violating Bukkit/Folia thread ownership.
 *
 * <h2>Availability</h2>
 * No synthetic event is emitted while EmakiGem is unavailable; facade calls return
 * {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult#unavailable()}.
 */
package emaki.jiuwu.craft.gem.api.event;
