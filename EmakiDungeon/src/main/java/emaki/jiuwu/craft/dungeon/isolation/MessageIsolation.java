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
        playersData.updateAsync(playerId, data -> data.setGlobalChat(next));
        return next;
    }

    public void invalidate(UUID playerId) {
        if (playerId != null) {
            cache.remove(playerId);
        }
    }

    public static boolean dropChatRecipient(boolean senderInInstance,
            String senderInstanceId,
            boolean senderGlobalListen,
            String targetInstanceId,
            boolean targetGlobalListen) {
        if (!senderInInstance) {
            return targetInstanceId != null && !targetGlobalListen;
        }
        if (senderGlobalListen) {
            return false;
        }
        return !senderInstanceId.equals(targetInstanceId);
    }

    public void applyChatViewers(io.papermc.paper.event.player.AsyncChatEvent event, Player sender) {
        boolean senderInInstance = inInstance(sender);
        String senderInstanceId = senderInInstance ? instanceIdOf(sender) : null;
        boolean senderGlobal = senderInInstance && globalListen(sender.getUniqueId());
        event.viewers().removeIf(viewer -> {
            if (!(viewer instanceof Player target)) {
                return senderInInstance && !senderGlobal;
            }
            return dropChatRecipient(senderInInstance, senderInstanceId, senderGlobal,
                    instanceIdOf(target), globalListen(target.getUniqueId()));
        });
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBroadcast(BroadcastMessageEvent event) {
        event.getRecipients().removeIf(recipient -> {
            if (!(recipient instanceof Player target)) {
                return false;
            }
            return dropChatRecipient(false, null, false,
                    instanceIdOf(target), globalListen(target.getUniqueId()));
        });
    }

    public void sendToggleFeedback(Player player, boolean enabled) {
        messages.send(player, enabled ? "globalchat.enabled" : "globalchat.disabled", Map.of());
    }
}
