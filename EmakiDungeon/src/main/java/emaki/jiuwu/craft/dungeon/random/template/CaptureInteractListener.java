package emaki.jiuwu.craft.dungeon.random.template;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import emaki.jiuwu.craft.dungeon.random.model.TemplateModel.ModuleNode;

public final class CaptureInteractListener implements Listener {

    private final CaptureService captures;
    private final CaptureGui gui;

    public CaptureInteractListener(CaptureService captures, CaptureGui gui) {
        this.captures = captures;
        this.gui = gui;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.getInventory().getItemInMainHand().getType().isAir()
                || !player.getInventory().getItemInOffHand().getType().isAir()) {
            return;
        }
        CaptureService.Session session = captures.sessionOf(player.getUniqueId());
        if (session == null) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !block.getWorld().getName().equals(session.worldName)) {
            return;
        }
        event.setCancelled(true);
        ModuleNode node = captures.nodeAt(session, block);
        if (node != null) {
            gui.openNode(player, node);
            return;
        }
        String dungeonId = session.worldName.startsWith(CaptureService.EDIT_WORLD_PREFIX)
                ? session.worldName.substring(CaptureService.EDIT_WORLD_PREFIX.length())
                : "";
        if (!dungeonId.isBlank()) {
            CaptureService.StructureHit hit = captures.structureAt(dungeonId, block);
            if (hit != null && hit.nodeId() != null) {
                gui.openStructureNode(player, hit.templateId(), hit.nodeId());
                return;
            }
            if (hit != null) {
                gui.openStructure(player, hit.templateId());
                return;
            }
        }
        gui.openPlacement(player, block);
    }
}
