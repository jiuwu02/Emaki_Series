package emaki.jiuwu.craft.skills.api;

import emaki.jiuwu.craft.skills.script.SkillScriptCastService;

public interface EmakiSkillsApi {

    SkillScriptActionRegistry scriptActionRegistry();

    SkillScriptCastService scriptCastService();
}
