/**
 * Stable public API for EmakiSkills.
 *
 * <p>{@link emaki.jiuwu.craft.skills.api.EmakiSkillsApi} exposes non-null catalog, operations, and
 * extensions layers plus uniform availability metadata. Definition snapshots are any-thread; live player
 * reads and synchronous mutations require the player's owner thread. Cast operations return genuine futures
 * because scripts and integrations may complete later, and EmakiSkills performs all Bukkit cast work on the
 * player's owner thread.
 *
 * <p>Upgrade previews and outcomes are explicit models. A failed success-rate roll is an outcome, not an API
 * failure. Script actions and external skill sources are owner-scoped extension points and are removed when
 * their plugin disables. When the runtime is absent, queries are empty and fallible calls return
 * {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult#unavailable()}.
 *
 * <p>Depend on this artifact with {@code provided} or {@code compileOnly}; do not shade it.
 */
package emaki.jiuwu.craft.skills.api;
