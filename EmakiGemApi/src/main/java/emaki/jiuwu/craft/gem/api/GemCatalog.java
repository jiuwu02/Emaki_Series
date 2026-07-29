package emaki.jiuwu.craft.gem.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.gem.api.model.GemDefinitionView;
import emaki.jiuwu.craft.gem.api.model.GemRelationshipCheck;
import emaki.jiuwu.craft.gem.api.model.GemResonanceView;
import emaki.jiuwu.craft.gem.api.model.GemStateView;

/**
 * Read-only queries against EmakiGem's gem definitions and the gem layer stored on equipment.
 *
 * <p>Reached through {@code EmakiGemApi.catalog()}.
 *
 * <p><strong>Thread:</strong> every method here reads a definition table or an item's persistent data
 * and may be called from any thread, provided the caller is not mutating the same stack concurrently.
 * None of these methods fire events or write state.
 */
@ApiStatus.NonExtendable
public interface GemCatalog {

    /** {@return every loaded gem id, sorted; empty when EmakiGem is unavailable} */
    @NotNull
    List<String> gemIds();

    /**
     * Resolves a gem definition at a specific level.
     *
     * <p>EmakiGem scales stats, attributes, and skills per level, so the level must be supplied rather
     * than defaulted.
     *
     * @param gemId the gem id
     * @param level the level to resolve values for; values below one are treated as one
     * @return the definition when the gem exists, otherwise an empty optional
     */
    @NotNull
    Optional<GemDefinitionView> gem(@Nullable String gemId, int level);

    /**
     * Identifies a loose gem item.
     *
     * @param itemStack the stack to identify
     * @return the gem definition at the item's own level, or an empty optional when the stack is not a
     *         gem
     */
    @NotNull
    Optional<GemDefinitionView> identifyGem(@Nullable ItemStack itemStack);

    /**
     * @param itemStack the stack to test
     * @return whether the stack is a socket opener item
     */
    boolean isOpenerItem(@Nullable ItemStack itemStack);

    /**
     * Reads the gem layer stored on a piece of equipment.
     *
     * @param equipment the equipment to inspect
     * @return the socket state, or a failure when the stack carries no EmakiGem equipment definition
     */
    @NotNull
    EmakiResult<GemStateView> state(@Nullable ItemStack equipment);

    /**
     * Sums the attribute contributions of every gem inlaid in a piece of equipment.
     *
     * @param equipment the equipment to inspect
     * @return attribute id to summed value; empty when the equipment holds no gems
     */
    @NotNull
    Map<String, Double> aggregatedAttributes(@Nullable ItemStack equipment);

    /**
     * Collects the skill ids granted by every gem inlaid in a piece of equipment.
     *
     * <p>Duplicates are preserved: two copies of the same gem grant the same skill twice, which is how
     * EmakiGem stacks skill effects.
     *
     * @param equipment the equipment to inspect
     * @return granted skill ids, possibly containing duplicates
     */
    @NotNull
    List<String> aggregatedSkillIds(@Nullable ItemStack equipment);

    /**
     * Evaluates which resonances the gems inlaid in a piece of equipment currently satisfy.
     *
     * @param equipment the equipment to inspect
     * @return active resonances in resolution order; empty when none apply
     */
    @NotNull
    List<GemResonanceView> resonances(@Nullable ItemStack equipment);

    /**
     * Checks whether a gem may be inlaid into a specific slot, evaluating socket compatibility,
     * dependencies, conflicts, and per-type or per-id limits.
     *
     * <p>A business rejection is returned as a <em>successful</em> result whose
     * {@link GemRelationshipCheck#allowed()} is {@code false}.
     *
     * @param equipment the target equipment
     * @param gemId     the gem to test
     * @param slotIndex the slot to test
     * @return the relationship outcome
     */
    @NotNull
    EmakiResult<GemRelationshipCheck> canInlay(@Nullable ItemStack equipment,
                                               @Nullable String gemId,
                                               int slotIndex);

    /**
     * Checks whether the gem in a specific slot may be extracted.
     *
     * @param equipment the target equipment
     * @param slotIndex the slot to test
     * @return the relationship outcome
     */
    @NotNull
    EmakiResult<GemRelationshipCheck> canExtract(@Nullable ItemStack equipment, int slotIndex);
}
