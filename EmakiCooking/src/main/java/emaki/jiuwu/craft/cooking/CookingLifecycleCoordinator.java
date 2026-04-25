package emaki.jiuwu.craft.cooking;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.action.ActionExecutor;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapHooks;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.integration.CraftEngineBlockBridge;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.runtime.AbstractLifecycleCoordinator;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
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

final class CookingLifecycleCoordinator extends AbstractLifecycleCoordinator<EmakiCookingPlugin, CookingRuntimeComponents> {

    private static final String DEFAULT_PREFIX = "<gray>[ <gradient:#D8792E:#F6D16E>Emaki Cooking</gradient> ]</gray>";
    private static final List<String> VERSIONED_FILES = List.of("config.yml", "lang/zh_CN.yml");
    private static final List<String> EXTRA_DIRECTORIES = List.of(
            "recipes/chopping_board",
            "recipes/wok",
            "recipes/grinder",
            "recipes/steamer",
            "item_adjustments",
            "data/stations"
    );

    @Override
    public CookingRuntimeComponents initialize(EmakiCookingPlugin plugin) {
        EmakiCoreLibPlugin coreLibPlugin = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
        YamlConfigLoader<AppConfig> appConfigLoader = new YamlConfigLoader<>(
                plugin,
                "config.yml",
                "version",
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
        MessageService messageService = new MessageService(plugin, languageLoader, DEFAULT_PREFIX, false);
        BootstrapService bootstrapService = new BootstrapService(
                plugin,
                messageService,
                VERSIONED_FILES,
                staticFiles(plugin),
                defaultDataFiles(plugin),
                EXTRA_DIRECTORIES,
                List.of(),
                new BootstrapHooks() {
                    @Override
                    public boolean shouldInstallDefaultData() {
                        return shouldReleaseDefaultData(plugin);
                    }
                }
        );
        CraftEngineBlockBridge craftEngineBlockBridge = coreLibPlugin.craftEngineBlockBridge();
        CookingSettingsService settingsService = new CookingSettingsService(plugin);
        settingsService.reload();
        CookingBlockMatcher blockMatcher = new CookingBlockMatcher(settingsService, craftEngineBlockBridge);
        StationStateStore stationStateStore = new StationStateStore(plugin);
        CookingRecipeService recipeService = new CookingRecipeService(plugin, settingsService);
        ActionExecutor coreActionExecutor = coreLibPlugin.actionExecutor();
        CookingRewardService rewardService = new CookingRewardService(
                plugin,
                messageService,
                coreLibPlugin.itemSourceService(),
                coreActionExecutor,
                coreLibPlugin.itemAssemblyService()
        );
        CookingInspectService inspectService = new CookingInspectService(messageService, coreLibPlugin.itemSourceService());
        ChoppingBoardRuntimeService choppingBoardRuntimeService = new ChoppingBoardRuntimeService(
                plugin,
                messageService,
                settingsService,
                blockMatcher,
                stationStateStore,
                recipeService,
                rewardService,
                coreLibPlugin.itemSourceService()
        );
        WokRuntimeService wokRuntimeService = new WokRuntimeService(
                plugin,
                messageService,
                settingsService,
                blockMatcher,
                stationStateStore,
                recipeService,
                rewardService,
                coreLibPlugin.itemSourceService()
        );
        GrinderRuntimeService grinderRuntimeService = new GrinderRuntimeService(
                plugin,
                messageService,
                settingsService,
                blockMatcher,
                stationStateStore,
                recipeService,
                rewardService,
                coreLibPlugin.itemSourceService()
        );
        SteamerRuntimeService steamerRuntimeService = new SteamerRuntimeService(
                plugin,
                messageService,
                settingsService,
                blockMatcher,
                stationStateStore,
                recipeService,
                rewardService,
                coreLibPlugin.itemSourceService()
        );
        return new CookingRuntimeComponents(
                appConfigLoader,
                languageLoader,
                choppingBoardRecipeLoader,
                wokRecipeLoader,
                grinderRecipeLoader,
                steamerRecipeLoader,
                messageService,
                bootstrapService,
                coreActionExecutor,
                coreLibPlugin.itemSourceService(),
                craftEngineBlockBridge,
                settingsService,
                blockMatcher,
                stationStateStore,
                recipeService,
                rewardService,
                inspectService,
                choppingBoardRuntimeService,
                wokRuntimeService,
                grinderRuntimeService,
                steamerRuntimeService
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
        plugin.settingsService().reload();
        plugin.choppingBoardRuntimeService().reload();
        plugin.wokRuntimeService().reload();
        plugin.grinderRuntimeService().reload();
        plugin.steamerRuntimeService().reload();
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
        return List.copyOf(files);
    }
}
