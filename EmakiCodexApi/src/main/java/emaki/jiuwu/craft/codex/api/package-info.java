/**
 * Stable public API for EmakiCodex's real Minecraft advancement tree.
 *
 * <p>{@link emaki.jiuwu.craft.codex.api.EmakiCodexApi} exposes catalog, operations, and extensions layers.
 * Advancement/page definitions are any-thread snapshots. Live completion reads and grant/revoke mutations
 * require the target player's owner thread. Mutations return {@code EmakiResult<Unit>}; disabled subsystems
 * are reported as {@code REJECTED} with a stable reason key, while a missing runtime is {@code UNAVAILABLE}.
 *
 * <p>{@link emaki.jiuwu.craft.codex.api.AdvancementSpec} is deliberately narrower than the runtime YAML
 * definition. External advancements and trigger providers are owner-scoped, closeable, and automatically
 * removed when their owner disables. Advancement registration and handle close must run on the global thread.
 *
 * <p>Depend on this artifact with {@code provided} or {@code compileOnly}; do not shade it.
 */
package emaki.jiuwu.craft.codex.api;
