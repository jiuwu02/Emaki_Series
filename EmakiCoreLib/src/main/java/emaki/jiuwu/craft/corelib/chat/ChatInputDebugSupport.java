package emaki.jiuwu.craft.corelib.chat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.debug.DebugLoggerProvider;
import emaki.jiuwu.craft.corelib.api.chat.ChatInputRequest;

public final class ChatInputDebugSupport {

    private static final String MODULE = "chat_input";

    private ChatInputDebugSupport() {
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

    /** 请求字段：调度归属插件、超时秒数与取消词数量。 */
    public static Map<String, Object> requestFields(ChatInputRequest request) {
        return requestFields(request, Map.of());
    }

    public static Map<String, Object> requestFields(ChatInputRequest request, Map<String, ?> fields) {
        Map<String, Object> replacements = copy(fields);
        replacements.put("request_owner", request == null || request.owner() == null
                ? ""
                : request.owner().getName());
        replacements.put("request_timeout_seconds", request == null ? 0L : request.timeoutSeconds());
        replacements.put("request_cancel_keywords", request == null ? 0 : request.cancelKeywords().size());
        return replacements;
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
