package emaki.jiuwu.craft.skills.api;

import emaki.jiuwu.craft.skills.script.SkillScriptCastService;

public final class DefaultEmakiSkillsApi implements EmakiSkillsApi {

    private final SkillScriptActionRegistry scriptActionRegistry;
    private final SkillScriptCastService scriptCastService;

    public DefaultEmakiSkillsApi(SkillScriptActionRegistry scriptActionRegistry,
            SkillScriptCastService scriptCastService) {
        this.scriptActionRegistry = scriptActionRegistry;
        this.scriptCastService = scriptCastService;
    }

    @Override
    public SkillScriptActionRegistry scriptActionRegistry() {
        return scriptActionRegistry;
    }

    @Override
    public SkillScriptCastService scriptCastService() {
        return scriptCastService;
    }
}
