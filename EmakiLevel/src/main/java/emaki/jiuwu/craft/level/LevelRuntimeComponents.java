package emaki.jiuwu.craft.level;

import emaki.jiuwu.craft.level.loader.LevelTypeLoader;
import emaki.jiuwu.craft.level.loader.RequirementLoader;
import emaki.jiuwu.craft.level.loader.SourceRuleLoader;
import emaki.jiuwu.craft.level.service.LevelMessageService;
import emaki.jiuwu.craft.level.service.LevelPdcService;
import emaki.jiuwu.craft.level.service.LevelTopService;
import emaki.jiuwu.craft.level.service.LevelTypeRegistry;
import emaki.jiuwu.craft.level.service.PlayerLevelDataStore;
import emaki.jiuwu.craft.level.service.PlayerLevelService;
import emaki.jiuwu.craft.level.service.RequirementService;

record LevelRuntimeComponents(LevelMessageService messages,
        LevelTypeLoader typeLoader,
        RequirementLoader requirementLoader,
        SourceRuleLoader sourceRuleLoader,
        LevelTypeRegistry typeRegistry,
        RequirementService requirementService,
        PlayerLevelDataStore dataStore,
        LevelPdcService pdcService,
        PlayerLevelService levelService,
        LevelTopService topService) {
}
