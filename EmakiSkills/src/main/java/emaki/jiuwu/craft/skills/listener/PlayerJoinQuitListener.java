package emaki.jiuwu.craft.skills.listener;

import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.skills.config.AppConfig;
import emaki.jiuwu.craft.skills.model.PlayerSkillProfile;
import emaki.jiuwu.craft.skills.service.ActionBarService;
import emaki.jiuwu.craft.skills.service.CastModeService;
import emaki.jiuwu.craft.skills.service.PlayerSkillDataStore;

/**
 * Handles player join/quit for skill data loading, saving, and cast mode restoration.
 */
public final class PlayerJoinQuitListener implements Listener {

    private final JavaPlugin plugin;
    private final PlayerSkillDataStore dataStore;
    private final CastModeService castModeService;
    private final ActionBarService actionBarService;
    private final Supplier<AppConfig> configSupplier;

    public PlayerJoinQuitListener(JavaPlugin plugin,
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

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Asynchronously load player data, then apply on main thread
        dataStore.loadAsync(player).thenAccept(profile -> {
            // loadAsync may complete on async thread; schedule back to main thread
            if (!player.isOnline()) {
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                applyJoinState(player, profile);
            });
        });
    }

    private void applyJoinState(Player player, PlayerSkillProfile profile) {
        // Restore cast mode if configured
        AppConfig config = configSupplier.get();
        if (config != null && config.castMode().restoreLastStateOnJoin()) {
            if (profile != null && profile.castModeEnabled()) {
                castModeService.setCastMode(player, true);
            }
        } else {
            // Config says don't restore -> ensure cast mode is off
            castModeService.setCastMode(player, false);
        }

        // If cast mode is active, refresh action bar
        if (castModeService.isCastModeEnabled(player)) {
            actionBarService.refreshPlayer(player);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Asynchronously save and unload — no need to block the main thread
        dataStore.unloadAsync(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onKick(PlayerKickEvent event) {
        Player player = event.getPlayer();

        // 被踢玩家同样需要保存并卸载技能数据，防止缓存泄漏
        dataStore.unloadAsync(player.getUniqueId());
    }
}
