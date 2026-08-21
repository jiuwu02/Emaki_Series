/**
 * Legacy {@code match} block conversion for {@code items/*.yml}.
 *
 * <p>This package exists only to migrate configurations written before the
 * {@code match} block was replaced by the unified CoreLib {@code matcher} plus
 * the promoted top-level {@code slot_groups} field. It is a one-off migration
 * aid, not part of the plugin's behaviour.
 *
 * <h2>Cleanup contract</h2>
 *
 * <p>The following invariants are what make this package safely deletable once
 * every server has migrated:
 *
 * <ul>
 *   <li>It is invoked <strong>only from the command entry point</strong>
 *       ({@code /emakigem convert-legacy}); nothing on a runtime matching,
 *       loading or inlay path calls into it.</li>
 *   <li>Deleting the whole package leaves the rest of the plugin compiling,
 *       except for the command branch that dispatches to it.</li>
 *   <li>The converter reads no runtime state, writes no persistent data
 *       container entry, and touches no player data. It only reads and rewrites
 *       configuration files inside the plugin data folder.</li>
 * </ul>
 *
 * <p>Deliberate exception to the module-wide no-comment rule: this package-level
 * Javadoc records the cleanup contract, because the contract is the reason the
 * package may be deleted without further investigation.
 */
package emaki.jiuwu.craft.gem.legacy;
