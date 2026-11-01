/**
 * Third-party {@code effects} types for EmakiItem item definitions.
 *
 * <h2>Stability</h2>
 * Stable. {@link emaki.jiuwu.craft.item.api.effect.ItemEffectType} and
 * {@link emaki.jiuwu.craft.item.api.effect.ItemEffect} are the provider extension points; registration handles
 * and contexts are runtime values that are not implemented by consumers.
 *
 * <h2>Threading</h2>
 * Handlers run on whichever thread builds the item, normally the owning stack's thread. They must stay
 * non-blocking and must not schedule work; they write only to the item handed to them.
 *
 * <h2>Degradation</h2>
 * When EmakiItem is absent, registration returns a no-op handle and no handler is ever invoked. Entries whose
 * type has no registered handler are skipped, so a definition may reference a type whose provider is not
 * installed without being rejected.
 */
package emaki.jiuwu.craft.item.api.effect;
