package emaki.jiuwu.craft.cooking;

import emaki.jiuwu.craft.corelib.action.ActionExecutor;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.integration.CraftEngineBlockBridge;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.runtime.RuntimeComponents;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.cooking.config.AppConfig;
import emaki.jiuwu.craft.cooking.loader.ChoppingBoardRecipeLoader;
import emaki.jiuwu.craft.cooking.loader.GrinderRecipeLoader;
import emaki.jiuwu.craft.cooking.loader.SteamerRecipeLoader;
import emaki.jiuwu.craft.cooking.loader.WokRecipeLoader;
import emaki.jiuwu.craft.cooking.service.ChoppingBoardRuntimeService;
import emaki.jiuwu.craft.cooking.service.CookingBlockMatcher;
import emaki.jiuwu.craft.cooking.service.CookingInspectService;
import emaki.jiuwu.craft.cooking.service.CookingRecipeService;
import emaki.jiuwu.craft.cooking.service.CookingRewardService;
import emaki.jiuwu.craft.cooking.service.CookingSettingsService;
import emaki.jiuwu.craft.cooking.service.GrinderRuntimeService;
import emaki.jiuwu.craft.cooking.service.StationStateStore;
import emaki.jiuwu.craft.cooking.service.SteamerRuntimeService;
import emaki.jiuwu.craft.cooking.service.WokRuntimeService;

record CookingRuntimeComponents(YamlConfigLoader<AppConfig> appConfigLoader,
        LanguageLoader languageLoader,
        ChoppingBoardRecipeLoader choppingBoardRecipeLoader,
        WokRecipeLoader wokRecipeLoader,
        GrinderRecipeLoader grinderRecipeLoader,
        SteamerRecipeLoader steamerRecipeLoader,
        MessageService messageService,
        BootstrapService bootstrapService,
        ActionExecutor coreActionExecutor,
        ItemSourceService coreItemSourceService,
        CraftEngineBlockBridge craftEngineBlockBridge,
        CookingSettingsService settingsService,
        CookingBlockMatcher blockMatcher,
        StationStateStore stationStateStore,
        CookingRecipeService recipeService,
        CookingRewardService rewardService,
        CookingInspectService inspectService,
        ChoppingBoardRuntimeService choppingBoardRuntimeService,
        WokRuntimeService wokRuntimeService,
        GrinderRuntimeService grinderRuntimeService,
        SteamerRuntimeService steamerRuntimeService) implements RuntimeComponents {
}
