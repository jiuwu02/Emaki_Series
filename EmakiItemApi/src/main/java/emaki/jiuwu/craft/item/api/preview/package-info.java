/**
 * Optional third-party preview layers for EmakiItem item rendering.
 *
 * <h2>Stability</h2>
 * Stable. {@link emaki.jiuwu.craft.item.api.preview.ItemLayerPreviewProvider} is the explicit provider
 * extension point; registration handles and requests/results are runtime API values.
 *
 * <h2>Threading</h2>
 * Providers receive a defensive request snapshot and must remain non-blocking. They must not access or
 * mutate a live inventory stack unless the caller is already on that stack owner's thread.
 *
 * <h2>Degradation</h2>
 * When EmakiItem is absent, registration returns a no-op handle and no preview provider is called. Facade
 * operations use {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult#unavailable()} instead of
 * returning {@code null}.
 */
package emaki.jiuwu.craft.item.api.preview;
