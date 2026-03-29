package emaki.jiuwu.craft.corelib.action;

import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class ActionContext {

    private final Plugin sourcePlugin;
    private final Player player;
    private final String phase;
    private final boolean silent;
    private final Map<String, String> placeholders;
    private final Map<String, Object> attributes;

    public ActionContext(Plugin sourcePlugin,
                         Player player,
                         String phase,
                         boolean silent,
                         Map<String, String> placeholders,
                         Map<String, Object> attributes) {
        this.sourcePlugin = sourcePlugin;
        this.player = player;
        this.phase = Texts.isBlank(phase) ? "default" : Texts.trim(phase);
        this.silent = silent;
        this.placeholders = placeholders == null ? Map.of() : Map.copyOf(placeholders);
        this.attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static ActionContext create(Plugin sourcePlugin, Player player, String phase, boolean silent) {
        return new ActionContext(sourcePlugin, player, phase, silent, Map.of(), Map.of());
    }

    public Plugin sourcePlugin() {
        return sourcePlugin;
    }

    public Player player() {
        return player;
    }

    public String phase() {
        return phase;
    }

    public boolean silent() {
        return silent;
    }

    public Map<String, String> placeholders() {
        return placeholders;
    }

    public Map<String, Object> attributes() {
        return attributes;
    }

    public String placeholder(String key) {
        return placeholders.get(Texts.lower(key));
    }

    public Object attribute(String key) {
        return attributes.get(key);
    }

    public ActionContext withPhase(String value) {
        return new ActionContext(sourcePlugin, player, value, silent, placeholders, attributes);
    }

    public ActionContext withPlaceholder(String key, Object value) {
        Map<String, String> copy = new LinkedHashMap<>(placeholders);
        copy.put(Texts.lower(key), Texts.toStringSafe(value));
        return new ActionContext(sourcePlugin, player, phase, silent, copy, attributes);
    }

    public ActionContext withPlaceholders(Map<String, ?> values) {
        Map<String, String> copy = new LinkedHashMap<>(placeholders);
        if (values != null) {
            for (Map.Entry<String, ?> entry : values.entrySet()) {
                copy.put(Texts.lower(entry.getKey()), Texts.toStringSafe(entry.getValue()));
            }
        }
        return new ActionContext(sourcePlugin, player, phase, silent, copy, attributes);
    }

    public ActionContext withAttribute(String key, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(attributes);
        copy.put(key, value);
        return new ActionContext(sourcePlugin, player, phase, silent, placeholders, copy);
    }

    public ActionContext withAttributes(Map<String, ?> values) {
        Map<String, Object> copy = new LinkedHashMap<>(attributes);
        if (values != null) {
            copy.putAll(values);
        }
        return new ActionContext(sourcePlugin, player, phase, silent, placeholders, copy);
    }
}
