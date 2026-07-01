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
 * Channel 2: JEI/REI plugin-message delivery over Bukkit's {@link Messenger}, using
 * the same outgoing channels that the JEIServerProxy project validated
 * ({@code jei:network} / {@code rei:networking}). This is disabled by default because
 * the on-wire frame layout is a private JEI/REI protocol that can only be confirmed
 * against a real client; enabling it lets a server operator test and tune the frame.
 *
 * <p>The frame written here sends the visible recipe id list as a length-prefixed
 * UTF block. Operators running a client that expects a different layout should adjust
 * {@link #encode(Set)} to match their JEI/REI version.
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
