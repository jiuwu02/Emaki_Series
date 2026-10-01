package emaki.jiuwu.craft.codex.script;

import org.graalvm.polyglot.Value;

import emaki.jiuwu.craft.corelib.script.host.ScriptHost;

public final class TriggerScriptBinding {

    private final TriggerScriptBridge bridge;
    private ScriptHost host;

    TriggerScriptBinding(TriggerScriptBridge bridge) {
        this.bridge = bridge;
    }

    void bindHost(ScriptHost host) {
        this.host = host;
    }

    public void register(Value payload) {
        bridge.registerScriptTrigger(payload, host);
    }
}
