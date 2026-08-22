package emaki.jiuwu.craft.corelib.placeholder;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import me.clip.placeholderapi.PlaceholderAPI;

public final class PlayerPlaceholders {

    private PlayerPlaceholders() {
    }

    public static String resolve(@Nullable Player player, @Nullable String text) {
        return resolve(player, text, null);
    }

    public static String resolve(@Nullable Player player, @Nullable String text, @Nullable Map<String, String> customVariables) {
        if (Texts.isBlank(text)) {
            return text;
        }
        String resolved = text;
        if (resolved.indexOf('{') >= 0) {
            Map<String, String> variables = new LinkedHashMap<>();
            if (customVariables != null && !customVariables.isEmpty()) {
                variables.putAll(customVariables);
            }
            if (player != null) {
                putPlayerVariables(variables, player);
            }
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                resolved = resolved.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        if (resolved.indexOf('%') >= 0 && player != null && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                resolved = Texts.toStringSafe(PlaceholderAPI.setPlaceholders(player, resolved));
            } catch (Exception | NoClassDefFoundError _) {
            }
        }
        return resolved;
    }

    public static Map<String, String> playerVariables(@Nullable Player player) {
        Map<String, String> variables = new LinkedHashMap<>();
        if (player != null) {
            putPlayerVariables(variables, player);
        }
        return variables;
    }

    private static void putPlayerVariables(Map<String, String> target, Player player) {
        target.put("player_name", player.getName());
        target.put("player_uuid", player.getUniqueId().toString());
        target.put("player_level", Integer.toString(player.getLevel()));
        target.put("player_exp", Float.toString(player.getExp()));
        target.put("player_food", Integer.toString(player.getFoodLevel()));
        target.put("player_health", Double.toString(player.getHealth()));
        target.put("player_max_health", Double.toString(player.getMaxHealth()));
        target.put("player_world", player.getWorld() == null ? "" : player.getWorld().getName());
    }
}
