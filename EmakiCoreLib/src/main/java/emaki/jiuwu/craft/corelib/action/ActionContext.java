package emaki.jiuwu.craft.corelib.action;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class ActionContext {

    private final Player player;
    private final String phase;
    private final boolean silent;
    private final Map<String, String> placeholders;
    private final Map<String, Object> attributes;

    private ActionContext(Player player,
            String phase,
            boolean silent,
            Map<String, String> placeholders,
            Map<String, Object> attributes) {
        this.player = player;
        this.phase = Texts.isBlank(phase) ? "default" : Texts.trim(phase);
        this.silent = silent;
        this.placeholders = placeholders == null ? Map.of() : Map.copyOf(placeholders);
        this.attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static ActionContext create(Player player, String phase, boolean silent) {
        return new ActionContext(player, phase, silent, Map.of(), Map.of());
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

    public String placeholder(String key) {
        return placeholders.get(Texts.lower(key));
    }

    public Object attribute(String key) {
        return attributes.get(key);
    }

    public ActionContext withPlaceholders(Map<String, ?> values) {
        Map<String, String> copy = new LinkedHashMap<>(placeholders);
        if (values != null) {
            for (Map.Entry<String, ?> entry : values.entrySet()) {
                copy.put(Texts.lower(entry.getKey()), Texts.toStringSafe(entry.getValue()));
            }
        }
        return new ActionContext(player, phase, silent, copy, attributes);
    }

    public ActionContext withAttribute(String key, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(attributes);
        copy.put(key, value);
        return new ActionContext(player, phase, silent, placeholders, copy);
    }
}
