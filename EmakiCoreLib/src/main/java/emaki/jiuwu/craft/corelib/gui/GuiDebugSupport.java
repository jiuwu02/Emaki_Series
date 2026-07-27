package emaki.jiuwu.craft.corelib.gui;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.debug.DebugLoggerProvider;

public final class GuiDebugSupport {

    private static final String MODULE = "gui";

    private GuiDebugSupport() {
    }

    public static void log(Plugin plugin, Player player, String langKey) {
        log(plugin, player, langKey, Map.of());
    }

    public static void log(Plugin plugin, Player player, String langKey, Map<String, ?> replacements) {
        if (!(plugin instanceof DebugLoggerProvider provider)) {
            return;
        }
        DebugLogger logger = provider.debugLogger();
        if (logger != null) {
            logger.log(MODULE, player, langKey, replacements);
        }
    }

    public static Map<String, Object> replacements(Object... entries) {
        if (entries == null || entries.length == 0) {
            return new LinkedHashMap<>();
        }
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("entries must contain key-value pairs");
        }
        Map<String, Object> replacements = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            replacements.put(String.valueOf(entries[index]), entries[index + 1]);
        }
        return replacements;
    }

    public static Map<String, Object> sessionFields(GuiSession session) {
        return sessionFields(session, Map.of());
    }

    public static Map<String, Object> sessionFields(GuiSession session, Map<String, ?> fields) {
        Map<String, Object> replacements = copy(fields);
        replacements.put("session_id", session == null
                ? ""
                : Integer.toHexString(System.identityHashCode(session)));
        replacements.put("session_owner", session == null || session.owner() == null
                ? ""
                : session.owner().getName());
        replacements.put("session_backend", session == null || session.backend() == null
                ? ""
                : session.backend().name());
        replacements.put("session_title", session == null || session.plainTitle() == null
                ? ""
                : session.plainTitle());
        return replacements;
    }

    public static Map<String, Object> windowFields(int windowId,
            int stateId,
            int topSize,
            GuiSession session,
            Map<String, ?> fields) {
        Map<String, Object> replacements = sessionFields(session, fields);
        replacements.put("window_id", windowId);
        replacements.put("window_state_id", stateId);
        replacements.put("window_top_size", topSize);
        return replacements;
    }

    public static String itemType(ItemStack itemStack) {
        Material material = itemStack == null ? Material.AIR : itemStack.getType();
        return material.getKey().toString();
    }

    public static int itemAmount(ItemStack itemStack) {
        return itemStack == null || itemStack.getType().isAir() ? 0 : itemStack.getAmount();
    }

    public static Map<String, Object> errorFields(Throwable throwable) {
        return errorFields(throwable, Map.of());
    }

    public static Map<String, Object> errorFields(Throwable throwable, Map<String, ?> fields) {
        Map<String, Object> replacements = copy(fields);
        replacements.put("error_type", throwable == null ? "" : throwable.getClass().getSimpleName());
        replacements.put("error_message", throwable == null || throwable.getMessage() == null
                ? ""
                : throwable.getMessage());
        return replacements;
    }

    private static Map<String, Object> copy(Map<String, ?> fields) {
        Map<String, Object> replacements = new LinkedHashMap<>();
        if (fields != null) {
            replacements.putAll(fields);
        }
        return replacements;
    }
}
