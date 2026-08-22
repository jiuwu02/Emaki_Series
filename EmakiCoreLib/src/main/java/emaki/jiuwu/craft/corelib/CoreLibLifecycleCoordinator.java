package emaki.jiuwu.craft.corelib;

import emaki.jiuwu.craft.corelib.action.pipeline.exec.StageDispatcher;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyService;
import emaki.jiuwu.craft.corelib.async.AsyncFileService;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckService;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.dialog.DialogLoader;
import emaki.jiuwu.craft.corelib.dialog.DialogService;
import emaki.jiuwu.craft.corelib.display.DisplayRuntimeSettings;
import emaki.jiuwu.craft.corelib.display.DisplayServiceFactory;
import emaki.jiuwu.craft.corelib.display.ItemDisplayService;
import emaki.jiuwu.craft.corelib.display.TextDisplayService;
import emaki.jiuwu.craft.corelib.event.gameplay.GameplayEventPublisher;
import emaki.jiuwu.craft.corelib.gui.GuiBackend;
import emaki.jiuwu.craft.corelib.gui.GuiBackendRegistry;
import emaki.jiuwu.craft.corelib.gui.GuiClickThrottle;
import emaki.jiuwu.craft.corelib.gui.RegistryBackedGuiBackend;
import emaki.jiuwu.craft.corelib.item.ConfiguredItemService;
import emaki.jiuwu.craft.corelib.item.ItemSourceIntegrationCoordinator;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.metrics.BStatsService;
import emaki.jiuwu.craft.corelib.monitor.PerformanceMonitor;
import emaki.jiuwu.craft.corelib.runtime.AbstractLifecycleCoordinator;
import emaki.jiuwu.craft.corelib.runtime.CorePluginLifecycle;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.yaml.AsyncYamlFiles;

final class CoreLibLifecycleCoordinator
        extends AbstractLifecycleCoordinator<EmakiCoreLibPlugin, CoreLibRuntimeComponents> {

    private static final int ASYNC_FILE_WORKERS = 3;
    private static final String ASYNC_SCHEDULER_NAME = "emaki-corelib-async";
    private static final String FALLBACK_LANGUAGE = "zh_CN";

    @Override
    public CoreLibRuntimeComponents initialize(EmakiCoreLibPlugin plugin) {
        CoreLibConfig config = plugin.configModel() == null ? CoreLibConfig.defaults() : plugin.configModel();
        MiniMessages.configureDefaultNoItalic(config.miniMessageConfig().defaultNoItalic());
        ItemSourceService itemSourceService = plugin.itemSourceService();
        LanguageLoader languageLoader =
                new LanguageLoader(plugin, "lang", "lang", config.language(), FALLBACK_LANGUAGE);
        MessageService messageService = new MessageService(plugin, languageLoader);
        BStatsService bStatsService = new BStatsService(plugin, messageService);
        DebugLogger debugLogger = new DebugLogger(plugin, languageLoader);
        ItemSourceIntegrationCoordinator itemSourceIntegrationCoordinator =
                new ItemSourceIntegrationCoordinator(plugin, messageService, itemSourceService);
        ConfiguredItemService configuredItemService = new ConfiguredItemService(plugin, itemSourceService);
        ConfigPrecheckService configPrecheckService = new ConfigPrecheckService(messageService);

        StageDispatcher stageDispatcher =
                new StageDispatcher(plugin.executionDispatcher(), plugin.platformCapabilities);
        PerformanceMonitor performanceMonitor = new PerformanceMonitor();
        AsyncTaskScheduler asyncTaskScheduler = AsyncTaskScheduler.forPlugin(
                ASYNC_SCHEDULER_NAME,
                performanceMonitor);
        AsyncFileService asyncFileService =
                new AsyncFileService(asyncTaskScheduler, ASYNC_FILE_WORKERS, performanceMonitor);
        AsyncYamlFiles asyncYamlFiles = new AsyncYamlFiles(asyncFileService);
        plugin.loadVanillaLanguageTableAsync(asyncTaskScheduler);
        CorePluginLifecycle corePluginLifecycle =
                new CorePluginLifecycle(plugin::finalizeCoreRuntimeAsync);
        corePluginLifecycle.start(asyncFileService, asyncTaskScheduler);
        languageLoader.load();
        GuiBackendRegistry guiBackendRegistry = new GuiBackendRegistry(messageService);
        guiBackendRegistry.setConfiguredName(config.guiConfig().backend());
        GuiClickThrottle.configureIntervalMs(config.guiConfig().clickIntervalMs());
        GuiBackend guiBackend = new RegistryBackedGuiBackend(guiBackendRegistry, configuredItemService);
        EmakiItemAssemblyService itemAssemblyService = new EmakiItemAssemblyService(
                plugin.namespaceRegistry(),
                plugin.itemLayerCodecRegistry,
                itemSourceService,
                debugLogger
        );
        itemAssemblyService.configureAsync(
                asyncTaskScheduler, plugin.executionDispatcher(), plugin, performanceMonitor);
        String displayBackend = config.displayConfig().resolveBackend(config.guiConfig().backend());
        DisplayRuntimeSettings displaySettings =
                DisplayRuntimeSettings.of(
                        config.displayConfig().viewDistanceBlocks(),
                        config.displayConfig().refreshIntervalTicks());
        TextDisplayService textDisplayService = DisplayServiceFactory.createTextService(
                plugin, displayBackend, displaySettings, plugin.executionDispatcher());
        ItemDisplayService itemDisplayService = DisplayServiceFactory.createItemService(
                plugin, displayBackend, displaySettings, plugin.executionDispatcher());
        DialogService dialogService = new DialogService(
                plugin,
                new DialogLoader(plugin, config.dialogConfig().directory()),
                itemSourceService,
                plugin.executionDispatcher());
        dialogService.setEnabled(config.dialogConfig().enabled());
        dialogService.load();
        GameplayEventPublisher gameplayEventPublisher = new GameplayEventPublisher(
                plugin, plugin.executionDispatcher(), plugin.eventBus(),
                () -> plugin.configModel() == null ? null : plugin.configModel().gameplayEventConfig(),
                plugin.mythicMobBridge());
        plugin.getServer().getPluginManager().registerEvents(gameplayEventPublisher, plugin);
        return new CoreLibRuntimeComponents(
                languageLoader,
                messageService,
                bStatsService,
                debugLogger,
                itemSourceIntegrationCoordinator,
                configuredItemService,
                configPrecheckService,
                stageDispatcher,
                performanceMonitor,
                asyncTaskScheduler,
                asyncFileService,
                asyncYamlFiles,
                corePluginLifecycle,
                guiBackendRegistry,
                guiBackend,
                itemAssemblyService,
                textDisplayService,
                itemDisplayService,
                dialogService,
                gameplayEventPublisher);
    }
}
