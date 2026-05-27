package emaki.jiuwu.craft.item.api;

import java.util.Set;

import org.bukkit.inventory.ItemStack;

public interface EmakiItemApi {

    boolean exists(String id);

    ItemStack create(String id, int amount);

    String identify(ItemStack itemStack);

    Set<String> definitionIds();

    String displayName(String id);
}
