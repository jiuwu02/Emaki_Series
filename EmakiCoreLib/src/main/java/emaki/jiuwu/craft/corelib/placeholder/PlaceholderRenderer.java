package emaki.jiuwu.craft.corelib.placeholder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import me.clip.placeholderapi.PlaceholderAPI;

public final class PlaceholderRenderer {

    public static final String DEBUG_VARIABLES = "variables";
    public static final String DEBUG_PLACEHOLDER = "placeholder";

    private static final Pattern PERCENT_PLACEHOLDER = Pattern.compile("%([^%\\s]+)%");

    private PlaceholderRenderer() {
    }

    public static Map<String, Object> contextVariables(ActionContext context) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (context == null) {
            return values;
        }
        putAllNormalized(values, context.placeholders());
        putPlayerDefaults(values, context.player());
        values.putIfAbsent("phase", context.phase());
        return values;
    }

    public static Map<String, Object> playerVariables(Player player) {
        Map<String, Object> values = new LinkedHashMap<>();
        putPlayerDefaults(values, player);
        return values;
    }

    public static Map<String, Object> mergeContextVariables(ActionContext context, Map<String, ?> variables) {
        Map<String, Object> values = contextVariables(context);
        putAllNormalized(values, variables);
        return values;
    }

    public static Map<String, Object> normalizeVariables(Map<String, ?> variables) {
        Map<String, Object> values = new LinkedHashMap<>();
        putAllNormalized(values, variables);
        return values;
    }

    public static String renderInternal(String text, Map<String, ?> variables) {
        return renderInternal(text, variables, null, null, "template");
    }

    public static String renderInternal(String text,
            Map<String, ?> variables,
            DebugLogger debugLogger,
            Player player,
            String source) {
        if (Texts.isBlank(text) || text.indexOf('%') < 0) {
            return text;
        }
        Map<String, Object> values = normalizeVariables(variables);
        if (values.isEmpty()) {
            debugMissingPlaceholders(text, debugLogger, player, source, List.of(), placeholdersIn(text));
            return text;
        }
        Matcher matcher = PERCENT_PLACEHOLDER.matcher(text);
        StringBuffer buffer = new StringBuffer();
        List<String> hits = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        while (matcher.find()) {
            String rawKey = matcher.group(1);
            String key = Texts.lower(rawKey);
            boolean hasRawKey = values.containsKey(rawKey);
            boolean hasNormalizedKey = values.containsKey(key);
            Object value = hasRawKey ? values.get(rawKey) : values.get(key);
            if (!hasRawKey && !hasNormalizedKey) {
                missing.add(rawKey);
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            hits.add(rawKey);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(Texts.toStringSafe(value)));
        }
        matcher.appendTail(buffer);
        debugMissingPlaceholders(text, debugLogger, player, source, hits, missing);
        return buffer.toString();
    }

    public static String render(ActionContext context,
            String text,
            DebugLogger debugLogger,
            String source) {
        return render(context, text, Map.of(), debugLogger, source);
    }

    public static String render(ActionContext context,
            String text,
            Map<String, ?> variables,
            DebugLogger debugLogger,
            String source) {
        Map<String, Object> merged = mergeContextVariables(context, variables);
        Player player = context == null ? null : context.player();
        debugVariables(merged, debugLogger, player, source);
        String internal = renderInternal(text, merged, debugLogger, player, source);
        return renderPapi(player, internal, debugLogger, source);
    }

    public static String renderPapi(Player player,
            String text,
            DebugLogger debugLogger,
            String source) {
        if (player == null || Texts.isBlank(text) || text.indexOf('%') < 0) {
            return text;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return text;
        }
        try {
            String resolved = PlaceholderAPI.setPlaceholders(player, text);
            if (debugLogger != null && debugLogger.shouldLog(DEBUG_PLACEHOLDER, player) && !Texts.toStringSafe(text).equals(resolved)) {
                debugLogger.log(DEBUG_PLACEHOLDER, player, "common.placeholder.papi_resolved", Map.of(
                        "source", Texts.toStringSafe(source),
                        "before", Texts.toStringSafe(text),
                        "after", Texts.toStringSafe(resolved)
                ));
            }
            return resolved;
        } catch (Exception exception) {
            if (debugLogger != null) {
                debugLogger.log(DEBUG_PLACEHOLDER, player, "common.placeholder.papi_failed", Map.of(
                        "source", Texts.toStringSafe(source),
                        "error", Texts.toStringSafe(exception.getMessage()),
                        "text", Texts.toStringSafe(text)
                ));
            }
            return text;
        }
    }

    public static void debugVariables(Map<String, ?> variables,
            DebugLogger debugLogger,
            Player player,
            String source) {
        if (debugLogger == null || !debugLogger.shouldLog(DEBUG_VARIABLES, player)) {
            return;
        }
        debugLogger.log(DEBUG_VARIABLES, player, "common.placeholder.variables", Map.of(
                "source", Texts.toStringSafe(source),
                "values", variables == null ? Map.of() : variables
        ));
    }

    private static void putPlayerDefaults(Map<String, Object> values, Player player) {
        if (values == null || player == null) {
            return;
        }
        values.putIfAbsent("player", player.getName());
        values.putIfAbsent("player_name", player.getName());
        values.putIfAbsent("player_uuid", player.getUniqueId().toString());
        values.putIfAbsent("player_world", player.getWorld() == null ? "" : player.getWorld().getName());
        values.putIfAbsent("player_x", Double.toString(player.getLocation().getX()));
        values.putIfAbsent("player_y", Double.toString(player.getLocation().getY()));
        values.putIfAbsent("player_z", Double.toString(player.getLocation().getZ()));
    }

    private static void putAllNormalized(Map<String, Object> target, Map<String, ?> source) {
        if (target == null || source == null || source.isEmpty()) {
            return;
        }
        for (Map.Entry<String, ?> entry : source.entrySet()) {
            if (Texts.isBlank(entry.getKey())) {
                continue;
            }
            target.put(Texts.lower(entry.getKey()), entry.getValue());
        }
    }

    private static List<String> placeholdersIn(String text) {
        if (Texts.isBlank(text) || text.indexOf('%') < 0) {
            return List.of();
        }
        Matcher matcher = PERCENT_PLACEHOLDER.matcher(text);
        List<String> result = new ArrayList<>();
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    private static void debugMissingPlaceholders(String original,
            DebugLogger debugLogger,
            Player player,
            String source,
            List<String> hits,
            List<String> missing) {
        if (debugLogger == null || !debugLogger.shouldLog(DEBUG_PLACEHOLDER, player)) {
            return;
        }
        if ((hits == null || hits.isEmpty()) && (missing == null || missing.isEmpty())) {
            return;
        }
        debugLogger.log(DEBUG_PLACEHOLDER, player, "common.placeholder.internal_resolved", Map.of(
                "source", Texts.toStringSafe(source),
                "hits", hits == null ? List.of() : hits,
                "missing", missing == null ? List.of() : missing,
                "text", Texts.toStringSafe(original)
        ));
    }
}
