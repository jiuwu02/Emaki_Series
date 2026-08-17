package emaki.jiuwu.craft.corelib.placeholder;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import me.clip.placeholderapi.PlaceholderAPI;

/**
 * 玩家上下文占位符解析工具类。统一处理 PlaceholderAPI + 玩家内置变量。
 * <p>
 * 供 Cooking、Station、Codex、Strengthen 等模块复用，替代各模块内部的 resolvePlaceholders 方法。
 */
public final class PlayerPlaceholders {

    private PlayerPlaceholders() {
    }

    /**
     * 解析文本中的占位符。依次应用：
     * <ol>
     *   <li>内置玩家变量（{player_name}、{player_level}、{player_health} 等）</li>
     *   <li>PlaceholderAPI（%xxx% 格式，当 PlaceholderAPI 插件存在时）</li>
     * </ol>
     *
     * @param player 玩家，null 时仅处理 PAPI（无玩家变量）
     * @param text   待解析文本，null 或空白时原样返回
     * @return 解析后的文本
     */
    public static String resolve(@Nullable Player player, @Nullable String text) {
        return resolve(player, text, null);
    }

    /**
     * 解析文本中的占位符。依次应用：
     * <ol>
     *   <li>自定义变量（{key} 格式）</li>
     *   <li>内置玩家变量（{player_name}、{player_level}、{player_health} 等）</li>
     *   <li>PlaceholderAPI（%xxx% 格式，当 PlaceholderAPI 插件存在时）</li>
     * </ol>
     *
     * @param player            玩家，null 时仅处理 PAPI（无玩家变量）
     * @param text              待解析文本，null 或空白时原样返回
     * @param customVariables   自定义变量 Map，null 时跳过
     * @return 解析后的文本
     */
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

    /**
     * 获取玩家的内置变量 Map。包含：
     * <ul>
     *   <li>player_name: 玩家名</li>
     *   <li>player_uuid: 玩家 UUID</li>
     *   <li>player_level: 玩家等级</li>
     *   <li>player_exp: 玩家经验百分比</li>
     *   <li>player_food: 玩家饥饿值</li>
     *   <li>player_health: 玩家生命值</li>
     *   <li>player_max_health: 玩家最大生命值</li>
     *   <li>player_world: 玩家所在世界名</li>
     * </ul>
     *
     * @param player 玩家，null 时返回空 Map
     * @return 玩家变量 Map
     */
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
