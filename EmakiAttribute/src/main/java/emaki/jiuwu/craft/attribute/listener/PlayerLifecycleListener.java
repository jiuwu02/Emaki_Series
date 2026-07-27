package emaki.jiuwu.craft.attribute.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import emaki.jiuwu.craft.attribute.service.AttributeService;

public final class PlayerLifecycleListener implements Listener {

    private final AttributeService attributeService;

    public PlayerLifecycleListener(AttributeService attributeService) {
        this.attributeService = attributeService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        attributeService.parentAttributeService().load(event.getPlayer());
        attributeService.scheduleJoinHealthSync(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        attributeService.parentAttributeService().unload(event.getPlayer().getUniqueId(), true);
        attributeService.cleanupEntityState(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        attributeService.parentAttributeService().unload(event.getPlayer().getUniqueId(), true);
        attributeService.cleanupEntityState(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        attributeService.scheduleRespawnHealthSync(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        attributeService.scheduleEquipmentSync(event.getPlayer());
    }
}
