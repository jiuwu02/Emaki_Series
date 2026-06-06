package emaki.jiuwu.craft.skills.script;

import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.skills.api.EmakiSkillsApi;
import emaki.jiuwu.craft.skills.api.SkillScriptActionRegistry;

public final class ScriptSkillsModuleApi {

    @HostAccess.Export
    public boolean available() {
        return EmakiSkillsApi.available();
    }

    @HostAccess.Export
    public boolean hasScriptAction(String actionId) {
        SkillScriptActionRegistry registry = EmakiSkillsApi.scriptActionRegistry();
        return registry != null && registry.get(actionId) != null;
    }

    @HostAccess.Export
    public java.util.List<String> registeredScriptActions() {
        SkillScriptActionRegistry registry = EmakiSkillsApi.scriptActionRegistry();
        return registry == null ? java.util.List.of() : java.util.List.copyOf(registry.all().keySet());
    }
}
