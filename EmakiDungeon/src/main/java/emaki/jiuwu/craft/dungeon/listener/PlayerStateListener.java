package emaki.jiuwu.craft.dungeon.listener;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import emaki.jiuwu.craft.dungeon.flow.EventIngressKeys;
import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;
import emaki.jiuwu.craft.dungeon.instance.EventIngress.IngressEvent;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceRegistry;
import emaki.jiuwu.craft.dungeon.state.PlayerStateService;

public final class PlayerStateListener implements Listener {

    private final InstanceRegistry registry;
    private final PlayerStateService stateService;

    public PlayerStateListener(InstanceRegistry registry, PlayerStateService stateService) {
        this.registry = registry;
        this.stateService = stateService;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        DungeonInstance instance = registry.get(player.getWorld().getName());
        if (instance == null) {
            return;
        }
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        Map<String, Object> payload = new HashMap<>();
        payload.put(EventIngressKeys.KEY_PLAYER, player.getUniqueId());
        payload.put("kind", "death");
        instance.ingress().post(new IngressEvent(EventIngressKeys.PLAYER_STATE, player.getUniqueId().toString(), payload));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        DungeonInstance instance = registry.get(player.getWorld().getName());
        if (instance == null) {
            return;
        }
        var location = stateService.resolveRespawn(instance, player);
        if (location != null) {
            event.setRespawnLocation(location);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        DungeonInstance instance = registry.get(player.getWorld().getName());
        if (instance != null && stateService.isSpectator(player.getUniqueId())) {
            player.setGameMode(GameMode.SPECTATOR);
            return;
        }
        if (stateService.isSpectator(player.getUniqueId())) {
            String pointer = pointerOf(player);
            if (instance == null && pointer == null) {
                stateService.forgetPlayer(player.getUniqueId());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        Player player = event.getPlayer();
        if (!stateService.isSpectator(player.getUniqueId())) {
            return;
        }
        if (registry.get(player.getWorld().getName()) == null) {
            return;
        }
        Location from = event.getFrom();
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) {
            return;
        }
        Location locked = from.clone();
        locked.setYaw(to.getYaw());
        locked.setPitch(to.getPitch());
        event.setTo(locked);
    }

    private String pointerOf(Player player) {
        DungeonInstance instance = registry.get(player.getWorld().getName());
        return instance == null ? null : instance.instanceId();
    }
}
