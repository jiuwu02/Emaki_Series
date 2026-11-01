package emaki.jiuwu.craft.dungeon.guard;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceRegistry;

public final class TeleportGuard implements Listener {

    private final MessageService messages;
    private final InstanceRegistry registry;
    private final Map<UUID, AtomicInteger> permits = new ConcurrentHashMap<>();

    public TeleportGuard(MessageService messages, InstanceRegistry registry) {
        this.messages = messages;
        this.registry = registry;
    }

    public void permit(UUID playerId) {
        if (playerId == null) {
            return;
        }
        permits.computeIfAbsent(playerId, key -> new AtomicInteger()).incrementAndGet();
    }

    public boolean consumePermit(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        AtomicInteger counter = permits.get(playerId);
        if (counter == null) {
            return false;
        }
        while (true) {
            int current = counter.get();
            if (current <= 0) {
                return false;
            }
            if (counter.compareAndSet(current, current - 1)) {
                return true;
            }
        }
    }

    public void forget(UUID playerId) {
        if (playerId != null) {
            permits.remove(playerId);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        var from = event.getFrom();
        var to = event.getTo();
        if (to == null || to.getWorld() == null || from.getWorld() == null) {
            return;
        }
        if (from.getWorld().equals(to.getWorld())) {
            return;
        }
        if (registry.get(from.getWorld().getName()) == null) {
            return;
        }
        if (player.hasPermission("emakidungeon.bypass.escape")) {
            return;
        }
        if (consumePermit(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        messages.send(player, "guard.teleport_blocked", Map.of(
                "world", to.getWorld().getName()
        ));
    }
}
