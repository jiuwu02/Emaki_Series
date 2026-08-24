/**
 * Legacy {@code item_sources} conversion for {@code config.yml} station
 * settings and {@code recipes/**} inputs.
 *
 * <p>This package exists only to migrate configurations written before cooking
 * decision sites were converged onto the unified CoreLib {@code matcher}. It is
 * a one-off migration aid, not part of the plugin's behaviour.
 *
 * <h2>Cleanup contract</h2>
 *
 * <ul>
 *   <li>It is invoked <strong>only from the command entry point</strong>
 *       ({@code /emakicooking convert-legacy}) and from the load-time scanner
 *       warning; nothing on a runtime matching, loading or cooking path calls
 *       into it.</li>
 *   <li>Deleting the whole package leaves the rest of the plugin compiling,
 *       except for the single {@code case "convert-legacy" -> ...} line in
 *       {@code CookingCommandRouter}, the {@code "convert-legacy"} entry in its
 *       tab completion list, and the {@code reportLegacyItemSources()} call in
 *       {@code EmakiCookingPlugin}.</li>
 *   <li>Also remove the {@code command.convert_legacy.*} keys from both
 *       language files.</li>
 *   <li>The converter reads no runtime state, writes no persistent data
 *       container entry, and touches no player data. It only reads and rewrites
 *       configuration files inside the plugin data folder.</li>
 * </ul>
 *
 * <p>Cooking is the only module whose targets use
 * {@code RuntimeSemantics.AND}: its runtime required both the item source and
 * the matcher to pass, so co-existing entries are wrapped into a single
 * {@code all_of} to keep the decision equivalent. The fermentation-barrel
 * target additionally retains the legacy key, because that key shares a key
 * space with persisted slot state.
 *
 * <p>Depends on the shared engine in {@code emaki.jiuwu.craft.corelib.legacy},
 * which is used by every module's converter and must therefore be retired in
 * the same batch as this package, not before.
 *
 * <p>Deliberate exception to the module-wide no-comment rule: this
 * package-level Javadoc records the cleanup contract, because the contract is
 * the reason the package may be deleted without further investigation.
 */
package emaki.jiuwu.craft.cooking.legacy;
