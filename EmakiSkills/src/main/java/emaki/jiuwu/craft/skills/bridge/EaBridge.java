package emaki.jiuwu.craft.skills.bridge;

import java.util.Map;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.integration.EmakiAttributeBridge;
import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.text.MiniMessages;

public final class EaBridge {

    private static final String ATTRIBUTE_PLUGIN_NAME = "EmakiAttribute";

    private final JavaPlugin plugin;
    private final LogMessages messages;
    private boolean available;
    private EmakiAttributeBridge bridge;
    private String providerMode;

    public EaBridge(JavaPlugin plugin, LogMessages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    public void init() {
        available = false;
        bridge = null;
        providerMode = "";

        RegisteredServiceProvider<EmakiAttributeBridge> registration = Bukkit.getServicesManager().getRegistration(EmakiAttributeBridge.class);
        if (registration == null || registration.getProvider() == null) {
            if (plugin.getServer().getPluginManager().isPluginEnabled(ATTRIBUTE_PLUGIN_NAME)) {
                info("console.ea_bridge_service_unregistered");
            } else {
                info("console.ea_bridge_unavailable");
            }
            return;
        }

        bridge = registration.getProvider();
        available = bridge.available();
        providerMode = "Bukkit ServicesManager";
        if (!available) {
            info("console.ea_bridge_service_unregistered");
            return;
        }
        info("console.ea_bridge_ready", Map.of("mode", providerMode));
    }

    public boolean isAvailable() {
        return available && bridge != null;
    }

    public String providerMode() {
        return providerMode == null ? "" : providerMode;
    }

    public double readResourceCurrent(org.bukkit.entity.Player player, String resourceId) {
        if (!isAvailable()) {
            return -1D;
        }
        try {
            return bridge.readResourceCurrent(player, resourceId);
        } catch (Exception exception) {
            warning("console.ea_bridge_read_resource_current_failed", Map.of(
                    "resource", String.valueOf(resourceId),
                    "error", errorMessage(exception)
            ), exception);
            return -1D;
        }
    }

    public double readResourceMax(org.bukkit.entity.Player player, String resourceId) {
        if (!isAvailable()) {
            return -1D;
        }
        try {
            return bridge.readResourceMax(player, resourceId);
        } catch (Exception exception) {
            warning("console.ea_bridge_read_resource_max_failed", Map.of(
                    "resource", String.valueOf(resourceId),
                    "error", errorMessage(exception)
            ), exception);
            return -1D;
        }
    }

    public boolean consumeResource(org.bukkit.entity.Player player, String resourceId, double amount) {
        if (!isAvailable()) {
            return false;
        }
        try {
            return bridge.consumeResource(player, resourceId, amount);
        } catch (Exception exception) {
            warning("console.ea_bridge_consume_failed", Map.of(
                    "resource", String.valueOf(resourceId),
                    "error", errorMessage(exception)
            ), exception);
            return false;
        }
    }

    public double readAttributeValue(org.bukkit.entity.Player player, String attributeId) {
        if (!isAvailable()) {
            return 0D;
        }
        try {
            return bridge.readAttributeValue(player, attributeId);
        } catch (Exception exception) {
            warning("console.ea_bridge_attribute_read_failed", Map.of(
                    "attribute", String.valueOf(attributeId),
                    "error", errorMessage(exception)
            ), exception);
            return 0D;
        }
    }

    public void shutdown() {
        available = false;
        bridge = null;
        providerMode = "";
    }

    private void info(String key) {
        info(key, Map.of());
    }

    private void info(String key, Map<String, ?> replacements) {
        if (messages != null) {
            messages.info(key, replacements == null ? Map.of() : replacements);
            return;
        }
        plugin.getLogger().info(key);
    }

    private void warning(String key, Map<String, ?> replacements, Throwable throwable) {
        String text = messages == null ? key : messages.message(key, replacements == null ? Map.of() : replacements);
        String plainText = messages == null ? text : MiniMessages.plain(messages.render(text));
        if (throwable == null) {
            plugin.getLogger().warning(plainText);
            return;
        }
        plugin.getLogger().log(Level.WARNING, plainText, throwable);
    }

    private String errorMessage(Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}
