package emaki.jiuwu.craft.codex.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import emaki.jiuwu.craft.codex.EmakiCodexPlugin;
import emaki.jiuwu.craft.corelib.async.FoliaSchedulerAdapter;

/**
 * Pushes the EmakiCodex advancement tree to a player shortly after they join.
 *
 * <p>Advancements registered at runtime via {@code Bukkit.getUnsafe().loadAdvancement}
 * are part of the tree the vanilla server auto-sends on login, so a joining client does
 * receive them. But that auto-sent packet is not intercepted by our coordinate channel in
 * a way we can rely on for freshly (re)registered nodes, and its exact timing relative to
 * plugin registration is not guaranteed. To make the client advancement screen show the
 * complete Codex tree with the configured coordinates from the very first open, we re-push
 * the same payload the reload path uses, a few ticks after join.
 *
 * <p>The push is a no-op when PacketEvents is absent (the client still has the vanilla
 * auto-sent tree, just without coordinate injection), so this listener never hard-depends
 * on the soft dependency.
 */
public final class PlayerConnectionListener implements Listener {

    /** Delay before pushing, so the vanilla login advancement packet lands first. */
    private static final long RESYNC_DELAY_TICKS = 10L;

    private final EmakiCodexPlugin plugin;

    public PlayerConnectionListener(EmakiCodexPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.appConfig().advancementEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        FoliaSchedulerAdapter.runEntityTaskLater(plugin, player, () -> {
            if (player.isOnline()) {
                plugin.advancementPacketGateway().resync(player);
            }
        }, RESYNC_DELAY_TICKS);
    }
}
