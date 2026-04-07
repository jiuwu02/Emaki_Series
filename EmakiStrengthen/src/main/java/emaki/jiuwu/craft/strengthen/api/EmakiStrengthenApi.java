package emaki.jiuwu.craft.strengthen.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.strengthen.model.AttemptContext;
import emaki.jiuwu.craft.strengthen.model.AttemptPreview;
import emaki.jiuwu.craft.strengthen.model.AttemptResult;
import emaki.jiuwu.craft.strengthen.model.StrengthenState;

public interface EmakiStrengthenApi {

    boolean canStrengthen(ItemStack itemStack);

    StrengthenState readState(ItemStack itemStack);

    AttemptPreview preview(Player player, AttemptContext context);

    AttemptResult attempt(Player player, AttemptContext context);

    ItemStack rebuild(ItemStack itemStack);
}
