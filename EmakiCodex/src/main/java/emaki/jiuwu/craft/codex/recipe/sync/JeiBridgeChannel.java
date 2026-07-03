package emaki.jiuwu.craft.codex.recipe.sync;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;

import emaki.jiuwu.craft.codex.recipe.sync.nms.NmsRecipeBridge;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;

/**
 * Channel 2 (rewritten): the real server-side JEI/REI bridge for Minecraft 1.21.2+.
 *
 * <p>Since 1.21.2 recipes are server-side only, so a standard JEI/REI client no longer
 * receives the full recipe table over the vanilla protocol. It rebuilds its recipe list
 * from the mod loader's recipe-sync custom payload instead. This channel reproduces exactly
 * that payload — via {@link NmsRecipeBridge} reflecting the server's Mojang-mapped NMS
 * {@code RecipeHolder.STREAM_CODEC} — and dispatches it on the correct channel per the
 * player's client brand:
 * <ul>
 *   <li>Fabric client → {@code fabric:recipe_sync}</li>
 *   <li>NeoForge client → {@code neoforge:recipe_content} (plus a tag-update packet)</li>
 * </ul>
 *
 * <p>This mirrors the approach of the JEIRecipeBridge plugin, the difference being that
 * EmakiCodex reflects NMS at runtime (compile-time it depends on spigot-api only) so it
 * fits the existing Maven build without paperweight. The channel reports itself unavailable
 * when the reflection bridge cannot resolve, so the gateway silently degrades to the other
 * channels.
 *
 * <p>All work runs on the player's region/main thread (the gateway schedules it): both the
 * recipe-manager read and the packet send must happen there.
 */
public final class JeiBridgeChannel implements RecipeSyncChannel {

    private static final String FABRIC_CHANNEL = "fabric:recipe_sync";
    private static final String NEOFORGE_CHANNEL = "neoforge:recipe_content";
    private static final String DEBUG_MODULE = "sync";

    private final JavaPlugin plugin;
    private final Supplier<DebugLogger> debugLoggerSupplier;
    private final NmsRecipeBridge bridge;
    private volatile boolean registered;

    /**
     * @param plugin              the owning plugin (used for messenger + logger)
     * @param debugLoggerSupplier lazy accessor for the debug logger; resolved at send time
     *                            because the logger is wired after this channel is built
     */
    public JeiBridgeChannel(JavaPlugin plugin, Supplier<DebugLogger> debugLoggerSupplier) {
        this.plugin = plugin;
        this.debugLoggerSupplier = debugLoggerSupplier;
        this.bridge = new NmsRecipeBridge();
    }

    /** Registers the Fabric/NeoForge outgoing plugin-message channels; call once during build. */
    public void register() {
        if (registered) {
            return;
        }
        Messenger messenger = plugin.getServer().getMessenger();
        registerOutgoing(messenger, FABRIC_CHANNEL);
        registerOutgoing(messenger, NEOFORGE_CHANNEL);
        registered = true;
    }

    /** {@return whether the NMS reflection bridge resolved and can encode payloads} */
    public boolean bridgeAvailable() {
        return bridge.isAvailable();
    }

    /** {@return a short reason the bridge is unavailable, or {@code null} when available} */
    public String bridgeUnavailableReason() {
        return bridge.unavailableReason();
    }

    private void registerOutgoing(Messenger messenger, String channel) {
        if (!messenger.isOutgoingChannelRegistered(plugin, channel)) {
            messenger.registerOutgoingPluginChannel(plugin, channel);
        }
    }

    @Override
    public String id() {
        return "jei_bridge";
    }

    @Override
    public boolean isAvailable() {
        return registered && bridge.isAvailable();
    }

    @Override
    public void sync(Player player, Set<String> visibleRecipeIds) {
        if (player == null || visibleRecipeIds == null || !isAvailable()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        String brand = clientBrand(player);
        debug(uuid, "[DEBUG:JEI:1] entry player=" + player.getName() + " brand=" + brand
                + " visible=" + visibleRecipeIds.size());

        String channel = channelForBrand(brand);
        if (channel == null) {
            debug(uuid, "[DEBUG:JEI:2] skip: unsupported/unknown brand '" + brand
                    + "' (JEI 需 fabric/neoforge 客户端)");
            return;
        }

        try {
            List<Object> holders = bridge.collectHolders(visibleRecipeIds);
            debug(uuid, "[DEBUG:JEI:3] collected holders=" + holders.size()
                    + " (matched against " + visibleRecipeIds.size() + " visible ids)");
            if (holders.isEmpty()) {
                debug(uuid, "[DEBUG:JEI:4] skip: no matching recipe holders");
                return;
            }

            NmsRecipeBridge.EncodedPayload payload;
            if (NEOFORGE_CHANNEL.equals(channel)) {
                payload = bridge.encodeNeoForge(holders);
            } else {
                payload = bridge.encodeFabric(holders);
            }
            debug(uuid, "[DEBUG:JEI:5] encoded channel=" + channel + " recipes=" + payload.recipeCount()
                    + " bytes=" + payload.bytes().length);

            player.sendPluginMessage(plugin, channel, payload.bytes());
            if (NEOFORGE_CHANNEL.equals(channel)) {
                bridge.sendNeoForgeTagUpdate(player);
            }
            debug(uuid, "[DEBUG:JEI:6] sent ok channel=" + channel + " recipes=" + payload.recipeCount());
        } catch (Throwable throwable) {
            debug(uuid, "[DEBUG:JEI:E] encode/send failed: " + throwable.getClass().getSimpleName()
                    + ": " + throwable.getMessage());
            plugin.getLogger().warning("[Codex] JEI 桥接发送失败 for " + player.getName() + " ("
                    + channel + "): " + throwable.getMessage());
        }
    }

    private String channelForBrand(String brand) {
        if (brand == null) {
            return null;
        }
        String normalized = brand.toLowerCase(Locale.ROOT);
        if (normalized.contains("fabric") || normalized.contains("quilt")) {
            return FABRIC_CHANNEL;
        }
        if (normalized.contains("neoforge")) {
            return NEOFORGE_CHANNEL;
        }
        return null;
    }

    /**
     * Reads the player's self-reported client brand ({@code "fabric"}, {@code "neoforge"}, ...).
     * Uses reflection because {@code Player#getClientBrandName()} is a Paper API method not
     * present in spigot-api at compile time. Returns {@code null} if unavailable.
     */
    private String clientBrand(Player player) {
        try {
            Object value = player.getClass().getMethod("getClientBrandName").invoke(player);
            return value == null ? null : value.toString();
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private void debug(UUID uuid, String message) {
        DebugLogger debugLogger = debugLoggerSupplier == null ? null : debugLoggerSupplier.get();
        if (debugLogger != null && debugLogger.shouldLog(DEBUG_MODULE, uuid)) {
            debugLogger.logRaw(DEBUG_MODULE, uuid, message);
        }
    }

    @Override
    public void shutdown() {
        if (!registered) {
            return;
        }
        Messenger messenger = plugin.getServer().getMessenger();
        messenger.unregisterOutgoingPluginChannel(plugin, FABRIC_CHANNEL);
        messenger.unregisterOutgoingPluginChannel(plugin, NEOFORGE_CHANNEL);
        registered = false;
    }
}
