package emaki.jiuwu.craft.codex.recipe.sync;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.codex.config.AppConfig;
import emaki.jiuwu.craft.codex.recipe.RecipeIndex;
import emaki.jiuwu.craft.codex.recipe.RecipeVisibilityService;
import emaki.jiuwu.craft.corelib.async.FoliaSchedulerAdapter;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;

/**
 * Unified recipe sync entry point. Holds the enabled channels and dispatches a
 * player's visible recipe set to each available channel on the player's region/main
 * thread. Channels are probed at build time and again at send time via
 * {@link RecipeSyncChannel#isAvailable()} so a degraded dependency never breaks sync.
 *
 * <p>The vanilla recipe book channel is always present as the baseline; the PacketEvents
 * (legacy 1.21.1-) and the NMS-reflecting JEI bridge (1.21.2+) channels layer on top when
 * enabled and available.
 */
public final class RecipeSyncGateway {

    private final JavaPlugin plugin;
    private final RecipeVisibilityService visibilityService;
    private final java.util.function.Supplier<DebugLogger> debugLoggerSupplier;
    private final List<RecipeSyncChannel> channels = new ArrayList<>();
    private volatile AppConfig config;

    public RecipeSyncGateway(JavaPlugin plugin,
            RecipeIndex recipeIndex,
            RecipeVisibilityService visibilityService,
            AppConfig config,
            java.util.function.Supplier<DebugLogger> debugLoggerSupplier) {
        this.plugin = plugin;
        this.visibilityService = visibilityService;
        this.debugLoggerSupplier = debugLoggerSupplier;
        this.config = config;
        buildChannels(recipeIndex, config);
    }

    private void buildChannels(RecipeIndex recipeIndex, AppConfig config) {
        channels.clear();
        if (config.channelVanillaBook()) {
            channels.add(new VanillaRecipeBookChannel(recipeIndex));
        }
        if (config.channelPacketEvents() && isPacketEventsPresent()) {
            try {
                channels.add(new PacketRecipeChannel(plugin));
            } catch (Throwable throwable) {
                plugin.getLogger().warning("[Codex] PacketEvents channel unavailable, skipped: " + throwable.getMessage());
            }
        }
        if (config.channelJeiBridge()) {
            JeiBridgeChannel jei = new JeiBridgeChannel(plugin, debugLoggerSupplier);
            jei.register();
            channels.add(jei);
        }
    }

    /**
     * Rebuilds channels against a new config snapshot (called on reload).
     *
     * @param recipeIndex the recipe index
     * @param config      the new config snapshot
     */
    public void rebuild(RecipeIndex recipeIndex, AppConfig config) {
        shutdown();
        this.config = config;
        buildChannels(recipeIndex, config);
    }

    /**
     * Syncs a single online player. Computes their visible set and pushes it through
     * every available channel on the player's region/main thread.
     *
     * @param player the target player
     */
    public void sync(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Set<String> visible = visibilityService.visibleRecipeIds(uuid);
        FoliaSchedulerAdapter.runEntityTask(plugin, player, () -> dispatch(player, visible));
    }

    /**
     * Resyncs every online player, spreading the work across ticks to avoid a spike
     * when many players are online (e.g. after a reload full resend).
     */
    public void syncAll() {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) {
            return;
        }
        int index = 0;
        for (Player player : online) {
            long delayTicks = index / 5L; // batch: ~5 players per tick
            FoliaSchedulerAdapter.runEntityTaskLater(plugin, player, () -> sync(player), Math.max(1L, delayTicks));
            index++;
        }
    }

    private void dispatch(Player player, Set<String> visible) {
        for (RecipeSyncChannel channel : channels) {
            if (!channel.isAvailable()) {
                continue;
            }
            try {
                channel.sync(player, visible);
            } catch (Throwable throwable) {
                plugin.getLogger().warning("[Codex] Recipe channel '" + channel.id()
                        + "' failed for " + player.getName() + ": " + throwable.getMessage());
            }
        }
    }

    /** {@return the ids of channels currently active, for debug/logging} */
    public List<String> activeChannelIds() {
        List<String> ids = new ArrayList<>();
        for (RecipeSyncChannel channel : channels) {
            if (channel.isAvailable()) {
                ids.add(channel.id());
            }
        }
        return ids;
    }

    /**
     * {@return the JEI bridge channel if it is built, else {@code null}} Lets the lifecycle
     * coordinator report whether the NMS reflection bridge resolved on this platform.
     */
    public JeiBridgeChannel jeiBridgeChannel() {
        for (RecipeSyncChannel channel : channels) {
            if (channel instanceof JeiBridgeChannel jei) {
                return jei;
            }
        }
        return null;
    }

    public void shutdown() {
        for (RecipeSyncChannel channel : channels) {
            try {
                channel.shutdown();
            } catch (Throwable ignored) {
                // best-effort cleanup
            }
        }
        channels.clear();
    }

    private boolean isPacketEventsPresent() {
        return Bukkit.getPluginManager().getPlugin("packetevents") != null
                || Bukkit.getPluginManager().getPlugin("PacketEvents") != null;
    }
}
