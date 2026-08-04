package emaki.jiuwu.craft.station;

import java.util.List;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapHooks;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.runtime.AbstractLifecycleCoordinator;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.AsyncYamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.station.config.AppConfig;
import emaki.jiuwu.craft.station.config.AppConfigParser;
import emaki.jiuwu.craft.station.definition.StationLoader;
import emaki.jiuwu.craft.station.gui.ConfiguredGuiSupport;
import emaki.jiuwu.craft.station.gui.StationGuiService;
import emaki.jiuwu.craft.station.material.BackpackChannel;
import emaki.jiuwu.craft.station.material.OutputDelivery;
import emaki.jiuwu.craft.station.material.StationCapabilities;
import emaki.jiuwu.craft.station.material.StorageChannel;
import emaki.jiuwu.craft.station.queue.QueueService;
import emaki.jiuwu.craft.station.queue.QueueStore;
import emaki.jiuwu.craft.station.queue.QueueTicker;
import emaki.jiuwu.craft.station.queue.StationCraftService;
import emaki.jiuwu.craft.station.recipe.RecipeLoader;

/**
 * Builds the runtime component graph in dependency order.
 *
 * <p>Capabilities are probed exactly once here and the result is handed to the warehouse channel as an
 * immutable value. Probing at construction time rather than per call is what keeps the gate cheap and keeps a
 * mid-session capability change from producing two different answers inside one operation.
 */
final class StationLifecycleCoordinator
        extends AbstractLifecycleCoordinator<EmakiStationPlugin, StationRuntimeComponents> {

    private static final String DEFAULT_PREFIX = "<gray>[<aqua>EmakiStation</aqua>]</gray> ";
    private static final List<String> VERSIONED_FILES = List.of("config.yml");
    private static final List<String> STATIC_FILES = List.of();
    private static final List<String> DEFAULT_DATA_FILES =
            List.of("stations/blacksmith.yml", "gui/grid_3x3.yml", "recipes/example_recipe.yml");
    private static final List<String> EXTRA_DIRECTORIES =
            List.of("stations", "gui", "recipes", "data");

    @Override
    public StationRuntimeComponents initialize(EmakiStationPlugin plugin) {
        EmakiCoreLibPlugin coreLibPlugin = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
        YamlConfigLoader<AppConfig> appConfigLoader = new YamlConfigLoader<>(plugin,
                "config.yml", AppConfig::defaults, AppConfigParser::parse);
        appConfigLoader.load();
        AppConfig config = appConfigLoader.current();
        LanguageLoader languageLoader = new LanguageLoader(plugin, "lang", "lang", "zh_CN", "zh_CN");
        MessageService messageService = new MessageService(plugin, languageLoader, DEFAULT_PREFIX, true);
        languageLoader.load();
        BootstrapService bootstrapService = new BootstrapService(plugin,
                messageService,
                VERSIONED_FILES,
                STATIC_FILES,
                DEFAULT_DATA_FILES,
                EXTRA_DIRECTORIES,
                new BootstrapHooks() {
                    @Override
                    public boolean shouldInstallDefaultData() {
                        AppConfig current = appConfigLoader.current();
                        return current == null || current.releaseDefaultData();
                    }
                });

        ExecutionDispatcher executionDispatcher = coreLibPlugin.executionDispatcher();
        ThreadOwnership threadOwnership = coreLibPlugin.threadOwnership();
        GuiService guiService = new GuiService(plugin, executionDispatcher,
                coreLibPlugin.asyncTaskScheduler(), coreLibPlugin.performanceMonitor(),
                coreLibPlugin.guiBackend());
        GuiTemplateLoader layoutLoader = new GuiTemplateLoader(plugin);
        StationLoader stationLoader = new StationLoader(plugin, config.queueSettings());
        RecipeLoader recipeLoader = new RecipeLoader(plugin, config.limitSettings().warnMaterialTypes());

        StationCapabilities capabilities = StationCapabilities.probe();
        BackpackChannel backpackChannel = new BackpackChannel(coreLibPlugin.itemSourceService());
        StorageChannel storageChannel = new StorageChannel(coreLibPlugin.itemSourceService(),
                capabilities, config.storageSettings());
        OutputDelivery outputDelivery =
                new OutputDelivery(coreLibPlugin.itemSourceService(), storageChannel);

        AsyncYamlFiles queueFiles = coreLibPlugin.asyncYamlFiles(plugin);
        QueueStore queueStore = new QueueStore(plugin, () -> queueFiles);
        QueueService queueService = new QueueService(queueStore);
        StationCraftService craftService = new StationCraftService(plugin,
                executionDispatcher,
                queueService,
                backpackChannel,
                storageChannel,
                outputDelivery,
                plugin::registry,
                () -> plugin.appConfig().limitSettings().maxPendingClaim(),
                () -> plugin.appConfig().persistenceSettings().saveOnSubmit());
        ConfiguredGuiSupport guiSupport =
                new ConfiguredGuiSupport(() -> layoutLoader, guiService::configuredItemService);
        StationGuiService stationGuiService = new StationGuiService(plugin,
                guiService,
                threadOwnership,
                () -> layoutLoader,
                plugin::registry,
                coreLibPlugin.itemSourceService(),
                backpackChannel,
                storageChannel,
                queueService,
                craftService,
                guiSupport);
        QueueTicker queueTicker = new QueueTicker(plugin,
                executionDispatcher,
                queueService,
                craftService,
                plugin::registry,
                stationGuiService::refreshOpenSessions);

        return new StationRuntimeComponents(appConfigLoader,
                languageLoader,
                messageService,
                bootstrapService,
                executionDispatcher,
                threadOwnership,
                guiService,
                layoutLoader,
                stationLoader,
                recipeLoader,
                capabilities,
                backpackChannel,
                storageChannel,
                outputDelivery,
                queueStore,
                queueService,
                craftService,
                stationGuiService,
                queueTicker);
    }
}
