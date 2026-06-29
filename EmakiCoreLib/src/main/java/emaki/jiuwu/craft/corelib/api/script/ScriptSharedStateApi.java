package emaki.jiuwu.craft.corelib.api.script;

import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.corelib.action.ActionContext;

public final class ScriptSharedStateApi {

    private final ActionContext context;

    public ScriptSharedStateApi(ActionContext context) {
        this.context = context;
    }

    @HostAccess.Export
    public void set(String key, Object value) {
        if (context != null && key != null) {
            context.sharedState().put(key, value);
        }
    }

    @HostAccess.Export
    public Object get(String key) {
        return context == null || key == null ? null : context.sharedValue(key);
    }

    @HostAccess.Export
    public boolean has(String key) {
        return context != null && key != null && context.sharedState().containsKey(key);
    }

    @HostAccess.Export
    public void remove(String key) {
        if (context != null && key != null) {
            context.sharedState().remove(key);
        }
    }
}
