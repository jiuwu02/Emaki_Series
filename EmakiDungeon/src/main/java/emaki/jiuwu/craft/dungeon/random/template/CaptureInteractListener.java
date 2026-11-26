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
        String dungeonId = session.worldName.startsWith(CaptureService.EDIT_WORLD_PREFIX)
                ? session.worldName.substring(CaptureService.EDIT_WORLD_PREFIX.length())
                : "";
        if (dungeonId.isBlank()) {
            return;
        }
        ModuleNode sessionNode = captures.nodeAt(session, block);
        if (sessionNode != null) {
            event.setCancelled(true);
            gui.openNode(player, sessionNode);
            return;
        }
        CaptureService.StructureHit hit = captures.structureAt(dungeonId, block);
        if (hit != null) {
            event.setCancelled(true);
            if (hit.nodeId() != null) {
                gui.openStructureNode(player, hit.templateId(), hit.nodeId());
            } else {
                gui.openPlacement(player, block, hit.templateId());
            }
            return;
        }
        if (insideSession(session, block)) {
            event.setCancelled(true);
            gui.openPlacement(player, block, "");
        }
    }

    private boolean insideSession(CaptureService.Session session, Block block) {
        if (session.pos1 == null || session.pos2 == null) {
            return false;
        }
        int minX = Math.min(session.pos1[0], session.pos2[0]);
        int minY = Math.min(session.pos1[1], session.pos2[1]);
        int minZ = Math.min(session.pos1[2], session.pos2[2]);
        int maxX = Math.max(session.pos1[0], session.pos2[0]);
        int maxY = Math.max(session.pos1[1], session.pos2[1]);
        int maxZ = Math.max(session.pos1[2], session.pos2[2]);
        return block.getX() >= minX && block.getX() <= maxX
                && block.getY() >= minY && block.getY() <= maxY
                && block.getZ() >= minZ && block.getZ() <= maxZ;
    }
}
