package emaki.jiuwu.craft.cooking;

import emaki.jiuwu.craft.cooking.model.StationBreakContext;
import emaki.jiuwu.craft.cooking.model.StationInteraction;
import emaki.jiuwu.craft.cooking.service.ChoppingBoardRuntimeService;
import emaki.jiuwu.craft.cooking.service.GrinderRuntimeService;
import emaki.jiuwu.craft.cooking.service.SteamerRuntimeService;
import emaki.jiuwu.craft.cooking.service.WokRuntimeService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

final class CookingStationListener implements Listener {

    private final ChoppingBoardRuntimeService choppingBoardRuntimeService;
    private final WokRuntimeService wokRuntimeService;
    private final GrinderRuntimeService grinderRuntimeService;
    private final SteamerRuntimeService steamerRuntimeService;

    CookingStationListener(ChoppingBoardRuntimeService choppingBoardRuntimeService,
            WokRuntimeService wokRuntimeService,
            GrinderRuntimeService grinderRuntimeService,
            SteamerRuntimeService steamerRuntimeService) {
        this.choppingBoardRuntimeService = choppingBoardRuntimeService;
        this.wokRuntimeService = wokRuntimeService;
        this.grinderRuntimeService = grinderRuntimeService;
        this.steamerRuntimeService = steamerRuntimeService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || event.getHand() == null) {
            return;
        }
        StationInteraction interaction = new StationInteraction(
                event.getPlayer(),
                event.getClickedBlock(),
                event.getAction() == Action.LEFT_CLICK_BLOCK,
                event.getAction() == Action.RIGHT_CLICK_BLOCK,
                event.getHand() == EquipmentSlot.HAND,
                event::setCancelled
        );
        dispatchInteraction(interaction);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        dispatchBreak(new StationBreakContext(
                event.getPlayer(),
                event.getBlock(),
                event::setCancelled
        ));
    }

    void dispatchInteraction(StationInteraction interaction) {
        if (choppingBoardRuntimeService.handleInteraction(interaction)) {
            return;
        }
        if (wokRuntimeService.handleInteraction(interaction)) {
            return;
        }
        if (steamerRuntimeService.handleInteraction(interaction)) {
            return;
        }
        grinderRuntimeService.handleInteraction(interaction);
    }

    void dispatchBreak(StationBreakContext context) {
        if (choppingBoardRuntimeService.handleBreak(context)) {
            return;
        }
        if (wokRuntimeService.handleBreak(context)) {
            return;
        }
        if (steamerRuntimeService.handleBreak(context)) {
            return;
        }
        grinderRuntimeService.handleBreak(context);
    }
}
