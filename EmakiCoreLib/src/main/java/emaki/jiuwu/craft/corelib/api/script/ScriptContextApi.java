package emaki.jiuwu.craft.corelib.api.script;

import java.util.Map;

import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class ScriptContextApi {

    private final ActionContext context;
    private final Map<String, Object> arguments;

    public ScriptContextApi(ActionContext context, Map<String, Object> arguments) {
        this.context = context;
        this.arguments = arguments == null ? Map.of() : arguments;
    }

    @HostAccess.Export
    public String phase() {
        return context == null ? "" : context.phase();
    }

    @HostAccess.Export
    public String plugin() {
        return context == null || context.sourcePlugin() == null ? "" : context.sourcePlugin().getName();
    }

    @HostAccess.Export
    public String placeholder(String key) {
        return context == null ? "" : Texts.toStringSafe(context.placeholder(key));
    }

    @HostAccess.Export
    public Object attribute(String key) {
        return context == null ? null : context.attribute(key);
    }

    @HostAccess.Export
    public Object arg(String key) {
        return arguments.get(key);
    }

    @HostAccess.Export
    public Map<String, String> placeholders() {
        return context == null ? Map.of() : context.placeholders();
    }

    @HostAccess.Export
    public Map<String, Object> attributes() {
        return context == null ? Map.of() : context.attributes();
    }

    @HostAccess.Export
    public Map<String, Object> args() {
        return arguments;
    }
}
