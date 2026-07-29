/**
 * Bukkit events fired by EmakiForge.
 *
 * <h2>Stability</h2>
 * Stable. Both events are final classes with fixed constructors; EmakiForge is their only publisher.
 *
 * <h2>Threading</h2>
 * Both are synchronous events fired on the thread that owns the forging player. On Folia that is the
 * player's region thread, so listeners may touch the player and nearby blocks directly.
 *
 * <h2>Coverage</h2>
 * Only the GUI forging path fires these events. Neither is an exhaustive audit trail:
 * {@link emaki.jiuwu.craft.forge.api.event.ForgeStartEvent} is skipped when the owner thread is
 * unavailable or the runtime generation has changed, and
 * {@link emaki.jiuwu.craft.forge.api.event.ForgeCompletedEvent} is additionally skipped for stale
 * sessions, rejected completion tasks during shutdown, and unexpected execution errors.
 *
 * <h2>Do not shade</h2>
 * Bukkit registers listeners by {@code Class} identity. If you shade
 * {@code emaki-forge-api} into your own jar, your listener classes will reference a different
 * {@code Class} object than the one EmakiForge fires, and your handlers will never run — with no
 * error logged. Always depend on this artifact with {@code provided} or {@code compileOnly}.
 */
package emaki.jiuwu.craft.forge.api.event;
