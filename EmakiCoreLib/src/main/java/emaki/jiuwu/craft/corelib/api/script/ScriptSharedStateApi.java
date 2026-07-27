package emaki.jiuwu.craft.corelib.api.script;

import java.util.LinkedHashMap;
import java.util.Map;

import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.script.ScriptDeferredOperationQueue;
import emaki.jiuwu.craft.corelib.script.ScriptHostObjectProxy;

public final class ScriptSharedStateApi {

    private final ActionContext context;
    private final ScriptDeferredOperationQueue deferredOperations;
    private final Map<String, Object> snapshot;

    public ScriptSharedStateApi(ActionContext context) {
        this(context, null);
    }

    public ScriptSharedStateApi(ActionContext context, ScriptDeferredOperationQueue deferredOperations) {
        this.context = context;
        this.deferredOperations = deferredOperations;
        this.snapshot = new LinkedHashMap<>();
        if (context != null) {
            for (Map.Entry<String, Object> entry : context.sharedState().entrySet()) {
                snapshot.put(entry.getKey(), ScriptHostObjectProxy.snapshotValue(entry.getValue()));
            }
        }
    }

    @HostAccess.Export
    public void set(String key, Object value) {
        if (context == null || key == null) {
            return;
        }
        Object safeValue = ScriptHostObjectProxy.snapshotValue(value);
        if (safeValue == null) {
            snapshot.remove(key);
        } else {
            snapshot.put(key, safeValue);
        }
        mutateContext("state:set", key, safeValue);
    }

    @HostAccess.Export
    public Object get(String key) {
        return key == null ? null : ScriptHostObjectProxy.snapshotValue(snapshot.get(key));
    }

    @HostAccess.Export
    public boolean has(String key) {
        return key != null && snapshot.containsKey(key);
    }

    @HostAccess.Export
    public void remove(String key) {
        if (context == null || key == null) {
            return;
        }
        snapshot.remove(key);
        mutateContext("state:remove", key, null);
    }

    private void mutateContext(String description, String key, Object value) {
        Runnable mutation = () -> {
            if (value == null) {
                context.sharedState().remove(key);
            } else {
                context.sharedState().put(key, value);
            }
        };
        if (deferredOperations != null) {
            deferredOperations.enqueueContext(description, mutation);
        } else {
            mutation.run();
        }
    }
}
