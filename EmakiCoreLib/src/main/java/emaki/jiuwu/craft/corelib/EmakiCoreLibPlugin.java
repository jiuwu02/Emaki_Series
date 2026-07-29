package emaki.jiuwu.craft.corelib;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.action.ActionExecutor;
import emaki.jiuwu.craft.corelib.action.ActionLineParser;
import emaki.jiuwu.craft.corelib.action.ActionRegistry;
import emaki.jiuwu.craft.corelib.action.ActionTemplateRegistry;
import emaki.jiuwu.craft.corelib.action.builtin.BuiltinActions;
import emaki.jiuwu.craft.corelib.action.loop.LoopActionService;
import emaki.jiuwu.craft.corelib.async.AsyncFailures;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckMessages;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckReport;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckService;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.debug.DebugLoggerProvider;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyService;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemLayerCodecRegistry;
import emaki.jiuwu.craft.corelib.assembly.EmakiNamespaceRegistry;
import emaki.jiuwu.craft.corelib.async.AsyncFileService;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.command.CoreLibBasicCommand;
import emaki.jiuwu.craft.corelib.command.CoreLibCommandRouter;
import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.api.CompatibilityReport;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.integration.CraftEngineBlockBridge;
import emaki.jiuwu.craft.corelib.apiimpl.DefaultEmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.integration.CraftEngineBlockBridgeProvider;
import emaki.jiuwu.craft.corelib.api.integration.CustomBlockBridge;
import emaki.jiuwu.craft.corelib.integration.ItemsAdderBlockBridgeProvider;
import emaki.jiuwu.craft.corelib.integration.NexoBlockBridgeProvider;
import emaki.jiuwu.craft.corelib.integration.OraxenBlockBridgeProvider;
import emaki.jiuwu.craft.corelib.item.ConfiguredItemService;
import emaki.jiuwu.craft.corelib.item.ItemSourceIntegrationCoordinator;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.metrics.BStatsRegistration;
import emaki.jiuwu.craft.corelib.metrics.BStatsService;
import emaki.jiuwu.craft.corelib.monitor.PerformanceMonitor;
import emaki.jiuwu.craft.corelib.pdc.PdcService;
import emaki.jiuwu.craft.corelib.placeholder.ActionContextPlaceholderResolver;
import emaki.jiuwu.craft.corelib.placeholder.ActionInlineTokenResolver;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderApiResolver;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRegistry;
import emaki.jiuwu.craft.corelib.execution.ExecutionBackendLoader;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.PlatformCapabilities;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.runtime.CorePluginLifecycle;
import emaki.jiuwu.craft.corelib.service.EmakiServiceRegistry;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.yaml.AsyncYamlFiles;
import emaki.jiuwu.craft.corelib.yaml.VersionedYamlFile;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;

public final class EmakiCoreLibPlugin extends JavaPlugin implements LogMessagesProvider, EmakiServiceRegistry, DebugLoggerProvider {

    private static final String STARTUP_ASCII = """
 ______  __    __  ______  __  __   __  ______  ______  ______  ______  __      __  ______
/\\  ___\\/\\ "-./  \\/\\  __ \\/\\ \\/ /  /\\ \\/\\  ___\\/\\  __ \\/\\  == \\/\\  ___\\/\\ \\    /\\ \\/\\  == \\
\\ \\  __\\\\ \\ \\-./\\ \\ \\  __ \\ \\  _"-.\\ \\ \\ \\ \\___\\ \\ \\/\\ \\ \\  __<\\ \\  __\\\\ \\ \\___\\ \\ \\ \\  __<
 \\ \\_____\\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_\\ \\_\\\\ \\_\\ \\_____\\ \\_____\\ \\_\\ \\_\\ \\_____\\ \\_____\\ \\_\\ \\_____\\
  \\/_____/\\/_/  \\/_/\\/_/\\/_/\\/_/\\/_/ \\/_/\\/_____/\\/_____/\\/_/ /_/\\/_____/\\/_____/\\/_/\\/_____/
""";
    private static final int STARTUP_ASCII_START_COLOR = 0xFF80FF;
    private static final int STARTUP_ASCII_END_COLOR = 0x00FFFF;
    private static final int BSTATS_PLUGIN_ID = 31763;

    private BStatsRegistration metrics;
    private BStatsService bStatsService;

    private LanguageLoader languageLoader;
    private MessageService messageService;
    private CoreLibConfig configModel = CoreLibConfig.defaults();
    private PerformanceMonitor performanceMonitor;
    private AsyncTaskScheduler asyncTaskScheduler;
    private emaki.jiuwu.craft.corelib.gui.GuiBackendRegistry guiBackendRegistry;
    private emaki.jiuwu.craft.corelib.gui.GuiBackend guiBackend;
    private AsyncFileService asyncFileService;
    private AsyncYamlFiles asyncYamlFiles;
    private PlatformCapabilities platformCapabilities;
    private ExecutionDispatcher executionDispatcher;
    private ThreadOwnership threadOwnership;
    private CorePluginLifecycle corePluginLifecycle;
    private ActionRegistry actionRegistry;
    private ActionTemplateRegistry actionTemplateRegistry;
    private PlaceholderRegistry placeholderRegistry;
    private EconomyManager economyManager;
    private ActionExecutor actionExecutor;
    private LoopActionService loopActionService;
    private ConfigPrecheckService configPrecheckService;
    private final PdcService pdcService = new PdcService("emaki_corelib");
    private final ItemSourceService itemSourceService = new ItemSourceService();
    private ConfiguredItemService configuredItemService;
    private ItemSourceIntegrationCoordinator itemSourceIntegrationCoordinator;
    private final EmakiNamespaceRegistry namespaceRegistry = new EmakiNamespaceRegistry();
    private final EmakiItemLayerCodecRegistry itemLayerCodecRegistry = new EmakiItemLayerCodecRegistry();
    private final CraftEngineBlockBridge craftEngineBlockBridge = new CraftEngineBlockBridgeProvider(this);
    private final CustomBlockBridge itemsAdderBlockBridge = new ItemsAdderBlockBridgeProvider(this);
    private final CustomBlockBridge nexoBlockBridge = new NexoBlockBridgeProvider(this);
    private final CustomBlockBridge oraxenBlockBridge = new OraxenBlockBridgeProvider(this);
    private EmakiItemAssemblyService itemAssemblyService;
    private final emaki.jiuwu.craft.corelib.assembly.LayerMigrationRegistry layerMigrationRegistry
            = new emaki.jiuwu.craft.corelib.assembly.LayerMigrationRegistry();
    private final emaki.jiuwu.craft.corelib.event.EmakiEventBus eventBus
            = new emaki.jiuwu.craft.corelib.event.EmakiEventBus();
    private final Map<Class<?>, Object> serviceRegistry = new ConcurrentHashMap<>();
    private DebugLogger debugLogger;
    private CoreLibCommandRouter commandRouter;
    private DefaultEmakiCoreLibApi coreLibApiBridge;
    private emaki.jiuwu.craft.corelib.dialog.DialogService dialogService;
    private emaki.jiuwu.craft.corelib.api.dialog.CoreLibDialogs dialogApiBridge;
    private emaki.jiuwu.craft.corelib.display.TextDisplayService textDisplayService;
    private emaki.jiuwu.craft.corelib.display.ItemDisplayService itemDisplayService;
    private emaki.jiuwu.craft.corelib.event.gameplay.GameplayEventPublisher gameplayEventPublisher;

    @Override
    public void onLoad() {


    }

    @Override
    public void onEnable() {
        platformCapabilities = PlatformCapabilities.detect(getServer());
        CompatibilityReport compatibilityReport = platformCapabilities.compatibilityReport(getDescription().getVersion());
        logCompatibilityReport(compatibilityReport);
        if (!compatibilityReport.compatible()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        ExecutionBackendLoader.LoadedExecution loadedExecution = ExecutionBackendLoader.load(getServer(), platformCapabilities);
        executionDispatcher = loadedExecution.dispatcher();
        threadOwnership = loadedExecution.ownership();
        coreLibApiBridge = new DefaultEmakiCoreLibApi(this, platformCapabilities);
        ensureBundledFile("config.yml");
        configModel = loadConfigModel();
        initializeServices();
        ConsoleOutputs.sendGradientAscii(
                this,
                STARTUP_ASCII,
                STARTUP_ASCII_START_COLOR,
                STARTUP_ASCII_END_COLOR
        );
        messageService.info("console.plugin_starting");
        itemSourceIntegrationCoordinator.initialize();
        if (!reloadActionSystem()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        registerCommandHandler();
        registerPublicApiService();
        installPacketBackend();
        logStartupAudit();
        metrics = registerBStats(this, BSTATS_PLUGIN_ID);
        messageService.info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        DebugLogger.setGlobalAllEnabled(false);
        if (metrics != null) {
            metrics.close();
            metrics = null;
        }
        if (bStatsService != null) {
            bStatsService.shutdownAll();
            bStatsService = null;
        }
        if (loopActionService != null) {
            loopActionService.cancelAll();
        }

        // Bukkit registrations must be retired while the disable callback still owns the server thread.
        HandlerList.unregisterAll(this);
        getServer().getServicesManager().unregisterAll(this);
        ExpressionEngine.clearThreadLocalCache();

        CorePluginLifecycle lifecycle = corePluginLifecycle;
        if (lifecycle == null) {
            lifecycle = new CorePluginLifecycle(this::finalizeCoreRuntimeAsync);
            corePluginLifecycle = lifecycle;
        }
        lifecycle.shutdownAsync(15L, TimeUnit.SECONDS).whenComplete((report, throwable) -> {
            if (throwable != null) {
                getLogger().warning("CoreLib async shutdown failed: " + AsyncFailures.describe(throwable));
            } else if (report != null && !report.clean()) {
                getLogger().warning("CoreLib async shutdown was incomplete: pendingFiles="
                        + report.pendingFileOperations()
                        + ", shutdownFailures=" + report.fileFailures().size()
                        + ", schedulerTerminated=" + report.schedulerTerminated());
            }
            asyncFileService = null;
            asyncYamlFiles = null;
            asyncTaskScheduler = null;
            if (messageService != null) {
                try {
                    messageService.info("console.plugin_stopped");
                } catch (RuntimeException exception) {
                    getLogger().info("EmakiCoreLib stopped.");
                }
            }
        });
    }

    @Override
    public MessageService messageService() {
        return messageService;
    }

    @Override
    public <T> T getService(Class<T> type) {
        if (type == null) {
            return null;
        }
        Object service = serviceRegistry.get(type);
        return type.isInstance(service) ? type.cast(service) : null;
    }

    public LanguageLoader languageLoader() {
        return languageLoader;
    }

    public boolean reloadActionSystem() {
        CoreLibConfig candidateConfig = loadConfigModel();
        ActionRegistry candidateActionRegistry = new ActionRegistry(this);
        ActionTemplateRegistry candidateTemplateRegistry = new ActionTemplateRegistry();
        PlaceholderRegistry candidatePlaceholderRegistry = new PlaceholderRegistry(this::debugLogger);
        EconomyManager candidateEconomyManager = new EconomyManager(this);
        LoopActionService effectiveLoopService = loopActionService == null
                ? new LoopActionService(this, executionDispatcher)
                : loopActionService;
        candidatePlaceholderRegistry.register(new ActionContextPlaceholderResolver());
        candidatePlaceholderRegistry.register(new ActionInlineTokenResolver());
        candidatePlaceholderRegistry.register(new PlaceholderApiResolver());
        for (var entry : candidateConfig.actionTemplates().entrySet()) {
            candidateTemplateRegistry.register(entry.getKey(), entry.getValue());
        }
        BuiltinActions.registerAll(
                candidateActionRegistry,
                candidateEconomyManager,
                itemSourceService,
                craftEngineBlockBridge,
                itemsAdderBlockBridge,
                nexoBlockBridge,
                oraxenBlockBridge,
                effectiveLoopService
        );
        configPrecheckService.configure(candidateActionRegistry, candidateTemplateRegistry);
        ConfigPrecheckReport report = configPrecheckService.checkModule(candidateConfig, "corelib");
        logPrecheckReport(report);
        if (!report.success()) {
            return false;
        }
        if (loopActionService != null) {
            loopActionService.cancelAll();
        }
        configModel = candidateConfig;
        DebugLogger.setGlobalAllEnabled(configModel.debugConfig().globalAll());
        MiniMessages.configureDefaultNoItalic(configModel.miniMessageConfig().defaultNoItalic());
        if (configModel.guiConfig() != null) {
            emaki.jiuwu.craft.corelib.gui.GuiClickThrottle.configureIntervalMs(configModel.guiConfig().clickIntervalMs());
        }
        if (dialogService != null && configModel.dialogConfig() != null) {
            dialogService.setEnabled(configModel.dialogConfig().enabled());
            dialogService.load();
        }
        if (guiBackendRegistry != null && configModel.guiConfig() != null) {
            guiBackendRegistry.setConfiguredName(configModel.guiConfig().backend());
        }
        actionRegistry = candidateActionRegistry;
        actionTemplateRegistry = candidateTemplateRegistry;
        placeholderRegistry = candidatePlaceholderRegistry;
        economyManager = candidateEconomyManager;
        loopActionService = effectiveLoopService;
        if (languageLoader != null && configModel != null) {
            languageLoader.load();
            languageLoader.setLanguage(configModel.language());
        }
        actionExecutor = new ActionExecutor(
                this,
                actionRegistry,
                new ActionLineParser(),
                placeholderRegistry,
                actionTemplateRegistry,
                executionDispatcher,
                platformCapabilities,
                asyncTaskScheduler,
                performanceMonitor
        );
        loopActionService.configure(configModel.loopConfig(), actionTemplateRegistry, actionRegistry, () -> actionExecutor);
        refreshServiceRegistry();
        return true;
    }

    private void logPrecheckReport(ConfigPrecheckReport report) {
        ConfigPrecheckMessages.logReport(messageService, "corelib", report);
    }

    private void registerPublicApiService() {
        if (coreLibApiBridge == null) {
            coreLibApiBridge = new DefaultEmakiCoreLibApi(this, platformCapabilities);
        }
        if (dialogService != null) {
            if (dialogApiBridge == null) {
                dialogApiBridge = new emaki.jiuwu.craft.corelib.dialog.DialogApiBridge(dialogService);
            }
            coreLibApiBridge.installDialogs(dialogApiBridge);
        }
        EmakiCoreLibApi.install(coreLibApiBridge);
    }

    public BStatsRegistration registerBStats(JavaPlugin plugin, int pluginId) {
        if (bStatsService == null) {
            return BStatsRegistration.noop(plugin, pluginId);
        }
        return bStatsService.register(plugin, pluginId);
    }

    public BStatsService bStatsService() {
        return bStatsService;
    }

    private void logCompatibilityReport(CompatibilityReport report) {
        getLogger().info("[Compatibility] " + report.summary());
        for (CompatibilityReport.Issue issue : report.issues()) {
            String message = "[Compatibility][" + issue.code() + "] " + issue.message();
            switch (issue.severity()) {
                case INFO -> getLogger().info(message);
                case WARNING -> getLogger().warning(message);
                case ERROR -> getLogger().severe(message);
            }
        }
    }

    private void logStartupAudit() {
        if (economyManager == null) {
            return;
        }
        for (String providerId : economyManager.availableProviderIds()) {
            messageService.info("console.economy_bridge_ready", Map.of("provider", providerId));
        }
        for (String blockProvider : new String[]{"CraftEngine", "ItemsAdder", "Nexo", "Oraxen"}) {
            if (getServer().getPluginManager().isPluginEnabled(blockProvider)) {
                messageService.info("console.block_source_bridge_ready", Map.of("provider", blockProvider));
            }
        }
    }

    public Path dataPath(String first, String... more) {
        return getDataFolder().toPath().resolve(Path.of(first, more));
    }

    private void registerCommandHandler() {
        commandRouter = new CoreLibCommandRouter(this, executionDispatcher);



        registerCommand(
                "emakicorelib",
                "EmakiCoreLib management command",
                java.util.List.of("corelib", "emakicore"),
                new CoreLibBasicCommand(commandRouter)
        );
    }

    private CompletionStage<Void> finalizeCoreRuntimeAsync() {
        CompletionStage<Void> guiShutdown = CompletableFuture.completedFuture(null);
        if (guiBackendRegistry != null) {
            try {
                guiShutdown = guiBackendRegistry.shutdownAllAsync();
            } catch (Throwable throwable) {
                getLogger().warning("CoreLib GUI shutdown dispatch failed: " + AsyncFailures.describe(throwable));
            }
        }
        return guiShutdown.handle((ignored, throwable) -> {
            if (throwable != null) {
                getLogger().warning("CoreLib GUI shutdown was incomplete: " + AsyncFailures.describe(throwable));
            }
            return null;
        }).thenRunAsync(() -> {
            runShutdownStep("item source integrations", () -> {
                if (itemSourceIntegrationCoordinator != null) {
                    itemSourceIntegrationCoordinator.closeAfterBukkitUnregister();
                    itemSourceIntegrationCoordinator = null;
                }
            });
            runShutdownStep("event bus", () -> {
                if (eventBus != null) {
                    eventBus.clear();
                }
            });
            runShutdownStep("public API", () -> {
                if (coreLibApiBridge != null) {
                    coreLibApiBridge.installDialogs(null);
                    EmakiCoreLibApi.uninstall(coreLibApiBridge);
                    coreLibApiBridge = null;
                }
                dialogApiBridge = null;
                dialogService = null;
                if (textDisplayService != null) {
                    textDisplayService.shutdown();
                    textDisplayService = null;
                }
                if (itemDisplayService != null) {
                    itemDisplayService.shutdown();
                    itemDisplayService = null;
                }
            });
            gameplayEventPublisher = null;
            guiBackendRegistry = null;
            guiBackend = null;
            serviceRegistry.clear();
            executionDispatcher = null;
            threadOwnership = null;
            platformCapabilities = null;
            ExpressionEngine.clearGlobalCache();
            ExpressionEngine.clearThreadLocalCache();
            emaki.jiuwu.craft.corelib.assembly.OperationTemplateRenderer.clearRegexCache();
        });
    }

    private void runShutdownStep(String name, Runnable step) {
        try {
            step.run();
        } catch (Throwable throwable) {
            getLogger().warning("CoreLib " + name + " shutdown failed: " + AsyncFailures.describe(throwable));
        }
    }

    private void initializeServices() {
        CoreLibConfig config = configModel == null ? CoreLibConfig.defaults() : configModel;
        MiniMessages.configureDefaultNoItalic(config.miniMessageConfig().defaultNoItalic());
        languageLoader = new LanguageLoader(this, "lang", "lang", config.language(), "zh_CN");
        messageService = new MessageService(this, languageLoader);
        bStatsService = new BStatsService(this, messageService);
        debugLogger = new DebugLogger(this, languageLoader);
        itemSourceIntegrationCoordinator = new ItemSourceIntegrationCoordinator(this, messageService, itemSourceService);
        configuredItemService = new ConfiguredItemService(this, itemSourceService);
        configPrecheckService = new ConfigPrecheckService(messageService);
        loopActionService = new LoopActionService(this, executionDispatcher);
        getServer().getPluginManager().registerEvents(loopActionService, this);
        performanceMonitor = new PerformanceMonitor();
        asyncTaskScheduler = AsyncTaskScheduler.forPlugin(
                "emaki-corelib-async",
                performanceMonitor);
        asyncFileService = new AsyncFileService(asyncTaskScheduler, 3, performanceMonitor);
        asyncYamlFiles = new AsyncYamlFiles(asyncFileService);
        corePluginLifecycle = new CorePluginLifecycle(this::finalizeCoreRuntimeAsync);
        corePluginLifecycle.start(asyncFileService, asyncTaskScheduler);
        languageLoader.load();
        guiBackendRegistry = new emaki.jiuwu.craft.corelib.gui.GuiBackendRegistry(messageService);
        guiBackendRegistry.setConfiguredName(config.guiConfig().backend());
        emaki.jiuwu.craft.corelib.gui.GuiClickThrottle.configureIntervalMs(config.guiConfig().clickIntervalMs());
        guiBackend = new emaki.jiuwu.craft.corelib.gui.RegistryBackedGuiBackend(guiBackendRegistry, configuredItemService);
        itemAssemblyService = new EmakiItemAssemblyService(
                namespaceRegistry,
                itemLayerCodecRegistry,
                itemSourceService,
                debugLogger
        );
        itemAssemblyService.configureAsync(asyncTaskScheduler, executionDispatcher, this, performanceMonitor);
        String displayBackend = config.displayConfig().resolveBackend(config.guiConfig().backend());
        emaki.jiuwu.craft.corelib.display.DisplayRuntimeSettings displaySettings =
                emaki.jiuwu.craft.corelib.display.DisplayRuntimeSettings.of(
                        config.displayConfig().viewDistanceBlocks(),
                        config.displayConfig().refreshIntervalTicks());
        textDisplayService = emaki.jiuwu.craft.corelib.display.DisplayServiceFactory.createTextService(
                this, displayBackend, displaySettings, executionDispatcher);
        itemDisplayService = emaki.jiuwu.craft.corelib.display.DisplayServiceFactory.createItemService(
                this, displayBackend, displaySettings, executionDispatcher);
        dialogService = new emaki.jiuwu.craft.corelib.dialog.DialogService(
                this,
                new emaki.jiuwu.craft.corelib.dialog.DialogLoader(this, config.dialogConfig().directory()),
                itemSourceService,
                executionDispatcher);
        dialogService.setEnabled(config.dialogConfig().enabled());
        dialogService.load();
        gameplayEventPublisher = new emaki.jiuwu.craft.corelib.event.gameplay.GameplayEventPublisher(
                this, executionDispatcher, eventBus, () -> configModel == null ? null : configModel.gameplayEventConfig());
        getServer().getPluginManager().registerEvents(gameplayEventPublisher, this);
        refreshServiceRegistry();
    }

    private void ensureBundledFile(String relativePath) {
        File target = new File(getDataFolder(), relativePath);
        try {
            boolean copied = YamlFiles.copyResourceIfMissing(this, relativePath, target);
            if (!copied && !target.exists()) {
                if (messageService != null) {
                    messageService.warning("loader.bundled_resource_missing", java.util.Map.of(
                            "type", "资源",
                            "path", target.getPath(),
                            "resource", relativePath
                    ));
                } else {
                    getLogger().warning("Bundled resource missing: " + relativePath);
                }
            }
        } catch (Exception exception) {
            if (messageService != null) {
                messageService.warning("loader.bundled_resource_write_failed", java.util.Map.of(
                        "path", target.getPath(),
                        "error", String.valueOf(exception.getMessage())
                ));
            } else {
                getLogger().warning("Failed to write bundled resource " + relativePath + ": " + exception.getMessage());
            }
        }
    }

    private CoreLibConfig loadConfigModel() {
        try {
            File file = new File(getDataFolder(), "config.yml");
            VersionedYamlFile versionedFile = YamlFiles.syncVersionedResource(
                    this,
                    file,
                    "config.yml",
                    "version"
            );
            logVersionUpdate("config.yml", versionedFile);
            return CoreLibConfig.fromConfig(versionedFile == null ? YamlFiles.load(file) : versionedFile.root());
        } catch (Exception exception) {
            if (messageService != null) {
                messageService.warning("console.action_config_load_failed", java.util.Map.of(
                        "error", String.valueOf(exception.getMessage())
                ));
            } else {
                getLogger().warning("Failed to load CoreLib config: " + exception.getMessage());
            }
            return CoreLibConfig.defaults();
        }
    }

    private void logVersionUpdate(String relativePath, VersionedYamlFile versionedFile) {
        if (versionedFile == null || !versionedFile.versionUpdated()) {
            return;
        }
        if (messageService != null) {
            messageService.info("console.versioned_file_updated", Map.of(
                    "path", relativePath,
                    "old_version", versionedFile.previousVersion().isBlank() ? "unknown" : versionedFile.previousVersion(),
                    "new_version", versionedFile.updatedVersion()
            ));
        } else {
            getLogger().info("Updated bundled file version: " + relativePath + " ("
                    + (versionedFile.previousVersion().isBlank() ? "unknown" : versionedFile.previousVersion())
                    + " -> " + versionedFile.updatedVersion() + ")");
        }
    }

    public emaki.jiuwu.craft.corelib.dialog.DialogService dialogService() {
        return dialogService;
    }

    /**
     * {@return CoreLib 的默认文本展示服务}
     *
     * <p>按 CoreLib 的 {@code display.*} 配置创建，供无特殊需求的模块直接取用。
     * 需要独立配置的模块应改用 {@code DisplayServiceFactory} 自建实例，并自行负责关闭。
     */
    public emaki.jiuwu.craft.corelib.display.TextDisplayService textDisplayService() {
        return textDisplayService;
    }

    /** {@return CoreLib 的默认物品展示服务，语义同 {@link #textDisplayService()}} */
    public emaki.jiuwu.craft.corelib.display.ItemDisplayService itemDisplayService() {
        return itemDisplayService;
    }

    public CoreLibConfig configModel() {
        return configModel;
    }







    public emaki.jiuwu.craft.corelib.event.EmakiEventBus eventBus() {
        return eventBus;
    }

    public ActionRegistry actionRegistry() {
        return actionRegistry;
    }

    public ActionTemplateRegistry actionTemplateRegistry() {
        return actionTemplateRegistry;
    }

    public PlaceholderRegistry placeholderRegistry() {
        return placeholderRegistry;
    }

    public EconomyManager economyManager() {
        return economyManager;
    }

    public ActionExecutor actionExecutor() {
        return actionExecutor;
    }

    public LoopActionService loopActionService() {
        return loopActionService;
    }

    public ConfigPrecheckService configPrecheckService() {
        return configPrecheckService;
    }

    public AsyncTaskScheduler asyncTaskScheduler() {
        return asyncTaskScheduler;
    }

    public PerformanceMonitor performanceMonitor() {
        return performanceMonitor;
    }

    public emaki.jiuwu.craft.corelib.gui.GuiBackend guiBackend() {
        return guiBackend;
    }






    public emaki.jiuwu.craft.corelib.gui.GuiBackendRegistry guiBackendRegistry() {
        return guiBackendRegistry;
    }













    private void installPacketBackend() {
        if (guiBackendRegistry == null) {
            return;
        }
        var packetEvents = getServer().getPluginManager().getPlugin("PacketEvents");
        if (packetEvents == null || !packetEvents.isEnabled()) {
            return;
        }
        try {
            emaki.jiuwu.craft.corelib.gui.packet.PacketBackendInstaller.install(this, guiBackendRegistry, executionDispatcher);
        } catch (LinkageError | RuntimeException exception) {
            getLogger().warning("Failed to register the packet GUI backend: " + exception.getMessage()
                    + ". EmakiCoreLib will use the Bukkit (entity) backend.");
        }
    }

    public AsyncFileService asyncFileService() {
        return asyncFileService;
    }

    public AsyncYamlFiles asyncYamlFiles() {
        return asyncYamlFiles;
    }

    public AsyncYamlFiles asyncYamlFiles(Plugin owner) {
        if (asyncFileService == null) {
            throw new IllegalStateException("Async file service is unavailable");
        }
        String ownerName = owner == null ? "anonymous" : owner.getName();
        return new AsyncYamlFiles(asyncFileService.openScope(ownerName));
    }

    public AsyncFileService.FileScope asyncFileScope(Plugin owner) {
        if (asyncFileService == null) {
            throw new IllegalStateException("Async file service is unavailable");
        }
        String ownerName = owner == null ? "anonymous" : owner.getName();
        return asyncFileService.openScope(ownerName);
    }

    public PlatformCapabilities platformCapabilities() {
        return platformCapabilities;
    }

    public ExecutionDispatcher executionDispatcher() {
        return executionDispatcher;
    }

    public ThreadOwnership threadOwnership() {
        return threadOwnership;
    }

    public CorePluginLifecycle corePluginLifecycle() {
        return corePluginLifecycle;
    }

    public boolean registerDependentShutdown(String ownerKey, CompletionStage<?> shutdown) {
        CorePluginLifecycle lifecycle = corePluginLifecycle;
        return lifecycle != null && lifecycle.registerDependentShutdown(ownerKey, shutdown);
    }

    public boolean registerDependentShutdownFuture(String ownerKey, Future<?> shutdown) {
        CorePluginLifecycle lifecycle = corePluginLifecycle;
        return lifecycle != null && lifecycle.registerDependentShutdownFuture(ownerKey, shutdown);
    }

    public PdcService pdcService() {
        return pdcService;
    }

    public ItemSourceService itemSourceService() {
        return itemSourceService;
    }

    public ConfiguredItemService configuredItemService() {
        return configuredItemService;
    }

    public EmakiNamespaceRegistry namespaceRegistry() {
        return namespaceRegistry;
    }

    public EmakiItemLayerCodecRegistry itemLayerCodecRegistry() {
        return itemLayerCodecRegistry;
    }

    public EmakiItemAssemblyService itemAssemblyService() {
        return itemAssemblyService;
    }

    public CraftEngineBlockBridge craftEngineBlockBridge() {
        return craftEngineBlockBridge;
    }

    public CustomBlockBridge itemsAdderBlockBridge() {
        return itemsAdderBlockBridge;
    }

    public CustomBlockBridge nexoBlockBridge() {
        return nexoBlockBridge;
    }

    public CustomBlockBridge oraxenBlockBridge() {
        return oraxenBlockBridge;
    }

    @Override
    public DebugLogger debugLogger() {
        return debugLogger;
    }

    private void refreshServiceRegistry() {
        serviceRegistry.clear();
        registerService(LanguageLoader.class, languageLoader);
        registerService(MessageService.class, messageService);
        registerService(BStatsService.class, bStatsService);
        registerService(PerformanceMonitor.class, performanceMonitor);
        registerService(AsyncTaskScheduler.class, asyncTaskScheduler);
        registerService(AsyncFileService.class, asyncFileService);
        registerService(AsyncYamlFiles.class, asyncYamlFiles);
        registerService(PlatformCapabilities.class, platformCapabilities);
        registerService(ExecutionDispatcher.class, executionDispatcher);
        registerService(ThreadOwnership.class, threadOwnership);
        registerService(CorePluginLifecycle.class, corePluginLifecycle);
        registerService(ActionRegistry.class, actionRegistry);
        registerService(ActionTemplateRegistry.class, actionTemplateRegistry);
        registerService(PlaceholderRegistry.class, placeholderRegistry);
        registerService(EconomyManager.class, economyManager);
        registerService(ActionExecutor.class, actionExecutor);
        registerService(LoopActionService.class, loopActionService);
        registerService(ConfigPrecheckService.class, configPrecheckService);
        registerService(PdcService.class, pdcService);
        registerService(ItemSourceService.class, itemSourceService);
        registerService(ConfiguredItemService.class, configuredItemService);
        registerService(EmakiNamespaceRegistry.class, namespaceRegistry);
        registerService(EmakiItemLayerCodecRegistry.class, itemLayerCodecRegistry);
        registerService(CraftEngineBlockBridge.class, craftEngineBlockBridge);
        registerService(ItemsAdderBlockBridgeProvider.class, (ItemsAdderBlockBridgeProvider) itemsAdderBlockBridge);
        registerService(NexoBlockBridgeProvider.class, (NexoBlockBridgeProvider) nexoBlockBridge);
        registerService(OraxenBlockBridgeProvider.class, (OraxenBlockBridgeProvider) oraxenBlockBridge);
        registerService(EmakiItemAssemblyService.class, itemAssemblyService);
        registerService(emaki.jiuwu.craft.corelib.assembly.LayerMigrationRegistry.class, layerMigrationRegistry);
        registerService(emaki.jiuwu.craft.corelib.event.EmakiEventBus.class, eventBus);
    }

    private <T> void registerService(Class<T> type, T service) {
        if (service != null) {
            serviceRegistry.put(type, service);
        }
    }
}
