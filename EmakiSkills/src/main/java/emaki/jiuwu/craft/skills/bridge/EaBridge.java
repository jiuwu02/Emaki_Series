package emaki.jiuwu.craft.skills.bridge;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.skills.integration.SkillsAttributeBridge;

public final class EaBridge {

    private static final String ATTRIBUTE_PLUGIN_NAME = "EmakiAttribute";
    private static final String BRIDGE_CLASS =
            "emaki.jiuwu.craft.skills.integration.attribute.EmakiAttributeSkillsBridge";

    private final JavaPlugin plugin;
    private final LogMessages messages;
    private volatile SkillsAttributeBridge bridge = SkillsAttributeBridge.UNAVAILABLE;
    private volatile String providerMode = "";

    public EaBridge(JavaPlugin plugin, LogMessages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    public void init() {
        bridge = SkillsAttributeBridge.UNAVAILABLE;
        providerMode = "";

        if (!plugin.getServer().getPluginManager().isPluginEnabled(ATTRIBUTE_PLUGIN_NAME)) {
            info("console.ea_bridge_unavailable");
            return;
        }
        SkillsAttributeBridge resolved = load();
        if (!resolved.available()) {
            info("console.ea_bridge_service_unregistered");
            return;
        }
        bridge = resolved;
        providerMode = "EmakiAttributeApi";
        info("console.ea_bridge_ready", Map.of("mode", providerMode));
    }

    public boolean isAvailable() {
        return resolve().available();
    }

    public String providerMode() {
        return providerMode == null ? "" : providerMode;
    }

    public double readResourceCurrent(Player player, String resourceId) {
        SkillsAttributeBridge resolved = resolve();
        if (!resolved.available()) {
            return -1D;
        }
        try {
            return resolved.readResourceCurrent(player, resourceId);
        } catch (Exception exception) {
            warning("console.ea_bridge_read_resource_current_failed", Map.of(
                    "resource", String.valueOf(resourceId),
                    "error", errorMessage(exception)
            ), exception);
            return -1D;
        }
    }

    public double readResourceMax(Player player, String resourceId) {
        SkillsAttributeBridge resolved = resolve();
        if (!resolved.available()) {
            return -1D;
        }
        try {
            return resolved.readResourceMax(player, resourceId);
        } catch (Exception exception) {
            warning("console.ea_bridge_read_resource_max_failed", Map.of(
                    "resource", String.valueOf(resourceId),
                    "error", errorMessage(exception)
            ), exception);
            return -1D;
        }
    }

    public boolean consumeResource(Player player, String resourceId, double amount) {
        SkillsAttributeBridge resolved = resolve();
        if (!resolved.available()) {
            return false;
        }
        try {
            return resolved.consumeResource(player, resourceId, amount);
        } catch (Exception exception) {
            warning("console.ea_bridge_consume_failed", Map.of(
                    "resource", String.valueOf(resourceId),
                    "error", errorMessage(exception)
            ), exception);
            return false;
        }
    }

    public double readAttributeValue(Player player, String attributeId) {
        SkillsAttributeBridge resolved = resolve();
        if (!resolved.available()) {
            return 0D;
        }
        try {
            return resolved.readAttributeValue(player, attributeId);
        } catch (Exception exception) {
            warning("console.ea_bridge_attribute_read_failed", Map.of(
                    "attribute", String.valueOf(attributeId),
                    "error", errorMessage(exception)
            ), exception);
            return 0D;
        }
    }

    public boolean applyDamage(LivingEntity attacker, LivingEntity target, String damageTypeId, double baseDamage, Map<String, Object> context) {
        SkillsAttributeBridge resolved = resolve();
        if (!resolved.available()) {
            return false;
        }
        try {
            return resolved.applyDamage(attacker, target, damageTypeId, baseDamage, context);
        } catch (Exception exception) {
            warning("console.ea_bridge_apply_damage_failed", Map.of(
                    "damage_type", String.valueOf(damageTypeId),
                    "error", errorMessage(exception)
            ), exception);
            return false;
        }
    }

    public boolean isItemContributionActive(Player player, ItemStack itemStack, String slotName) {
        SkillsAttributeBridge resolved = resolve();
        if (!resolved.available()) {
            return true;
        }
        try {
            return resolved.isItemContributionActive(player, itemStack, slotName);
        } catch (Exception exception) {
            return true;
        }
    }

    public void shutdown() {
        bridge = SkillsAttributeBridge.UNAVAILABLE;
        providerMode = "";
    }

    private SkillsAttributeBridge resolve() {
        boolean enabled = Bukkit.getPluginManager().isPluginEnabled(ATTRIBUTE_PLUGIN_NAME);
        SkillsAttributeBridge current = bridge;
        if (!enabled) {
            if (current != SkillsAttributeBridge.UNAVAILABLE) {
                bridge = SkillsAttributeBridge.UNAVAILABLE;
                providerMode = "";
            }
            return SkillsAttributeBridge.UNAVAILABLE;
        }
        if (current != SkillsAttributeBridge.UNAVAILABLE) {
            return current;
        }
        SkillsAttributeBridge resolved = load();
        if (resolved.available()) {
            bridge = resolved;
            providerMode = "EmakiAttributeApi";
        }
        return resolved;
    }

    private SkillsAttributeBridge load() {
        try {
            Class<?> bridgeClass = Class.forName(BRIDGE_CLASS, true, getClass().getClassLoader());
            Object created = bridgeClass.getMethod("create").invoke(null);
            return created instanceof SkillsAttributeBridge resolved
                    ? resolved
                    : SkillsAttributeBridge.UNAVAILABLE;
        } catch (ReflectiveOperationException | LinkageError exception) {
            warning("console.ea_bridge_init_failed", Map.of(
                    "error", errorMessage(exception)
            ), exception);
            return SkillsAttributeBridge.UNAVAILABLE;
        }
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
        String plainText = messages == null ? text : MiniMessages.plainText(text);
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
        Throwable resolved = throwable instanceof InvocationTargetException invocation
                && invocation.getCause() != null
                ? invocation.getCause()
                : throwable;
        String message = resolved.getMessage();
        return message == null || message.isBlank() ? resolved.getClass().getSimpleName() : message;
    }
}
