package emaki.jiuwu.craft.skills.listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import emaki.jiuwu.craft.corelib.api.async.AsyncFailures;
import emaki.jiuwu.craft.skills.EmakiSkillsPlugin;
import emaki.jiuwu.craft.skills.config.AppConfig;
import emaki.jiuwu.craft.skills.model.PlayerSkillProfile;
import emaki.jiuwu.craft.skills.service.ActionBarService;
import emaki.jiuwu.craft.skills.service.CastModeService;
import emaki.jiuwu.craft.skills.service.PlayerSkillDataStore;

public final class PlayerJoinQuitListener implements Listener {

    private final EmakiSkillsPlugin plugin;
    private final PlayerSkillDataStore dataStore;
    private final CastModeService castModeService;
    private final ActionBarService actionBarService;
    private final Supplier<AppConfig> configSupplier;
    private final Map<UUID, ConnectionSession> sessions = new ConcurrentHashMap<>();

    public PlayerJoinQuitListener(EmakiSkillsPlugin plugin,
                                  PlayerSkillDataStore dataStore,
                                  CastModeService castModeService,
                                  ActionBarService actionBarService,
                                  Supplier<AppConfig> configSupplier) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.castModeService = castModeService;
        this.actionBarService = actionBarService;
        this.configSupplier = configSupplier;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        ConnectionSession previous = sessions.remove(playerId);
        if (previous != null) {
            closeSession(playerId, previous);
        }

        var loadFuture = dataStore.loadAsync(player);
        long generation = dataStore.currentGeneration(playerId);
        ConnectionSession session = new ConnectionSession(player, generation);
        sessions.put(playerId, session);
        loadFuture.whenComplete((profile, throwable) -> {
            if (throwable != null) {
                logFailure("load", playerId, throwable);
                return;
            }
            if (profile == null || !isCurrent(player, session)) {
                return;
            }
            try {
                plugin.scheduling().runForEntity(plugin, player, () -> {
                    if (isCurrent(player, session)) {
                        applyJoinState(player, profile);
                    }
                }, () -> { });
            } catch (Throwable schedulingFailure) {
                logFailure("join-schedule", playerId, schedulingFailure);
            }
        });
    }

    private void applyJoinState(Player player, PlayerSkillProfile profile) {
        AppConfig config = configSupplier.get();
        if (config != null && config.castMode().restoreLastStateOnJoin()) {
            if (profile != null && profile.castModeEnabled()) {
                castModeService.setCastMode(player, true);
            }
        } else {
            castModeService.setCastMode(player, false);
        }

        if (castModeService.isCastModeEnabled(player)) {
            actionBarService.refreshPlayer(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        closeSession(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(PlayerKickEvent event) {
        closeSession(event.getPlayer());
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
        dataStore.unloadAsync(playerId, session.generation())
                .whenComplete((ignored, throwable) -> logFailure("close", playerId, throwable));
    }

    private boolean isCurrent(Player player, ConnectionSession session) {
        ConnectionSession current = sessions.get(player.getUniqueId());
        return current == session
                && current.player() == player
                && player.isOnline()
                && dataStore.isCurrentGeneration(player.getUniqueId(), session.generation());
    }

    private void logFailure(String operation, UUID playerId, Throwable throwable) {
        if (throwable == null) {
            return;
        }
        Throwable cause = AsyncFailures.unwrapOnce(throwable);
        plugin.getLogger().log(Level.WARNING,
                "[SkillDataStore] Async " + operation + " failed for " + playerId,
                cause);
    }

    private record ConnectionSession(Player player, long generation) {
    }
}
