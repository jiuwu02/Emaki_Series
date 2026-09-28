package emaki.jiuwu.craft.item.api.effect;

import java.util.Map;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Apply-time view of one custom {@code effects} entry, carrying the item being built.
 *
 * <p>Supplied by EmakiItem; third-party plugins must not implement it. The item stack is the live stack being
 * built, so write to it directly and do not retain it.
 */
@ApiStatus.NonExtendable
public interface ItemEffectApplyContext extends ItemEffectParseContext {

    /** {@return the item currently being built} */
    @NotNull ItemStack itemStack();

    /**
     * {@return the resolved expression variables of the definition, used for templating}
     *
     * <p>Never {@code null}. Values are already resolved, so a custom effect may substitute them directly.
     */
    @NotNull Map<String, Object> variables();

    /** {@return the normalized equip slot of the definition, for example {@code main_hand}} */
    @NotNull String equipSlot();
}
