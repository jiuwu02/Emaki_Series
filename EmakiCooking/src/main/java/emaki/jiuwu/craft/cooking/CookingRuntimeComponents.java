package emaki.jiuwu.craft.cooking;

import java.util.Map;

import emaki.jiuwu.craft.corelib.action.ActionExecutor;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.api.integration.CraftEngineBlockBridge;
import emaki.jiuwu.craft.corelib.api.integration.CustomBlockBridge;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.runtime.RuntimeComponents;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.cooking.config.AppConfig;
import emaki.jiuwu.craft.cooking.loader.ChoppingBoardRecipeLoader;
import emaki.jiuwu.craft.cooking.loader.FermentationBarrelRecipeLoader;
import emaki.jiuwu.craft.cooking.loader.GrinderRecipeLoader;
import emaki.jiuwu.craft.cooking.loader.JuicerRecipeLoader;
import emaki.jiuwu.craft.cooking.loader.OvenRecipeLoader;
import emaki.jiuwu.craft.cooking.loader.SteamerRecipeLoader;
import emaki.jiuwu.craft.cooking.loader.WokRecipeLoader;
import emaki.jiuwu.craft.cooking.service.ChoppingBoardRuntimeService;
import emaki.jiuwu.craft.cooking.service.CookingBlockMatcher;
import emaki.jiuwu.craft.cooking.service.CookingInspectService;
import emaki.jiuwu.craft.cooking.service.CookingRecipeService;
import emaki.jiuwu.craft.cooking.service.CookingRewardService;
import emaki.jiuwu.craft.cooking.service.CookingSettingsService;
import emaki.jiuwu.craft.cooking.service.FermentationBarrelRuntimeService;
import emaki.jiuwu.craft.cooking.service.GrinderRuntimeService;
import emaki.jiuwu.craft.cooking.service.JuicerRuntimeService;
import emaki.jiuwu.craft.cooking.service.OvenRuntimeService;
import emaki.jiuwu.craft.cooking.service.StationStateStore;
import emaki.jiuwu.craft.cooking.service.SteamerRuntimeService;
import emaki.jiuwu.craft.cooking.service.WokRuntimeService;
import emaki.jiuwu.craft.cooking.service.display.CookingDisplayService;

record CookingRuntimeComponents(YamlConfigLoader<AppConfig> appConfigLoader,
        LanguageLoader languageLoader,
        ChoppingBoardRecipeLoader choppingBoardRecipeLoader,
        WokRecipeLoader wokRecipeLoader,
        GrinderRecipeLoader grinderRecipeLoader,
        SteamerRecipeLoader steamerRecipeLoader,
        OvenRecipeLoader ovenRecipeLoader,
        JuicerRecipeLoader juicerRecipeLoader,
        FermentationBarrelRecipeLoader fermentationBarrelRecipeLoader,
        MessageService messageService,
        BootstrapService bootstrapService,
        ActionExecutor coreActionExecutor,
        ItemSourceService coreItemSourceService,
        CraftEngineBlockBridge craftEngineBlockBridge,
        CustomBlockBridge itemsAdderBlockBridge,
        CustomBlockBridge nexoBlockBridge,
        CookingSettingsService settingsService,
        CookingBlockMatcher blockMatcher,
        StationStateStore stationStateStore,
        CookingRecipeService recipeService,
        CookingRewardService rewardService,
        CookingInspectService inspectService,
        CookingDisplayService displayService,
        ChoppingBoardRuntimeService choppingBoardRuntimeService,
        WokRuntimeService wokRuntimeService,
        GrinderRuntimeService grinderRuntimeService,
        SteamerRuntimeService steamerRuntimeService,
        OvenRuntimeService ovenRuntimeService,
        JuicerRuntimeService juicerRuntimeService,
        FermentationBarrelRuntimeService fermentationBarrelRuntimeService) implements RuntimeComponents {

    @Override
    public Map<Class<?>, Object> services() {
        return RuntimeComponents.services(
                RuntimeComponents.component(YamlConfigLoader.class, appConfigLoader),
                RuntimeComponents.component(LanguageLoader.class, languageLoader),
                RuntimeComponents.component(ChoppingBoardRecipeLoader.class, choppingBoardRecipeLoader),
                RuntimeComponents.component(WokRecipeLoader.class, wokRecipeLoader),
                RuntimeComponents.component(GrinderRecipeLoader.class, grinderRecipeLoader),
                RuntimeComponents.component(SteamerRecipeLoader.class, steamerRecipeLoader),
                RuntimeComponents.component(OvenRecipeLoader.class, ovenRecipeLoader),
                RuntimeComponents.component(JuicerRecipeLoader.class, juicerRecipeLoader),
                RuntimeComponents.component(FermentationBarrelRecipeLoader.class, fermentationBarrelRecipeLoader),
                RuntimeComponents.component(MessageService.class, messageService),
                RuntimeComponents.component(BootstrapService.class, bootstrapService),
                RuntimeComponents.component(ActionExecutor.class, coreActionExecutor),
                RuntimeComponents.component(ItemSourceService.class, coreItemSourceService),
                RuntimeComponents.component(CraftEngineBlockBridge.class, craftEngineBlockBridge),
                RuntimeComponents.component(CustomBlockBridge.class, itemsAdderBlockBridge),
                RuntimeComponents.component(CookingSettingsService.class, settingsService),
                RuntimeComponents.component(CookingBlockMatcher.class, blockMatcher),
                RuntimeComponents.component(StationStateStore.class, stationStateStore),
                RuntimeComponents.component(CookingRecipeService.class, recipeService),
                RuntimeComponents.component(CookingRewardService.class, rewardService),
                RuntimeComponents.component(CookingInspectService.class, inspectService),
                RuntimeComponents.component(CookingDisplayService.class, displayService),
                RuntimeComponents.component(ChoppingBoardRuntimeService.class, choppingBoardRuntimeService),
                RuntimeComponents.component(WokRuntimeService.class, wokRuntimeService),
                RuntimeComponents.component(GrinderRuntimeService.class, grinderRuntimeService),
                RuntimeComponents.component(SteamerRuntimeService.class, steamerRuntimeService),
                RuntimeComponents.component(OvenRuntimeService.class, ovenRuntimeService),
                RuntimeComponents.component(JuicerRuntimeService.class, juicerRuntimeService),
                RuntimeComponents.component(FermentationBarrelRuntimeService.class, fermentationBarrelRuntimeService)
        );
    }
}
