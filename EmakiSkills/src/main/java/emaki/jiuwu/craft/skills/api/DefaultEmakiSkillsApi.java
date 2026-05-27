package emaki.jiuwu.craft.skills.api;

public final class DefaultEmakiSkillsApi implements EmakiSkillsApi {

    private final SkillScriptActionRegistry scriptActionRegistry;

    public DefaultEmakiSkillsApi(SkillScriptActionRegistry scriptActionRegistry) {
        this.scriptActionRegistry = scriptActionRegistry;
    }

    @Override
    public SkillScriptActionRegistry scriptActionRegistry() {
        return scriptActionRegistry;
    }
}
