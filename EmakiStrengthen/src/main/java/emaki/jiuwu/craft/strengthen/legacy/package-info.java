/**
 * Legacy {@code match} block conversion for {@code recipes/*.yml}.
 *
 * <p>This package exists only to migrate configurations written before the
 * {@code match} block was replaced by the unified CoreLib {@code matcher} plus
 * the three promoted top-level fields ({@code slot_groups}, {@code stats_any},
 * {@code source_patterns}). It is a one-off migration aid, not part of the
 * plugin's behaviour.
 *
 * <h2>Cleanup contract</h2>
 *
 * <p>The following invariants are what make this package safely deletable once
 * every server has migrated:
 *
 * <ul>
 *   <li>It is invoked <strong>only from the command entry point</strong>
 *       ({@code /emakistrengthen convert-legacy}); nothing on a runtime
 *       matching, resolving or loading path calls into it.</li>
 *   <li>Deleting the whole package leaves the rest of the plugin compiling,
 *       except for the single {@code case "convert-legacy" -> ...} line in
 *       {@code StrengthenCommandRouter} and the {@code "convert-legacy"}
 *       entries in its tab completion and help lists.</li>
 *   <li>Also remove the {@code command.convert_legacy.*} and
 *       {@code command.help.desc.convert_legacy} keys from both language
 *       files.</li>
 *   <li>The converter reads no runtime state, writes no persistent data
 *       container entry, and touches no player data. It only reads and rewrites
 *       configuration files inside the plugin data folder.</li>
 * </ul>
 *
 * <p>This module has no {@code item_sources} decision sites to migrate: its
 * material tokens are construction and catalogue keys, and star-stage rules
 * already decide through {@code targetCompare} plus {@code matcher}. Only the
 * {@code match} block needs conversion here.
 *
 * <p>{@link StrengthenLegacyEntry} depends on the shared engine in
 * {@code emaki.jiuwu.craft.corelib.legacy}, which is used by every module's
 * converter and must therefore be retired in the same batch as this package,
 * not before.
 *
 * <p>Deliberate exception to the module-wide no-comment rule: this package-level
 * Javadoc records the cleanup contract, because the contract is the reason the
 * package may be deleted without further investigation.
 */
package emaki.jiuwu.craft.strengthen.legacy;
