package emaki.jiuwu.craft.gem.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
 * Read-only queries against EmakiGem's loaded definitions and item gem layers.
 *
 * <p>Reached through {@link EmakiGemApi#catalog()}.
 *
 * <p><strong>Thread:</strong> any thread, provided the caller does not mutate an inspected
 * {@link ItemStack} concurrently.
 */
@ApiStatus.NonExtendable
public interface GemCatalog {

    /**
     * Reads the gem layer resolved for an equipment item.
     *
     * @param equipment the equipment to inspect
     * @return the resolved state, or an empty optional when the item is not socketable
     */
    @NotNull
    Optional<GemStateView> readState(@Nullable ItemStack equipment);

    /**
     * Tests whether a stack carries EmakiGem's loose-gem data.
     *
     * <p>Identification reads the item's gem payload, not its display name or lore. {@code null}, air,
     * and stacks without that payload all return {@code false}, and so does an unavailable runtime, so
     * {@code false} means "not recognised as a gem here and now" rather than "definitely not a gem".
     *
     * @param itemStack the item to identify; {@code null} and air are accepted and yield {@code false}
     * @return whether the item is a loose gem item
     */
    boolean isGemItem(@Nullable ItemStack itemStack);

    /**
     * Tests whether a stack matches one of the socket openers declared in EmakiGem's configuration.
     *
     * <p>Only openers that are currently loaded and enabled can match, so a configuration reload that
     * disables an opener flips this to {@code false} for stacks players already hold. As with
     * {@link #isGemItem}, {@code null}, air, and an unavailable runtime return {@code false}.
     *
     * @param itemStack the item to identify; {@code null} and air are accepted and yield {@code false}
     * @return whether the item is a configured socket opener
     */
    boolean isOpenerItem(@Nullable ItemStack itemStack);

    /**
     * Resolves one gem definition at its configured base level.
     *
     * @param gemId the gem id
     * @return the definition, or an empty optional when it is unknown
     */
    @NotNull
    Optional<GemDefinitionView> definition(@Nullable String gemId);

    /** {@return all loaded gem definitions in id order, resolved at their configured base levels} */
    @NotNull
    List<GemDefinitionView> definitions();

    /**
     * Checks whether the supplied loose gem can be inlaid into at least one currently available socket.
     *
     * <p>A rule rejection is returned as a successful result whose
     * {@link GemRelationshipCheck#allowed()} is {@code false}. Invalid items, unavailable services, and
     * unknown gem definitions are failures rather than false successes.
     *
     * @param equipment the target equipment
     * @param gemItem   the loose gem item
     * @return the relationship outcome
     */
    @NotNull
    EmakiResult<GemRelationshipCheck> canInlay(@Nullable ItemStack equipment, @Nullable ItemStack gemItem);

    /**
     * Checks whether the gem in one socket can be extracted.
     *
     * @param equipment the target equipment
     * @param slotIndex the socket index
     * @return the relationship outcome
     */
    @NotNull
    EmakiResult<GemRelationshipCheck> canExtract(@Nullable ItemStack equipment, int slotIndex);

    /**
     * Resolves the highest-priority active resonance on an equipment item.
     *
     * <p>The runtime may activate more than one non-exclusive resonance. This method follows the
     * runtime's resolution order and returns the first one, which is the highest-priority active entry.
     *
     * @param equipment the equipment to inspect
     * @return the active resonance, or {@code NOT_FOUND} when none is active
     */
    @NotNull
    EmakiResult<GemResonanceView> resonance(@Nullable ItemStack equipment);

    /**
     * Sums the attribute contributions of every gem currently inlaid in an equipment item.
     *
     * <p>Each gem contributes the values resolved for the level it was inlaid at, and equal attribute
     * ids are added together. Sockets holding a gem whose definition is no longer loaded are skipped
     * silently, so a configuration change can shrink the result without any error surfacing here.
     *
     * <p>This reflects only EmakiGem's own layer; it does not include the base item's attributes or
     * contributions from other Emaki modules.
     *
     * @param equipment the equipment to inspect; {@code null}, air, and non-socketable items yield an
     *                  empty map
     * @return an unmodifiable map of attribute id to summed value, empty when nothing contributes or
     *         the runtime is unavailable
     */
    @NotNull
    Map<String, Double> aggregatedAttributes(@Nullable ItemStack equipment);

    /**
     * Collects the distinct skill ids granted by the gems currently inlaid in an equipment item.
     *
     * <p>Two gems granting the same skill appear once. As with {@link #aggregatedAttributes}, sockets
     * whose gem definition is no longer loaded are skipped silently, and an empty result does not
     * distinguish "grants no skills" from "runtime unavailable".
     *
     * @param equipment the equipment to inspect; {@code null}, air, and non-socketable items yield an
     *                  empty set
     * @return an unmodifiable set of distinct skill ids, empty when none are granted or the runtime is
     *         unavailable
     */
    @NotNull
    Set<String> aggregatedSkillIds(@Nullable ItemStack equipment);
}
