package emaki.jiuwu.craft.codex.recipe.sync;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;

/**
 * Channel 2: an experimental JEI/REI plugin-message channel over Bukkit's
 * {@link Messenger} ({@code jei:network} / {@code rei:networking}). Disabled by default.
 *
 * <p><b>Important — does not fix standard JEI on 1.21.2+.</b> Since Minecraft 1.21.2,
 * recipes are stored server-side only and are no longer fully synced to the client
 * (clients receive {@code RecipeDisplayEntry} data via Fabric's {@code fabric-recipe-api-v1}
 * custom payload, not a whole-table download). A standard, unmodified JEI/REI client does
 * <i>not</i> listen on {@code jei:network} to rebuild its recipe list, so the simple
 * length-prefixed frame written here has no effect on such clients — they will still show
 * an empty recipe list and prompt to "install JEI on the server".
 *
 * <p>To make standard JEI display recipes on a 1.21.2+ Spigot server you need either a
 * dedicated server-side JEI bridge plugin (e.g. JEI Recipe Bridge, which emits the
 * Fabric/NeoForge-expected payload) or a client-side fallback mod (e.g. JESR). This channel
 * exists only so an operator running a custom/modified client can test and tune that
 * client's own private protocol frame via {@link #encode(Set)}.
 */
public final class JeiMessageChannel implements RecipeSyncChannel {

    private static final String JEI_CHANNEL = "jei:network";
    private static final String REI_CHANNEL = "rei:networking";

    private final JavaPlugin plugin;
    private volatile boolean registered;

    public JeiMessageChannel(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Registers the outgoing plugin-message channels; call once during enable. */
    public void register() {
        if (registered) {
            return;
        }
        Messenger messenger = plugin.getServer().getMessenger();
        registerOutgoing(messenger, JEI_CHANNEL);
        registerOutgoing(messenger, REI_CHANNEL);
        registered = true;
    }

    private void registerOutgoing(Messenger messenger, String channel) {
        if (!messenger.isOutgoingChannelRegistered(plugin, channel)) {
            messenger.registerOutgoingPluginChannel(plugin, channel);
        }
    }

    @Override
    public String id() {
        return "jei_message";
    }

    @Override
    public boolean isAvailable() {
        return registered;
    }

    @Override
    public void sync(Player player, Set<String> visibleRecipeIds) {
        if (player == null || visibleRecipeIds == null || !registered) {
            return;
        }
        byte[] payload = encode(visibleRecipeIds);
        if (payload.length == 0) {
            return;
        }
        sendSafely(player, JEI_CHANNEL, payload);
        sendSafely(player, REI_CHANNEL, payload);
    }

    private void sendSafely(Player player, String channel, byte[] payload) {
        try {
            player.sendPluginMessage(plugin, channel, payload);
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.FINE,
                    "[Codex] JEI/REI message send skipped on " + channel + ": " + throwable.getMessage());
        }
    }

    /**
     * Encodes the visible recipe ids into a simple length-prefixed frame:
     * {@code [int count][UTF recipeId]...}. Adjust to match the target JEI/REI wire
     * format when tuning against a live client.
     *
     * @param visibleRecipeIds the recipe ids to advertise
     * @return the encoded payload, or an empty array on failure
     */
    private byte[] encode(Set<String> visibleRecipeIds) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeInt(visibleRecipeIds.size());
            for (String recipeId : visibleRecipeIds) {
                byte[] bytes = recipeId.getBytes(StandardCharsets.UTF_8);
                out.writeShort(bytes.length);
                out.write(bytes);
            }
            out.flush();
            return buffer.toByteArray();
        } catch (IOException exception) {
            plugin.getLogger().log(Level.FINE, "[Codex] JEI/REI frame encode failed: " + exception.getMessage());
            return new byte[0];
        }
    }

    @Override
    public void shutdown() {
        if (!registered) {
            return;
        }
        Messenger messenger = plugin.getServer().getMessenger();
        messenger.unregisterOutgoingPluginChannel(plugin, JEI_CHANNEL);
        messenger.unregisterOutgoingPluginChannel(plugin, REI_CHANNEL);
        registered = false;
    }
}
