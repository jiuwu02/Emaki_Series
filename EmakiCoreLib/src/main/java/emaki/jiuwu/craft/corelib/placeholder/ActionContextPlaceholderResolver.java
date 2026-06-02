package emaki.jiuwu.craft.corelib.placeholder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class ActionContextPlaceholderResolver implements PlaceholderResolver {

    private static final Pattern PERCENT_PLACEHOLDER = Pattern.compile("%([a-zA-Z0-9_]+)%");
    private static final Pattern BRACED_PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_]+)\\}");

    @Override
    public String resolve(ActionContext context, String text) {
        if (context == null || Texts.isBlank(text)) {
            return text;
        }
        Map<String, String> values = new LinkedHashMap<>(context.placeholders());
        Player player = context.player();
        if (player != null) {
            values.putIfAbsent("player_name", player.getName());
            values.putIfAbsent("player_uuid", player.getUniqueId().toString());
            values.putIfAbsent("player_world", player.getWorld().getName());
            values.putIfAbsent("player_x", String.valueOf(player.getLocation().getX()));
            values.putIfAbsent("player_y", String.valueOf(player.getLocation().getY()));
            values.putIfAbsent("player_z", String.valueOf(player.getLocation().getZ()));
        }
        values.putIfAbsent("phase", context.phase());
        String resolved = replace(values, text, PERCENT_PLACEHOLDER);
        return replace(values, resolved, BRACED_PLACEHOLDER);
    }

    private String replace(Map<String, String> values, String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String key = Texts.lower(matcher.group(1));
            String replacement = values.getOrDefault(key, matcher.group(0));
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
