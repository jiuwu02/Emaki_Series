package emaki.jiuwu.craft.corelib.script.api;

import java.util.Map;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class ScriptContextApi {

    private final ActionContext context;
    private final Map<String, Object> arguments;

    public ScriptContextApi(ActionContext context, Map<String, Object> arguments) {
        this.context = context;
        this.arguments = arguments == null ? Map.of() : arguments;
    }

    public String phase() {
        return context == null ? "" : context.phase();
    }

    public String plugin() {
        return context == null || context.sourcePlugin() == null ? "" : context.sourcePlugin().getName();
    }

    public String placeholder(String key) {
        return context == null ? "" : Texts.toStringSafe(context.placeholder(key));
    }

    public Object attribute(String key) {
        return context == null ? null : context.attribute(key);
    }

    public Object arg(String key) {
        return arguments.get(key);
    }

    public Map<String, String> placeholders() {
        return context == null ? Map.of() : context.placeholders();
    }

    public Map<String, Object> attributes() {
        return context == null ? Map.of() : context.attributes();
    }

    public Map<String, Object> args() {
        return arguments;
    }
}
