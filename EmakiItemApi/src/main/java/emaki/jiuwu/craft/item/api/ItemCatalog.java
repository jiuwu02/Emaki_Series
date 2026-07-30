package emaki.jiuwu.craft.item.api;

import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;

/**
 * Read-only access to loaded EmakiItem definitions and item conditions.
 *
 * <p>Definition lookups may be called from any thread. Item inspection is safe for detached stacks;
 * stacks owned by a live inventory must be inspected on that holder's owner thread. Condition evaluation
 * touches player state and must run on the player's entity-owner thread.
 */
@ApiStatus.NonExtendable
public interface ItemCatalog {

    /** {@return the immutable set of loaded canonical definition ids} */
    @NotNull
    Set<String> definitionIds();

    /**
     * Resolves a configured item definition by canonical id or alias.
     *
     * @param id definition id or alias
     * @return the definition, {@code NOT_FOUND} for an unknown id, or {@code UNAVAILABLE}
     */
    @NotNull
    EmakiResult<ConfiguredItemDefinition> definition(@Nullable String id);

    /**
     * Identifies the definition recorded on a concrete stack.
     *
     * @param itemStack stack to inspect
     * @return the canonical id, or {@code NOT_FOUND} when the stack is not managed by EmakiItem
     */
    @NotNull
    EmakiResult<String> identify(@Nullable ItemStack itemStack);

    /**
     * Resolves the effective rendered display name for a definition without firing a creation event.
     *
     * @param id definition id or alias
     * @return the effective name as MiniMessage text
     */
    @NotNull
    EmakiResult<String> displayName(@Nullable String id);

    /**
     * Checks whether a canonical id or alias resolves to a loaded definition.
     *
     * <p>When the bridge is unavailable this returns {@code false}; use {@link EmakiItemApi#status()} when
     * availability must be distinguished from a genuine miss.
     *
     * @param id definition id or alias
     * @return whether the definition exists
     */
    boolean exists(@Nullable String id);

    /**
     * Evaluates a definition's configured conditions through the real runtime condition service.
     *
     * <p>This method intentionally uses the normal condition path: pass/fail actions and the configured
     * denial message may run. It must be called on the player's entity-owner thread.
     *
     * @param player player used by conditions and placeholders
     * @param itemId definition id or alias
     * @param trigger trigger exposed to the condition context
     * @param itemStack optional concrete stack exposed to conditions
     * @return whether the conditions passed
     */
    @NotNull
    EmakiResult<Boolean> conditionPasses(@Nullable Player player,
                                         @Nullable String itemId,
                                         @Nullable String trigger,
                                         @Nullable ItemStack itemStack);
}
