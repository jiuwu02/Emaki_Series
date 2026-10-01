package emaki.jiuwu.craft.item.script;

import org.graalvm.polyglot.Value;

public final class ItemScriptBinding {

    private final ItemScriptBridge bridge;

    ItemScriptBinding(ItemScriptBridge bridge) {
        this.bridge = bridge;
    }

    public void registerEffectType(Value payload) {
        bridge.registerEffectType(payload);
    }
}
