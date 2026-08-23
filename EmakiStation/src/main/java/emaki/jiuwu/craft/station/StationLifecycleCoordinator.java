package emaki.jiuwu.craft.station;

import java.util.List;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapHooks;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.bootstrap.ConfigKeyMigration;
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
import emaki.jiuwu.craft.station.dismantle.DismantleRecipeLoader;
import emaki.jiuwu.craft.station.dismantle.DismantleService;
import emaki.jiuwu.craft.station.dismantle.DismantleStationLoader;
import emaki.jiuwu.craft.station.gui.ConfiguredGuiSupport;
import emaki.jiuwu.craft.station.gui.StationGuiService;
import emaki.jiuwu.craft.station.material.BackpackChannel;
import emaki.jiuwu.craft.station.material.MergedMaterialChannel;
import emaki.jiuwu.craft.station.material.OutputDelivery;
import emaki.jiuwu.craft.station.material.StationCapabilities;
import emaki.jiuwu.craft.station.material.StorageChannel;
import emaki.jiuwu.craft.station.queue.QueueService;
import emaki.jiuwu.craft.station.queue.QueueStore;
import emaki.jiuwu.craft.station.queue.QueueTicker;
import emaki.jiuwu.craft.station.queue.QueueUnlockService;
import emaki.jiuwu.craft.station.queue.QueueUnlockStore;
import emaki.jiuwu.craft.station.queue.StationCraftService;
import emaki.jiuwu.craft.station.queue.StationQueueUnlockService;
import emaki.jiuwu.craft.station.recipe.RecipeLoader;

final class StationLifecycleCoordinator
        extends AbstractLifecycleCoordinator<EmakiStationPlugin, StationRuntimeComponents> {

    private static final String DEFAULT_PREFIX = "<gray>[ <gradient:#A3E635:#22D3EE>EmakiStation</gradient> ]</gray> ";
    private static final List<String> VERSIONED_FILES = List.of("config.yml");
    private static final List<String> STATIC_FILES = List.of();
    private static final List<String> DEFAULT_DATA_FILES = List.of("stations/blacksmith.yml",
            "gui/station_catalog.yml",
            "gui/station_preview.yml",
            "gui/station_queue.yml",
            "gui/station_dismantle.yml",
            "recipes/example_recipe.yml",
            "recipes/example_component_recipe.yml",
            "recipes_dismantle/example_dismantle_recipe.yml",
            "recipes_dismantle/example_component_dismantle_recipe.yml",
            "stations_dismantle/example_dismantle_station.yml",
            "queue_costs.yml");
    private static final List<String> EXTRA_DIRECTORIES =
            List.of("stations", "stations_dismantle", "gui", "recipes", "recipes_dismantle", "data");
    private static final List<ConfigKeyMigration.Rename> CONFIG_RENAMES = List.of(
            new ConfigKeyMigration.Rename("persistence.autosave_interval", "persistence.autosave_interval_seconds"),
            new ConfigKeyMigration.Rename("gui.refresh_interval", "gui.refresh_interval_ticks"));

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

                    @Override
                    public void afterVersionedMerge(String relativePath, YamlSection runtime, YamlSection bundled) {
                        if (!"config.yml".equals(relativePath)) {
                            return;
                        }
                        ConfigKeyMigration.applyRenames(runtime, bundled, CONFIG_RENAMES, plugin.getLogger());
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
        DismantleStationLoader dismantleStationLoader = new DismantleStationLoader(plugin);
        DismantleRecipeLoader dismantleRecipeLoader = new DismantleRecipeLoader(plugin);
        DismantleService dismantleService = new DismantleService(coreLibPlugin.itemSourceService());

        StationCapabilities capabilities = StationCapabilities.probe();
        BackpackChannel backpackChannel = new BackpackChannel(coreLibPlugin.itemSourceService());
        StorageChannel storageChannel = new StorageChannel(coreLibPlugin.itemSourceService(),
                capabilities, config.storageSettings());
        OutputDelivery outputDelivery =
                new OutputDelivery(coreLibPlugin.itemSourceService(), storageChannel);

        MergedMaterialChannel materialChannel = new MergedMaterialChannel(plugin,
                executionDispatcher,
                backpackChannel,
                storageChannel,
                () -> plugin.appConfig().storageSettings().enabled());

        AsyncYamlFiles queueFiles = coreLibPlugin.asyncYamlFiles(plugin);
        QueueStore queueStore = new QueueStore(plugin, () -> queueFiles);
        QueueService queueService = new QueueService(queueStore);
        QueueUnlockStore unlockStore = new QueueUnlockStore(plugin, () -> queueFiles);

        StationQueueUnlockService purchaseService = new StationQueueUnlockService(
                coreLibPlugin.economyManager(),
                coreLibPlugin.itemSourceService(),
                () -> plugin.appConfig().purchaseSettings(),
                plugin::queueCosts);
        QueueUnlockService unlockService = new QueueUnlockService(unlockStore, purchaseService);
        StationCraftService craftService = new StationCraftService(plugin,
                executionDispatcher,
                queueService,
                backpackChannel,
                storageChannel,
                materialChannel,
                outputDelivery,
                coreLibPlugin.economyManager(),
                plugin::registry,
                () -> plugin.appConfig().limitSettings().maxPendingClaim(),
                () -> plugin.appConfig().persistenceSettings().saveOnSubmit(),
                plugin::debugLogger);
        ConfiguredGuiSupport guiSupport =
                new ConfiguredGuiSupport(() -> layoutLoader, guiService::configuredItemService);
        StationGuiService stationGuiService = new StationGuiService(plugin,
                guiService,
                threadOwnership,
                () -> layoutLoader,
                plugin::registry,
                () -> plugin.appConfig().guiSettings(),
                coreLibPlugin.itemSourceService(),
                materialChannel,
                storageChannel,
                queueService,
                unlockService,
                purchaseService,
                craftService,
                coreLibPlugin.economyManager(),
                StationLifecycleCoordinator::resolvePlaceholders,
                guiSupport,
                dismantleService,
                plugin::dismantleRegistry);
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
                dismantleStationLoader,
                dismantleRecipeLoader,
                dismantleService,
                capabilities,
                backpackChannel,
                storageChannel,
                materialChannel,
                outputDelivery,
                queueStore,
                queueService,
                unlockStore,
                unlockService,
                purchaseService,
                craftService,
                stationGuiService,
                queueTicker);
    }

    private static String resolvePlaceholders(Player player, String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return PlaceholderAPI.setPlaceholders(player, text);
        }
        return text;
    }
}
