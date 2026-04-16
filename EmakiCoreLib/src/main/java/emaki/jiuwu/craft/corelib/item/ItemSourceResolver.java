package emaki.jiuwu.craft.corelib.item;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;

public interface ItemSourceResolver {

    String id();

    int priority();

    boolean supports(ItemSource source);

    default boolean isAvailable(ItemSource source) {
        return supports(source);
    }

    ItemSource identify(ItemStack itemStack);

    ItemStack create(ItemSource source, int amount);

    default String displayName(ItemSource source) {
        if (!supports(source)) {
            return null;
        }
        ItemStack itemStack = create(source, 1);
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (!ItemTextBridge.hasCustomName(itemMeta)) {
            return null;
        }
        String displayName = MiniMessages.serialize(ItemTextBridge.customName(itemMeta));
        return Texts.isBlank(displayName) ? null : displayName;
    }
}
