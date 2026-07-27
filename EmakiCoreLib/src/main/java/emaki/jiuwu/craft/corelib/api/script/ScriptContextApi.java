package emaki.jiuwu.craft.corelib.api.script;

import java.util.Map;

import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.script.ScriptHostObjectProxy;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class ScriptContextApi {

    private final String phase;
    private final String plugin;
    private final Map<String, String> placeholders;
    private final Map<String, Object> attributes;
    private final Map<String, Object> arguments;

    public ScriptContextApi(ActionContext context, Map<String, Object> arguments) {
        this.phase = context == null ? "" : context.phase();
        this.plugin = context == null || context.sourcePlugin() == null ? "" : context.sourcePlugin().getName();
        this.placeholders = context == null ? Map.of() : Map.copyOf(context.placeholders());
        @SuppressWarnings("unchecked")
        Map<String, Object> safeAttributes = (Map<String, Object>) ScriptHostObjectProxy.snapshotValue(
                context == null ? Map.of() : context.attributes()
        );
        this.attributes = safeAttributes;
        @SuppressWarnings("unchecked")
        Map<String, Object> safeArguments = (Map<String, Object>) ScriptHostObjectProxy.snapshotValue(
                arguments == null ? Map.of() : arguments
        );
        this.arguments = safeArguments;
    }

    @HostAccess.Export
    public String phase() {
        return phase;
    }

    @HostAccess.Export
    public String plugin() {
        return plugin;
    }

    @HostAccess.Export
    public String placeholder(String key) {
        return Texts.toStringSafe(placeholders.get(Texts.lower(key)));
    }

    @HostAccess.Export
    public Object attribute(String key) {
        return attributes.get(key);
    }

    @HostAccess.Export
    public Object arg(String key) {
        return arguments.get(key);
    }

    @HostAccess.Export
    public Map<String, String> placeholders() {
        return placeholders;
    }

    @HostAccess.Export
    public Map<String, Object> attributes() {
        return attributes;
    }

    @HostAccess.Export
    public Map<String, Object> args() {
        return arguments;
    }
}
