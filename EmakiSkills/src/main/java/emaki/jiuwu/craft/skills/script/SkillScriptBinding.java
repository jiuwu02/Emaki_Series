package emaki.jiuwu.craft.skills.script;

import org.graalvm.polyglot.Value;

public final class SkillScriptBinding {

    private final SkillScriptBridge bridge;

    public SkillScriptBinding(SkillScriptBridge bridge) {
        this.bridge = bridge;
    }

    public void register(Value payload) {
        bridge.register(payload);
    }
}
