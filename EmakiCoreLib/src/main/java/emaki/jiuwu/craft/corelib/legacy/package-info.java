/**
 * Shared one-off migration engine that rewrites legacy {@code item_sources}
 * decision sites into top-level {@code matcher} blocks.
 *
 * <p>This package is migration scaffolding, not plugin behaviour. Nothing on a
 * runtime matching, loading or crafting path reads from it; it is reached only
 * from each module's {@code convert-legacy} command entry point and from the
 * load-time {@link LegacyItemSourceScanner} warning.
 *
 * <h2>Shared-engine warning</h2>
 *
 * <p>Unlike the per-module {@code legacy} packages, this one is depended upon by
 * <strong>all</strong> of them ({@code EmakiCooking}, {@code EmakiForge},
 * {@code EmakiGem}, {@code EmakiLevel}, {@code EmakiStrengthen}). Deleting it on
 * its own breaks every module's converter. It must be retired in the same batch
 * as the module-level {@code legacy} packages, never before.
 *
 * <h2>Cleanup contract</h2>
 *
 * <p>To retire the whole migration facility, delete in one change:
 *
 * <ul>
 *   <li>this package;</li>
 *   <li>every module's {@code legacy} package;</li>
 *   <li>the single {@code case "convert-legacy" -> ...} line in each module's
 *       command router, and the {@code "convert-legacy"} string in its tab
 *       completion list;</li>
 *   <li>the {@code command.convert_legacy.*} and
 *       {@code command.help.desc.convert_legacy} keys in both language files of
 *       each module;</li>
 *   <li>each module's load-time {@code reportLegacyItemSources()} call.</li>
 * </ul>
 *
 * <p>{@link LegacyMessageSink} is the one member with a non-migration
 * consumer: {@code AbstractMessageService} and {@code LevelMessageService}
 * implement it. Deleting this package requires dropping those two
 * {@code implements} clauses as well.
 *
 * <p>Deliberate exception to the module-wide no-comment rule: this
 * package-level Javadoc records the cleanup contract, because the contract is
 * the reason the package may be deleted without further investigation.
 */
package emaki.jiuwu.craft.corelib.legacy;
