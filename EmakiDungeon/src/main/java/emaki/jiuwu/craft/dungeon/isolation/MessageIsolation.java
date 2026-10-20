package emaki.jiuwu.craft.dungeon.isolation;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.BroadcastMessageEvent;

import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceRegistry;
import emaki.jiuwu.craft.dungeon.snapshot.PlayersDataStore;

public final class MessageIsolation implements Listener {

    private final InstanceRegistry registry;
    private final PlayersDataStore playersData;
    private final MessageService messages;
    private final Map<UUID, Boolean> cache = new ConcurrentHashMap<>();

    public MessageIsolation(InstanceRegistry registry, PlayersDataStore playersData, MessageService messages) {
        this.registry = registry;
        this.playersData = playersData;
        this.messages = messages;
    }

    public boolean inInstance(Player player) {
        return player != null && registry.get(player.getWorld().getName()) != null;
    }

    public String instanceIdOf(Player player) {
        if (player == null) {
            return null;
        }
        var instance = registry.get(player.getWorld().getName());
        return instance == null ? null : instance.instanceId();
    }

    public boolean globalListen(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        return cache.computeIfAbsent(playerId, id -> playersData.read(id).globalChat());
    }

    public boolean toggleGlobal(Player player) {
        UUID playerId = player.getUniqueId();
        boolean current = globalListen(playerId);
        boolean next = !current;
        cache.put(playerId, next);
        PlayersDataStore.PlayerData data = playersData.read(playerId);
        data.setGlobalChat(next);
        playersData.writeAsync(playerId, data);
        return next;
    }

    public void invalidate(UUID playerId) {
        if (playerId != null) {
            cache.remove(playerId);
        }
    }

    public void applyChatViewers(io.papermc.paper.event.player.AsyncChatEvent event, Player sender) {
        if (!inInstance(sender)) {
            event.viewers().removeIf(viewer -> {
                if (!(viewer instanceof Player target)) {
                    return false;
                }
                String targetInstanceId = instanceIdOf(target);
                return targetInstanceId != null && !globalListen(target.getUniqueId());
            });
            return;
        }
        if (globalListen(sender.getUniqueId())) {
            return;
        }
        String instanceId = instanceIdOf(sender);
        event.viewers().removeIf(viewer -> {
            if (!(viewer instanceof Player target)) {
                return true;
            }
            String targetInstanceId = instanceIdOf(target);
            return !instanceId.equals(targetInstanceId);
        });
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBroadcast(BroadcastMessageEvent event) {
        event.getRecipients().removeIf(recipient -> {
            if (!(recipient instanceof Player target)) {
                return false;
            }
            return inInstance(target) && !globalListen(target.getUniqueId());
        });
    }

    public void sendToggleFeedback(Player player, boolean enabled) {
        messages.send(player, enabled ? "globalchat.enabled" : "globalchat.disabled", Map.of());
    }
}
