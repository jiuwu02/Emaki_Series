/**
 * Version-independent item definitions and component build results.
 *
 * <h2>Stability</h2>
 * Stable. {@link emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition} is the input model and
 * {@link emaki.jiuwu.craft.corelib.api.item.ItemBuildResult} the output model for
 * {@code EmakiCoreLibApi.createConfiguredItem} and {@code applyConfiguredItem}.
 *
 * <h2>Why these do not return EmakiResult</h2>
 * {@link emaki.jiuwu.craft.corelib.api.item.ItemBuildResult} is already a complete result model: it
 * separates {@code success()} from a list of
 * {@link emaki.jiuwu.craft.corelib.api.item.ItemBuildIssue} carrying per-component severity, and it
 * expresses unavailability as an error issue. Wrapping it in
 * {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult} would hide exactly the diagnostics
 * callers need when a build fails, so this package keeps its own shape.
 *
 * <h2>Threading</h2>
 * Building and patching may be called from any thread. The returned stack is a defensive copy;
 * placing it into an inventory must happen on the owner thread of that inventory's holder.
 *
 * <h2>Degradation</h2>
 * When EmakiCoreLib or its configured-item service is unavailable, the returned
 * {@link emaki.jiuwu.craft.corelib.api.item.ItemBuildResult} has a {@code null} stack and one error
 * issue explaining why.
 */
package emaki.jiuwu.craft.corelib.api.item;
