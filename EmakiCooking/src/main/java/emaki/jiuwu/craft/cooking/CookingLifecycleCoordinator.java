package emaki.jiuwu.craft.cooking;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.action.ActionExecutor;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.assembly.EmakiNamespaceDefinition;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapHooks;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckReport;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.api.integration.CraftEngineBlockBridge;
import emaki.jiuwu.craft.corelib.api.integration.CustomBlockBridge;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.runtime.AbstractLifecycleCoordinator;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import emaki.jiuwu.craft.cooking.config.AppConfig;
import emaki.jiuwu.craft.cooking.loader.ChoppingBoardRecipeLoader;
import emaki.jiuwu.craft.cooking.loader.FermentationBarrelRecipeLoader;
import emaki.jiuwu.craft.cooking.loader.GrinderRecipeLoader;
import emaki.jiuwu.craft.cooking.loader.JuicerRecipeLoader;
import emaki.jiuwu.craft.cooking.loader.NutritionTypeLoader;
import emaki.jiuwu.craft.cooking.loader.OvenRecipeLoader;
import emaki.jiuwu.craft.cooking.loader.SteamerRecipeLoader;
import emaki.jiuwu.craft.cooking.loader.WokRecipeLoader;
import emaki.jiuwu.craft.cooking.service.ChoppingBoardRuntimeService;
import emaki.jiuwu.craft.cooking.service.CookingBlockMatcher;
import emaki.jiuwu.craft.cooking.service.CookingCompletionCoordinator;
import emaki.jiuwu.craft.cooking.service.CookingInspectService;
import emaki.jiuwu.craft.cooking.service.CookingRecipeService;
import emaki.jiuwu.craft.cooking.service.CookingRewardService;
import emaki.jiuwu.craft.cooking.service.CookingSettingsService;
import emaki.jiuwu.craft.cooking.service.FermentationBarrelRuntimeService;
import emaki.jiuwu.craft.cooking.service.GrinderRuntimeService;
import emaki.jiuwu.craft.cooking.service.JuicerRuntimeService;
import emaki.jiuwu.craft.cooking.service.NutritionService;
import emaki.jiuwu.craft.cooking.service.NutritionTypeRegistry;
import emaki.jiuwu.craft.cooking.service.OvenRuntimeService;
import emaki.jiuwu.craft.cooking.service.PlayerNutritionDataStore;
import emaki.jiuwu.craft.cooking.service.StationStateStore;
import emaki.jiuwu.craft.cooking.service.SteamerRuntimeService;
import emaki.jiuwu.craft.cooking.service.WokRuntimeService;
import emaki.jiuwu.craft.cooking.service.display.CookingDisplayService;
import emaki.jiuwu.craft.cooking.service.display.CookingDisplayServiceFactory;
import emaki.jiuwu.craft.cooking.service.display.CookingTextDisplayService;
import emaki.jiuwu.craft.cooking.service.display.CookingTextDisplayServiceFactory;

final class CookingLifecycleCoordinator extends AbstractLifecycleCoordinator<EmakiCookingPlugin, CookingRuntimeComponents> {

    private static final String DEFAULT_PREFIX = "<gray>[ <gradient:#22C55E:#FACC15>EmakiCooking</gradient> ]</gray>";
    private static final List<String> VERSIONED_FILES = List.of("config.yml", "lang/zh_CN.yml", "lang/en_US.yml");
    private static final List<String> EXTRA_DIRECTORIES = List.of(
            "recipes/chopping_board",
            "recipes/wok",
            "recipes/grinder",
            "recipes/steamer",
            "recipes/oven",
            "recipes/juicer",
            "recipes/fermentation_barrel",
            "item_adjustments",
            "data/stations",
            "data/stations/index",
            "data/stations-legacy-backup",
            "nutrition",
            "data/nutrition"
    );

    @Override
    public CookingRuntimeComponents initialize(EmakiCookingPlugin plugin) {
        EmakiCoreLibPlugin coreLibPlugin = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
        ExecutionDispatcher executionDispatcher = coreLibPlugin.executionDispatcher();
        ThreadOwnership threadOwnership = coreLibPlugin.threadOwnership();
        registerAssemblyLayer(coreLibPlugin);
        YamlConfigLoader<AppConfig> appConfigLoader = new YamlConfigLoader<>(
                plugin,
                "config.yml",
                AppConfig::defaults,
                this::parseAppConfig
        );
        appConfigLoader.load();
        LanguageLoader languageLoader = new LanguageLoader(plugin, "lang", "lang", "zh_CN", "zh_CN");
        languageLoader.load();
        ChoppingBoardRecipeLoader choppingBoardRecipeLoader = new ChoppingBoardRecipeLoader(plugin);
        WokRecipeLoader wokRecipeLoader = new WokRecipeLoader(plugin);
        GrinderRecipeLoader grinderRecipeLoader = new GrinderRecipeLoader(plugin);
        SteamerRecipeLoader steamerRecipeLoader = new SteamerRecipeLoader(plugin);
        OvenRecipeLoader ovenRecipeLoader = new OvenRecipeLoader(plugin);
        JuicerRecipeLoader juicerRecipeLoader = new JuicerRecipeLoader(plugin);
        FermentationBarrelRecipeLoader fermentationBarrelRecipeLoader = new FermentationBarrelRecipeLoader(plugin);
        MessageService messageService = new MessageService(plugin, languageLoader, DEFAULT_PREFIX, true);
        BootstrapService bootstrapService = new BootstrapService(
                plugin,
                messageService,
                VERSIONED_FILES,
                staticFiles(plugin),
                defaultDataFiles(plugin),
                EXTRA_DIRECTORIES,
                new BootstrapHooks() {
                    @Override
                    public boolean shouldInstallDefaultData() {
                        return shouldReleaseDefaultData(plugin);
                    }
                }
        );
        CraftEngineBlockBridge craftEngineBlockBridge = coreLibPlugin.craftEngineBlockBridge();
        CustomBlockBridge itemsAdderBlockBridge = coreLibPlugin.itemsAdderBlockBridge();
        CustomBlockBridge nexoBlockBridge = coreLibPlugin.nexoBlockBridge();
        CustomBlockBridge oraxenBlockBridge = coreLibPlugin.oraxenBlockBridge();
        CookingSettingsService settingsService = new CookingSettingsService(plugin);
        settingsService.reload();
        CookingBlockMatcher blockMatcher = new CookingBlockMatcher(settingsService, craftEngineBlockBridge, itemsAdderBlockBridge, nexoBlockBridge, oraxenBlockBridge);
        var cookingFileScope = coreLibPlugin.asyncFileScope(plugin);
        StationStateStore stationStateStore = new StationStateStore(
                plugin, cookingFileScope, executionDispatcher, threadOwnership);
        CookingRecipeService recipeService = new CookingRecipeService(plugin, settingsService);
        ActionExecutor coreActionExecutor = coreLibPlugin.actionExecutor();
        CookingRewardService rewardService = new CookingRewardService(
                plugin,
                messageService,
                coreLibPlugin.itemSourceService(),
                coreActionExecutor,
                coreLibPlugin.itemAssemblyService(),
                executionDispatcher,
                threadOwnership
        );
        rewardService.setRecipeService(recipeService);
        CookingCompletionCoordinator completionCoordinator = new CookingCompletionCoordinator(
                plugin, rewardService, cookingFileScope, executionDispatcher);
        CookingInspectService inspectService = new CookingInspectService(messageService, coreLibPlugin.itemSourceService(), stationStateStore, blockMatcher, settingsService);
        CookingDisplayService displayService = CookingDisplayServiceFactory.create(
                plugin, settingsService, executionDispatcher);
        CookingTextDisplayService textDisplayService = CookingTextDisplayServiceFactory.create(
                plugin, settingsService, executionDispatcher);
        ChoppingBoardRuntimeService choppingBoardRuntimeService = new ChoppingBoardRuntimeService(
                plugin,
                messageService,
                settingsService,
                blockMatcher,
                stationStateStore,
                recipeService,
                rewardService,
                coreLibPlugin.itemSourceService(),
                displayService,
                textDisplayService
        );
        WokRuntimeService wokRuntimeService = new WokRuntimeService(
                plugin,
                messageService,
                settingsService,
                blockMatcher,
                stationStateStore,
                recipeService,
                rewardService,
                coreLibPlugin.itemSourceService(),
                displayService,
                textDisplayService
        );
        GrinderRuntimeService grinderRuntimeService = new GrinderRuntimeService(
                plugin,
                messageService,
                settingsService,
                blockMatcher,
                stationStateStore,
                recipeService,
                rewardService,
                coreLibPlugin.itemSourceService(),
                textDisplayService,
                executionDispatcher
        );
        SteamerRuntimeService steamerRuntimeService = new SteamerRuntimeService(
                plugin,
                messageService,
                settingsService,
                blockMatcher,
                stationStateStore,
                recipeService,
                rewardService,
                coreLibPlugin.itemSourceService(),
                textDisplayService,
                executionDispatcher
        );
        OvenRuntimeService ovenRuntimeService = new OvenRuntimeService(
                plugin,
                messageService,
                settingsService,
                blockMatcher,
                stationStateStore,
                recipeService,
                rewardService,
                coreLibPlugin.itemSourceService(),
                textDisplayService,
                executionDispatcher
        );
        JuicerRuntimeService juicerRuntimeService = new JuicerRuntimeService(
                plugin,
                messageService,
                settingsService,
                blockMatcher,
                stationStateStore,
                recipeService,
                rewardService,
                coreLibPlugin.itemSourceService(),
                textDisplayService
        );
        FermentationBarrelRuntimeService fermentationBarrelRuntimeService = new FermentationBarrelRuntimeService(
                plugin,
                messageService,
                settingsService,
                blockMatcher,
                stationStateStore,
                recipeService,
                rewardService,
                completionCoordinator,
                coreLibPlugin.itemSourceService(),
                textDisplayService,
                executionDispatcher
        );
        choppingBoardRuntimeService.setCompletionCoordinator(completionCoordinator);
        wokRuntimeService.setCompletionCoordinator(completionCoordinator);
        grinderRuntimeService.setCompletionCoordinator(completionCoordinator);
        steamerRuntimeService.setCompletionCoordinator(completionCoordinator);
        ovenRuntimeService.setCompletionCoordinator(completionCoordinator);
        juicerRuntimeService.setCompletionCoordinator(completionCoordinator);
        NutritionTypeLoader nutritionTypeLoader = new NutritionTypeLoader(plugin);
        nutritionTypeLoader.load();
        NutritionTypeRegistry nutritionTypeRegistry = new NutritionTypeRegistry();
        nutritionTypeRegistry.reload(nutritionTypeLoader.types());
        var nutritionDataFiles = coreLibPlugin.asyncYamlFiles(plugin);
        PlayerNutritionDataStore nutritionDataStore = new PlayerNutritionDataStore(plugin, () -> nutritionDataFiles);
        NutritionService nutritionService = new NutritionService(
                plugin,
                coreActionExecutor,
                coreLibPlugin.itemSourceService(),
                settingsService,
                nutritionTypeRegistry,
                nutritionDataStore,
                executionDispatcher,
                threadOwnership
        );
        return new CookingRuntimeComponents(
                executionDispatcher,
                threadOwnership,
                appConfigLoader,
                languageLoader,
                choppingBoardRecipeLoader,
                wokRecipeLoader,
                grinderRecipeLoader,
                steamerRecipeLoader,
                ovenRecipeLoader,
                juicerRecipeLoader,
                fermentationBarrelRecipeLoader,
                messageService,
                bootstrapService,
                coreActionExecutor,
                coreLibPlugin.itemSourceService(),
                craftEngineBlockBridge,
                itemsAdderBlockBridge,
                nexoBlockBridge,
                oraxenBlockBridge,
                settingsService,
                blockMatcher,
                stationStateStore,
                recipeService,
                rewardService,
                completionCoordinator,
                inspectService,
                displayService,
                textDisplayService,
                choppingBoardRuntimeService,
                wokRuntimeService,
                grinderRuntimeService,
                steamerRuntimeService,
                ovenRuntimeService,
                juicerRuntimeService,
                fermentationBarrelRuntimeService,
                nutritionTypeLoader,
                nutritionTypeRegistry,
                nutritionDataStore,
                nutritionService
        );
    }

    public void reload(EmakiCookingPlugin plugin) {
        plugin.languageLoader().load();
        plugin.appConfigLoader().load();
        plugin.languageLoader().setLanguage(plugin.appConfig().language());
        plugin.choppingBoardRecipeLoader().load();
        plugin.wokRecipeLoader().load();
        plugin.grinderRecipeLoader().load();
        plugin.steamerRecipeLoader().load();
        plugin.ovenRecipeLoader().load();
        plugin.juicerRecipeLoader().load();
        plugin.fermentationBarrelRecipeLoader().load();
        plugin.nutritionTypeLoader().load();
        plugin.settingsService().reload();
        plugin.nutritionTypeRegistry().reload(plugin.nutritionTypeLoader().types());
        plugin.nutritionService().reload();
        plugin.choppingBoardRuntimeService().reload();
        plugin.wokRuntimeService().reload();
        plugin.grinderRuntimeService().reload();
        plugin.steamerRuntimeService().reload();
        plugin.ovenRuntimeService().reload();
        plugin.juicerRuntimeService().reload();
        plugin.fermentationBarrelRuntimeService().reload();
        logStationRecipeCounts(plugin);
    }

    public CompletableFuture<Void> reloadAsync(EmakiCookingPlugin plugin, Consumer<String> progressListener) {
        AsyncTaskScheduler scheduler = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class).asyncTaskScheduler();
        return runReloadPipelineAsync(scheduler, plugin.executionDispatcher(), plugin, new ReloadPipelineConfig<Void, Void>(
                "cooking",
                "config-load",
                "Loading configs...",
                () -> {
                    plugin.languageLoader().load();
                    plugin.appConfigLoader().load();
                    plugin.choppingBoardRecipeLoader().load();
                    plugin.wokRecipeLoader().load();
                    plugin.grinderRecipeLoader().load();
                    plugin.steamerRecipeLoader().load();
                    plugin.ovenRecipeLoader().load();
                    plugin.juicerRecipeLoader().load();
                    plugin.fermentationBarrelRecipeLoader().load();
                    plugin.nutritionTypeLoader().load();
                    ConfigPrecheckReport report = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class)
                            .configPrecheckService()
                            .checkModule(JavaPlugin.getPlugin(EmakiCoreLibPlugin.class).configModel(), "cooking");
                    if (!report.success()) {
                        throw new IllegalStateException("Cooking precheck failed: "
                                + String.join("; ", report.formatLines(plugin.messageService(), "cooking")));
                    }
                    return null;
                },
                "apply",
                "Applying configuration...",
                _ -> {
                    plugin.languageLoader().setLanguage(plugin.appConfig().language());
                    plugin.settingsService().reload();
                    plugin.nutritionTypeRegistry().reload(plugin.nutritionTypeLoader().types());
                    plugin.nutritionService().reload();
                    plugin.choppingBoardRuntimeService().reload();
                    plugin.wokRuntimeService().reload();
                    plugin.grinderRuntimeService().reload();
                    plugin.steamerRuntimeService().reload();
                    plugin.ovenRuntimeService().reload();
                    plugin.juicerRuntimeService().reload();
                    plugin.fermentationBarrelRuntimeService().reload();
                    logStationRecipeCounts(plugin);
                    notifyProgress(progressListener, "Reload complete.");
                    return null;
                },
                null,
                null,
                null,
                (stage, ex) -> plugin.getLogger().warning("[Reload] Stage " + stage + " failed: " + ex.getMessage()),
                progressListener
        ));
    }

    private void logStationRecipeCounts(EmakiCookingPlugin plugin) {
        MessageService ms = plugin.messageService();
        logStationCount(ms, ms.message("console.station_name.chopping_board"), plugin.choppingBoardRecipeLoader().all().size());
        logStationCount(ms, ms.message("console.station_name.wok"), plugin.wokRecipeLoader().all().size());
        logStationCount(ms, ms.message("console.station_name.grinder"), plugin.grinderRecipeLoader().all().size());
        logStationCount(ms, ms.message("console.station_name.steamer"), plugin.steamerRecipeLoader().all().size());
        logStationCount(ms, ms.message("console.station_name.oven"), plugin.ovenRecipeLoader().all().size());
        logStationCount(ms, ms.message("console.station_name.juicer"), plugin.juicerRecipeLoader().all().size());
        logStationCount(ms, ms.message("console.station_name.fermentation_barrel"), plugin.fermentationBarrelRecipeLoader().all().size());
    }

    private void logStationCount(MessageService ms, String station, int count) {
        ms.info("console.station_recipes_loaded", Map.of("station", station, "count", String.valueOf(count)));
    }

    private AppConfig parseAppConfig(YamlSection configuration) {
        if (configuration == null || configuration.isEmpty()) {
            return AppConfig.defaults();
        }
        AppConfig defaults = AppConfig.defaults();
        return new AppConfig(
                configuration.getString("language", defaults.language()),
                configuration.getString("version", defaults.configVersion()),
                configuration.getBoolean("release_default_data", defaults.releaseDefaultData())
        );
    }

    private boolean shouldReleaseDefaultData(EmakiCookingPlugin plugin) {
        YamlSection configuration = YamlFiles.load(plugin.dataPath("config.yml").toFile());
        return configuration.getBoolean("release_default_data", true);
    }

    private List<String> staticFiles(EmakiCookingPlugin plugin) {
        List<String> files = new ArrayList<>();
        files.addAll(YamlFiles.listResourcePaths(plugin, "gui"));
        return List.copyOf(files);
    }

    private List<String> defaultDataFiles(EmakiCookingPlugin plugin) {
        List<String> files = new ArrayList<>(YamlFiles.listResourcePaths(plugin, "recipes"));
        files.addAll(YamlFiles.listResourcePaths(plugin, "item_adjustments"));
        files.addAll(YamlFiles.listResourcePaths(plugin, "nutrition"));
        return List.copyOf(files);
    }

    private void registerAssemblyLayer(EmakiCoreLibPlugin coreLibPlugin) {
        coreLibPlugin.namespaceRegistry().register(new EmakiNamespaceDefinition("cooking", 10000, "Cooking"));
    }

}
