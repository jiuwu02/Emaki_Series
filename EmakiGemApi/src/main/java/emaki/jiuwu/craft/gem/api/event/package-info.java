/**
 * Bukkit events fired by EmakiGem.
 *
 * <h2>Pre and post pairs</h2>
 * Three cancellable pre-events fire before an operation commits:
 * {@link emaki.jiuwu.craft.gem.api.event.GemInlayEvent},
 * {@link emaki.jiuwu.craft.gem.api.event.GemExtractEvent}, and
 * {@link emaki.jiuwu.craft.gem.api.event.GemSocketOpenEvent}. Two informational post-events fire after
 * a committed transaction: {@link emaki.jiuwu.craft.gem.api.event.GemInlayCompletedEvent} and
 * {@link emaki.jiuwu.craft.gem.api.event.GemExtractCompletedEvent}.
 *
 * <h2>Threading</h2>
 * All five are synchronous and fire on the thread that owns the acting player. On Folia that is the
 * player's region thread, so listeners may touch the player and their inventory directly.
 *
 * <h2>Coverage — read this before building an audit trail</h2>
 * EmakiGem guards every pre-event fire point with an ownership check and <em>silently skips the
 * event</em> when the calling thread does not own the actor: the operation still proceeds. The public
 * API refuses off-thread calls for exactly this reason, but EmakiGem's own GUI, command, and held-item
 * action paths are not similarly constrained.
 *
 * <p>The two post-events are fired only by
 * {@link emaki.jiuwu.craft.gem.api.GemOperations}; EmakiGem's GUI and held-item paths do not fire them.
 * Treat them as "this API performed an operation", not "an operation happened somewhere".
 *
 * <h2>Do not shade</h2>
 * Bukkit matches listeners by {@code Class} identity. If you shade {@code emaki-gem-api} into your jar,
 * your listener will reference a different {@code Class} object than the one EmakiGem fires, and your
 * handlers will never run — silently, with nothing logged. Always depend on this artifact with
 * {@code provided} or {@code compileOnly}.
 */
package emaki.jiuwu.craft.gem.api.event;
