package emaki.jiuwu.craft.dungeon.editor;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import emaki.jiuwu.craft.dungeon.flow.FlowPoint;

public final class EditorInteractListener implements Listener {

    private final EditorService service;
    private final EditorGui gui;

    public EditorInteractListener(EditorService service, EditorGui gui) {
        this.service = service;
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
        EditorSession session = service.sessionOf(player.getUniqueId());
        if (session == null || !session.ready()) {
            return;
        }
        if (emaki.jiuwu.craft.dungeon.type.DungeonTypeRegistry.RANDOM
                .equals(service.dungeonTypeOf(player.getUniqueId()))) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !block.getWorld().getName().equals(session.editWorldName())) {
            return;
        }
        event.setCancelled(true);
        EditorService.ModuleRef hit = service.moduleAt(session, block);
        if (hit == null) {
            gui.openPlacement(player, new FlowPoint(block.getX() + 0.5D, block.getY(), block.getZ() + 0.5D,
                    player.getLocation().getYaw(), player.getLocation().getPitch()));
            return;
        }
        gui.openModule(player, hit);
    }
}
