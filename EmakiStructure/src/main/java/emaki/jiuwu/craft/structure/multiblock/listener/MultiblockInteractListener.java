package emaki.jiuwu.craft.structure.multiblock.listener;

import emaki.jiuwu.craft.structure.EmakiStructurePlugin;
import emaki.jiuwu.craft.structure.multiblock.MultiblockService;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class MultiblockInteractListener implements Listener {

    private final EmakiStructurePlugin plugin;
    private final MultiblockService service;

    public MultiblockInteractListener(EmakiStructurePlugin plugin, MultiblockService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        try {
            if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
                return;
            }
            if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
                return;
            }
            Block clicked = event.getClickedBlock();
            if (clicked == null || clicked.getType().isAir()) {
                return;
            }
            if (event.getPlayer() == null) {
                return;
            }
            service.onInteract(event.getPlayer(), clicked);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("多方块交互监听异常: " + exception.getMessage());
        }
    }
}
