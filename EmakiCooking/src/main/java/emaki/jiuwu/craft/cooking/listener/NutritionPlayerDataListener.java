package emaki.jiuwu.craft.cooking.listener;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;

/**
 * 玩家营养数据的异步会话生命周期监听器。
 */
public final class NutritionPlayerDataListener implements Listener {

    private final EmakiCookingPlugin plugin;
    private final ConcurrentMap<UUID, SessionRef> sessions = new ConcurrentHashMap<>();

    public NutritionPlayerDataListener(EmakiCookingPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        ensureSession(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        closeSession(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(PlayerKickEvent event) {
        closeSession(event.getPlayer());
    }

    public void ensureSession(Player player) {
        if (player == null || plugin.nutritionDataStore() == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        SessionRef existing = sessions.get(playerId);
        if (existing != null && existing.player() == player
                && plugin.nutritionDataStore().isKnownGeneration(playerId, existing.generation())) {
            return;
        }

        var load = plugin.nutritionDataStore().beginSession(player, plugin.nutritionTypeRegistry().asMap());
        long generation = plugin.nutritionDataStore().currentGeneration(playerId);
        if (generation < 0L) {
            return;
        }
        SessionRef session = new SessionRef(player, generation);
        sessions.put(playerId, session);
        load.whenComplete((_, throwable) -> {
            if (throwable == null || sessions.get(playerId) != session) {
                return;
            }
            plugin.getLogger().log(Level.WARNING,
                    "Failed to load nutrition data for " + playerId,
                    unwrap(throwable));
        });
    }

    private void closeSession(Player player) {
        if (player == null || plugin.nutritionDataStore() == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        SessionRef session = sessions.get(playerId);
        if (session != null && session.player() == player && sessions.remove(playerId, session)) {
            plugin.nutritionDataStore().unloadAsync(playerId, session.generation(), true);
        }
        if (plugin.nutritionService() != null) {
            plugin.nutritionService().handleQuit(playerId);
        }
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private record SessionRef(Player player, long generation) {
    }
}
