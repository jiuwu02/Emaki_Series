package emaki.jiuwu.craft.cooking;

import emaki.jiuwu.craft.cooking.model.StationBreakContext;
import emaki.jiuwu.craft.cooking.model.StationInteraction;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceType;
import net.momirealms.craftengine.bukkit.api.event.CustomBlockBreakEvent;
import net.momirealms.craftengine.bukkit.api.event.CustomBlockInteractEvent;
import net.momirealms.craftengine.bukkit.api.event.FurnitureBreakEvent;
import net.momirealms.craftengine.bukkit.api.event.FurnitureHitEvent;
import net.momirealms.craftengine.bukkit.api.event.FurnitureInteractEvent;
import net.momirealms.craftengine.bukkit.entity.furniture.BukkitFurniture;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

final class CraftEngineCookingStationListener implements Listener {

    private final CookingStationListener stationListener;

    CraftEngineCookingStationListener(CookingStationListener stationListener) {
        this.stationListener = stationListener;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraftEngineInteract(CustomBlockInteractEvent event) {
        Player player = event.player();
        Block block = event.bukkitBlock();
        ItemSource source = craftEngineSource(event.customBlock());
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
        ItemSource source = craftEngineSource(event.customBlock());
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraftEngineFurnitureInteract(FurnitureInteractEvent event) {
        Block block = anchorBlock(event.location());
        ItemSource source = craftEngineSource(event.furniture());
        Player player = event.player();
        if (player == null || block == null || source == null) {
            return;
        }
        stationListener.dispatchInteraction(new StationInteraction(
                player,
                block,
                false,
                true,
                event.hand() == InteractionHand.MAIN_HAND,
                event::setCancelled,
                source
        ));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraftEngineFurnitureHit(FurnitureHitEvent event) {
        Block block = anchorBlock(event.location());
        ItemSource source = craftEngineSource(event.furniture());
        Player player = event.player();
        if (player == null || block == null || source == null) {
            return;
        }
        stationListener.dispatchInteraction(new StationInteraction(
                player,
                block,
                true,
                false,
                true,
                event::setCancelled,
                source
        ));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraftEngineFurnitureBreak(FurnitureBreakEvent event) {
        Block block = anchorBlock(event.location());
        ItemSource source = craftEngineSource(event.furniture());
        if (block == null || source == null) {
            return;
        }
        stationListener.dispatchBreak(new StationBreakContext(
                event.player(),
                block,
                event::setCancelled,
                source
        ));
    }

    private Block anchorBlock(Location location) {
        return location == null || location.getWorld() == null ? null : location.getBlock();
    }

    private ItemSource craftEngineSource(BukkitFurniture furniture) {
        return craftEngineSource(furniture == null ? null : furniture.id());
    }

    private ItemSource craftEngineSource(BlockDefinition blockDefinition) {
        return craftEngineSource(blockDefinition == null ? null : blockDefinition.id());
    }

    private ItemSource craftEngineSource(Key key) {
        String text = key == null ? "" : key.asString();
        return text.isBlank() ? null : new ItemSource(ItemSourceType.CRAFTENGINE, text);
    }
}
