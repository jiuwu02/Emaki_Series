/**
 * Structured equipment-skill PDC payload codec shared by EmakiSkills and selected Emaki runtimes.
 *
 * <h2>Stability</h2>
 * Stable wire-format helpers. The normalized payload and key names are the supported way to preserve skill
 * annotations when an item is rebuilt.
 *
 * <h2>Threading</h2>
 * Pure normalization and detached payload inspection may run anywhere. Reading or writing an
 * {@link org.bukkit.inventory.ItemStack} must obey the owner-thread rule for the inventory or entity that
 * owns that stack.
 *
 * <h2>Degradation</h2>
 * This codec does not consult the EmakiSkills runtime bridge and remains usable as a standalone data helper.
 * Calls that require the runtime instead go through the facade, which returns
 * {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult#unavailable()} or a documented no-op layer.
 */
package emaki.jiuwu.craft.skills.api.pdc;
