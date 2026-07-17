package emaki.jiuwu.craft.forge;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import emaki.jiuwu.craft.forge.model.PlayerData;

final class ForgePlayerDataListener implements Listener {

    private final EmakiForgePlugin plugin;
    private final Map<UUID, ConnectionSession> sessions = new ConcurrentHashMap<>();

    ForgePlayerDataListener(EmakiForgePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        ConnectionSession previous = sessions.remove(playerId);
        if (previous != null) {
            previous.requestClose(playerId);
        }
        ConnectionSession session = new ConnectionSession(player);
        sessions.put(playerId, session);
        session.attach(playerId, plugin.playerDataStore().beginSession(playerId));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        closeSession(event.getPlayer());
        if (plugin.recipeBookGuiService() != null) {
            plugin.recipeBookGuiService().removeRecipeBook(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerKick(PlayerKickEvent event) {
        closeSession(event.getPlayer());
        if (plugin.recipeBookGuiService() != null) {
            plugin.recipeBookGuiService().removeRecipeBook(event.getPlayer());
        }
    }

    private void closeSession(Player player) {
        UUID playerId = player.getUniqueId();
        ConnectionSession session = sessions.get(playerId);
        if (session == null || session.player() != player || !sessions.remove(playerId, session)) {
            return;
        }
        session.requestClose(playerId);
    }

    private void logFailure(String operation, UUID playerId, Throwable throwable) {
        if (throwable == null) {
            return;
        }
        Throwable cause = throwable instanceof CompletionException completionException
                && completionException.getCause() != null
                        ? completionException.getCause()
                        : throwable;
        plugin.getLogger().log(java.util.logging.Level.WARNING,
                "[PlayerDataStore] Async " + operation + " failed for " + playerId,
                cause);
    }

    private final class ConnectionSession {

        private final Player player;
        private final AtomicLong generation = new AtomicLong();
        private final AtomicBoolean closeRequested = new AtomicBoolean();
        private final AtomicBoolean closeStarted = new AtomicBoolean();

        private ConnectionSession(Player player) {
            this.player = player;
        }

        private Player player() {
            return player;
        }

        private void attach(UUID playerId, CompletableFuture<PlayerData> loadFuture) {
            CompletableFuture<PlayerData> actual = loadFuture == null
                    ? CompletableFuture.completedFuture(null)
                    : loadFuture;
            actual.whenComplete((loaded, throwable) -> {
                logFailure("load", playerId, throwable);
                if (throwable == null && loaded != null) {
                    generation.compareAndSet(0L, plugin.playerDataStore().currentGeneration(playerId));
                }
                if (sessions.get(playerId) != this) {
                    closeRequested.set(true);
                }
                closeIfReady(playerId);
            });
        }

        private void requestClose(UUID playerId) {
            closeRequested.set(true);
            closeIfReady(playerId);
        }

        private void closeIfReady(UUID playerId) {
            long expectedGeneration = generation.get();
            if (!closeRequested.get() || expectedGeneration <= 0L || !closeStarted.compareAndSet(false, true)) {
                return;
            }
            plugin.playerDataStore().saveAndClearAsync(playerId, expectedGeneration)
                    .whenComplete((ignored, throwable) -> logFailure("close", playerId, throwable));
        }
    }
}
