package emaki.jiuwu.craft.corelib.item;

import org.bukkit.inventory.ItemStack;

public interface ItemSourceResolver {

    String id();

    int priority();

    boolean supports(ItemSource source);

    default boolean isAvailable(ItemSource source) {
        return supports(source);
    }

    ItemSource identify(ItemStack itemStack);

    ItemStack create(ItemSource source, int amount);
}
