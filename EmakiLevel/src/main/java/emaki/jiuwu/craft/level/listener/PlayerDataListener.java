package emaki.jiuwu.craft.level.listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.level.EmakiLevelPlugin;
import emaki.jiuwu.craft.level.model.PlayerLevelData;

public final class PlayerDataListener implements Listener {

    private final EmakiLevelPlugin plugin;
    private final ExecutionDispatcher executionDispatcher;
    private final Map<UUID, ConnectionSession> sessions = new ConcurrentHashMap<>();

    public PlayerDataListener(EmakiLevelPlugin plugin, ExecutionDispatcher executionDispatcher) {
        this.plugin = plugin;
        this.executionDispatcher = executionDispatcher;
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
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        ConnectionSession current = sessions.get(playerId);
        if (current != null
                && current.player() == player
                && plugin.dataStore().isKnownGeneration(playerId, current.generation())) {
            return;
        }

        ConnectionSession previous = sessions.remove(playerId);
        if (previous != null) {
            closeSession(playerId, previous);
        }

        var loadFuture = plugin.dataStore().beginSession(player, plugin.typeRegistry().asMap());
        ConnectionSession session = new ConnectionSession(player, plugin.dataStore().currentGeneration(playerId));
        sessions.put(playerId, session);
        loadFuture.whenComplete((data, throwable) -> {
            if (throwable != null) {
                logFailure("load", playerId, throwable);
                return;
            }
            if (data == null || !isCurrent(player, session)) {
                return;
            }
            try {
                if (executionDispatcher.runEntity(
                        plugin,
                        player,
                        () -> applyLoadedSession(player, session, data),
                        () -> logFailure("apply", playerId, new IllegalStateException("owner_schedule_retired"))) == null) {
                    logFailure("apply", playerId, new IllegalStateException("owner_schedule_rejected"));
                }
            } catch (Throwable scheduleFailure) {
                logFailure("apply", playerId, scheduleFailure);
            }
        });
    }

    private void applyLoadedSession(Player player, ConnectionSession session, PlayerLevelData loaded) {
        if (!isCurrent(player, session)) {
            return;
        }
        plugin.dataStore().mutate(
                player.getUniqueId(),
                session.generation(),
                plugin.typeRegistry().asMap(),
                data -> null
        );
        PlayerLevelData current = plugin.dataStore().cached(player.getUniqueId());
        plugin.topService().update(current == null ? loaded : current);
        plugin.levelService().syncPlayer(player);
    }

    private void closeSession(Player player) {
        UUID playerId = player.getUniqueId();
        ConnectionSession session = sessions.get(playerId);
        if (session == null || session.player() != player || !sessions.remove(playerId, session)) {
            return;
        }
        closeSession(playerId, session);
    }

    private void closeSession(UUID playerId, ConnectionSession session) {
        plugin.dataStore().unloadAsync(playerId, session.generation())
                .whenComplete((ignored, throwable) -> logFailure("close", playerId, throwable));
    }

    private boolean isCurrent(Player player, ConnectionSession session) {
        ConnectionSession current = sessions.get(player.getUniqueId());
        return current == session
                && current.player() == player
                && player.isOnline()
                && plugin.dataStore().isCurrentGeneration(player.getUniqueId(), session.generation());
    }

    private void logFailure(String operation, UUID playerId, Throwable throwable) {
        if (throwable == null) {
            return;
        }
        Throwable cause = throwable instanceof CompletionException completionException
                && completionException.getCause() != null
                        ? completionException.getCause()
                        : throwable;
        plugin.getLogger().log(Level.WARNING,
                "[LevelDataStore] Async " + operation + " failed for " + playerId,
                cause);
    }

    private record ConnectionSession(Player player, long generation) {
    }
}
