/**
 * Item sources: register your own item provider and resolve item references across plugins.
 *
 * <h2>Model</h2>
 * An {@link emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef} is "one item from one source",
 * written in config as shorthand text such as {@code minecraft-iron_ingot} or {@code ce-namespace:id}.
 * Which source it belongs to is an
 * {@link emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceKind} &mdash; a record, not an enum, so
 * third parties can mint their own and no {@code switch} over it can be exhaustive.
 *
 * <h2>Where to start</h2>
 * Implement {@link emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceProvider} and register it with
 * {@code EmakiCoreLibApi.registerItemSource}. One registration declares the kind, the shorthand
 * prefixes and the resolution behaviour together; see that interface for why they cannot be split.
 *
 * <h2>Ownership rule</h2>
 * Whoever supplies the provider declares the kind and the prefixes. CoreLib ships eight built-in kinds
 * (vanilla plus seven third-party bridges) and claims their seventeen prefixes; it deliberately knows
 * nothing about item sources it does not implement.
 *
 * <h2>Diagnostics</h2>
 * {@link emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceProbeState} separates
 * {@code PROVIDER_MISSING} from {@code SOURCE_NOT_FOUND}. Keep them apart in user-facing messages:
 * the first means no plugin claims that prefix, the second means the plugin is there but the item is
 * not. Collapsing them sends server owners hunting for typos in correct config.
 *
 * <h2>Degradation</h2>
 * With EmakiCoreLib absent, registration returns
 * {@link emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRegistration#unavailable}, lookups return
 * an empty {@link java.util.Optional} and probes return {@code PROVIDER_MISSING}.
 */
package emaki.jiuwu.craft.corelib.api.itemsource;
