package emaki.jiuwu.craft.codex.recipe;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.codex.recipe.model.ItemRef;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceType;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * Converts Bukkit {@link ItemStack}s into {@link ItemRef}s using corelib's
 * {@link ItemSourceService}. This is where a recipe's custom items get translated
 * into the stable, client-identifiable shorthand form; recipe traversal itself
 * stays decoupled from any plugin-specific item API.
 */
@SuppressWarnings("deprecation") // Material#getKey is soft-deprecated but the stable id source on Spigot
public final class ItemRefFactory {

    private final ItemSourceService itemSourceService;

    public ItemRefFactory(ItemSourceService itemSourceService) {
        this.itemSourceService = itemSourceService;
    }

    /**
     * Builds an item reference for a recipe stack.
     *
     * @param stack the ingredient or result stack (may be null/air)
     * @return the resolved reference, never {@code null}
     */
    public ItemRef toRef(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return ItemRef.empty();
        }
        int amount = Math.max(1, stack.getAmount());
        ItemSource source = itemSourceService == null ? null : itemSourceService.identifyItem(stack);
        if (source == null) {
            String fallback = "minecraft-" + stack.getType().getKey().getKey();
            return new ItemRef(fallback, amount, false);
        }
        String shorthand = ItemSourceUtil.toShorthand(source);
        if (Texts.isBlank(shorthand)) {
            shorthand = "minecraft-" + stack.getType().getKey().getKey();
        }
        boolean custom = source.getType() != ItemSourceType.VANILLA;
        return new ItemRef(shorthand, amount, custom);
    }
}
