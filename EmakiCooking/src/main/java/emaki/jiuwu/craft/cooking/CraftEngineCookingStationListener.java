package emaki.jiuwu.craft.cooking;

import emaki.jiuwu.craft.cooking.model.StationBreakContext;
import emaki.jiuwu.craft.cooking.model.StationInteraction;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceKind;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import net.momirealms.craftengine.bukkit.api.event.CustomBlockBreakEvent;
import net.momirealms.craftengine.bukkit.api.event.CustomBlockInteractEvent;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * CraftEngine custom-block events. Furniture events (including hit) are handled by CoreLib's
 * furniture relay; see {@code CookingFurnitureEventSubscriber}.
 */
final class CraftEngineCookingStationListener implements Listener {

    private final CookingStationListener stationListener;

    CraftEngineCookingStationListener(CookingStationListener stationListener) {
        this.stationListener = stationListener;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraftEngineInteract(CustomBlockInteractEvent event) {
        Player player = event.player();
        Block block = event.bukkitBlock();
        ItemSourceRef source = craftEngineSource(event.customBlock());
        if (player == null || block == null) {
            return;
        }
        stationListener.dispatchInteraction(new StationInteraction(
                player,
                block,
                event.action() == CustomBlockInteractEvent.Action.LEFT_CLICK,
                event.action() == CustomBlockInteractEvent.Action.RIGHT_CLICK,
                event.hand() == InteractionHand.MAIN_HAND,
                event::setCancelled,
                source
        ));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraftEngineBreak(CustomBlockBreakEvent event) {
        Player player = event.player() == null ? null : event.player().platformPlayer();
        Block block = event.bukkitBlock();
        ItemSourceRef source = craftEngineSource(event.customBlock());
        if (player == null || block == null) {
            return;
        }
        stationListener.dispatchBreak(new StationBreakContext(
                player,
                block,
                event::setCancelled,
                source
        ));
    }

    private ItemSourceRef craftEngineSource(BlockDefinition blockDefinition) {
        return craftEngineSource(blockDefinition == null ? null : blockDefinition.id());
    }

    private ItemSourceRef craftEngineSource(Key key) {
        String text = key == null ? "" : key.asString();
        return text.isBlank() ? null : ItemSourceRef.orNull(ItemSourceKind.CRAFTENGINE, text);
    }
}
