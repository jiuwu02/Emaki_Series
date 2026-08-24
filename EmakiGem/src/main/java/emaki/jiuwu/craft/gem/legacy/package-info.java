/**
 * Two one-off migrations: the legacy {@code match} block in
 * {@code items/*.yml}, and legacy {@code item_sources} decision sites in
 * {@code config.yml} socket openers and {@code gems/*.yml}.
 *
 * <p>This package exists only to migrate configurations written before the
 * {@code match} block was replaced by the unified CoreLib {@code matcher} plus
 * the promoted top-level {@code slot_groups} field, and before gem and opener
 * recognition was converged onto {@code matcher}. It is a one-off migration
 * aid, not part of the plugin's behaviour.
 *
 * <h2>Cleanup contract</h2>
 *
 * <p>The following invariants are what make this package safely deletable once
 * every server has migrated:
 *
 * <ul>
 *   <li>It is invoked <strong>only from the command entry point</strong>
 *       ({@code /emakigem convert-legacy}) and from the load-time scanner
 *       warning; nothing on a runtime matching, loading or inlay path calls
 *       into it.</li>
 *   <li>Deleting the whole package leaves the rest of the plugin compiling,
 *       except for the single {@code case "convert-legacy" -> ...} line in
 *       {@code GemCommandRouter}, the {@code "convert-legacy"} entries in its
 *       tab completion and help lists, and the
 *       {@code reportLegacyItemSources()} call in {@code EmakiGemPlugin}.</li>
 *   <li>Also remove the {@code command.convert_legacy.*} and
 *       {@code command.help.desc.convert_legacy} keys from both language
 *       files.</li>
 *   <li>The converter reads no runtime state, writes no persistent data
 *       container entry, and touches no player data. It only reads and rewrites
 *       configuration files inside the plugin data folder.</li>
 * </ul>
 *
 * <p>The {@code gems/*.yml} target deliberately <strong>retains</strong> the
 * legacy {@code item_sources} key: recognition moves to {@code matcher}, but
 * that key is still the construction base every gem is built from.
 *
 * <p>Depends on the shared engine in {@code emaki.jiuwu.craft.corelib.legacy},
 * which is used by every module's converter and must therefore be retired in
 * the same batch as this package, not before.
 *
 * <p>Deliberate exception to the module-wide no-comment rule: this package-level
 * Javadoc records the cleanup contract, because the contract is the reason the
 * package may be deleted without further investigation.
 */
package emaki.jiuwu.craft.gem.legacy;
