/**
 * Legacy {@code item_sources} conversion for {@code recipes/*.yml}.
 *
 * <p>This package exists only to migrate configurations written before recipe
 * decision sites were converged onto the unified CoreLib {@code matcher}. It is
 * a one-off migration aid, not part of the plugin's behaviour.
 *
 * <h2>Cleanup contract</h2>
 *
 * <ul>
 *   <li>It is invoked <strong>only from the command entry point</strong>
 *       ({@code /emakiforge convert-legacy}) and from the load-time scanner
 *       warning; nothing on a runtime matching, loading or forging path calls
 *       into it.</li>
 *   <li>Deleting the whole package leaves the rest of the plugin compiling,
 *       except for the single {@code case "convert-legacy" -> ...} line in
 *       {@code ForgeCommandRouter}, the {@code "convert-legacy"} entry in its
 *       tab completion list, and the {@code reportLegacyItemSources()} call in
 *       {@code EmakiForgePlugin}.</li>
 *   <li>Also remove the {@code command.convert_legacy.*} and
 *       {@code command.help.desc.convert_legacy} keys from both language
 *       files.</li>
 *   <li>The converter reads no runtime state, writes no persistent data
 *       container entry, and touches no player data. It only reads and rewrites
 *       configuration files inside the plugin data folder.</li>
 * </ul>
 *
 * <p>Depends on the shared engine in {@code emaki.jiuwu.craft.corelib.legacy},
 * which is used by every module's converter and must therefore be retired in
 * the same batch as this package, not before.
 *
 * <p>Deliberate exception to the module-wide no-comment rule: this
 * package-level Javadoc records the cleanup contract, because the contract is
 * the reason the package may be deleted without further investigation.
 */
package emaki.jiuwu.craft.forge.legacy;
