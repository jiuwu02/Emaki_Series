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
import emaki.jiuwu.craft.corelib.action.builtin.RunJavaScriptAction;
import emaki.jiuwu.craft.corelib.action.loop.LoopActionService;
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
import emaki.jiuwu.craft.corelib.bridge.mythic.MythicJavaScriptBridge;
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
import emaki.jiuwu.craft.corelib.script.JavaScriptService;
import emaki.jiuwu.craft.corelib.script.ScriptModuleRegistry;
import emaki.jiuwu.craft.corelib.script.ScriptRepository;
import emaki.jiuwu.craft.corelib.script.ScriptService;
import emaki.jiuwu.craft.corelib.script.graal.GraalJavaScriptService;
import emaki.jiuwu.craft.corelib.script.js.JavaScriptActionExtensionLoader;
import emaki.jiuwu.craft.corelib.script.js.registration.JavaScriptRegistrationTracker;
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
    private JavaScriptService javaScriptService;
    private final ScriptModuleRegistry scriptModuleRegistry = new ScriptModuleRegistry();
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
    private EmakiCoreLibApi.Bridge coreLibApiBridge;
    private emaki.jiuwu.craft.corelib.dialog.DialogService dialogService;
    private emaki.jiuwu.craft.corelib.api.dialog.DialogApi.Bridge dialogApiBridge;
    private JavaScriptActionExtensionLoader javaScriptActionExtensionLoader;
    private MythicJavaScriptBridge mythicJavaScriptBridge;
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
        registerMythicJavaScriptBridge();
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
                getLogger().warning("CoreLib async shutdown failed: " + shutdownError(throwable));
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
        reloadScriptSystem();
        if (javaScriptService != null && javaScriptService.enabled()) {
            for (RunJavaScriptAction action : RunJavaScriptAction.createAll(javaScriptService, configModel.scriptConfig())) {
                actionRegistry.register(action);
            }
            reloadJavaScriptActionExtensions();
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

    private void reloadScriptSystem() {
        if (javaScriptActionExtensionLoader != null) {
            javaScriptActionExtensionLoader.close();
            javaScriptActionExtensionLoader = null;
        }
        if (javaScriptService != null) {
            javaScriptService.close();
            javaScriptService = null;
        }
        if (configModel == null || configModel.scriptConfig() == null || !configModel.scriptConfig().enabled()) {
            messageService.info("console.script_engine_disabled");
            return;
        }
        try {
            javaScriptService = new GraalJavaScriptService(
                    this,
                    executionDispatcher,
                    configModel.scriptConfig(),
                    dataPath(configModel.scriptConfig().paths().root()),
                    () -> actionExecutor,
                    scriptModuleRegistry,
                    configModel.releaseDefaultData()
            );
            messageService.info("console.script_engine_ready");
            messageService.info("console.scripts_loaded", Map.of("count", String.valueOf(javaScriptService.loadedScripts().size())));
        } catch (Exception exception) {
            messageService.warning("console.script_engine_failed", Map.of("error", String.valueOf(exception.getMessage())));
        }
    }

    private void reloadJavaScriptActionExtensions() {
        if (javaScriptActionExtensionLoader != null) {
            javaScriptActionExtensionLoader.close();
            javaScriptActionExtensionLoader = null;
        }
        if (javaScriptService == null || !javaScriptService.enabled() || actionRegistry == null || configModel == null || configModel.scriptConfig() == null) {
            return;
        }
        javaScriptActionExtensionLoader = new JavaScriptActionExtensionLoader(
                this,
                actionRegistry,
                placeholderRegistry,
                javaScriptService,
                messageService,
                configModel.scriptConfig(),
                dataPath(configModel.scriptConfig().paths().root()),
                this::debugLogger
        );
        javaScriptActionExtensionLoader.reload();
    }

    private void registerPublicApiService() {
        if (coreLibApiBridge == null) {
            coreLibApiBridge = new DefaultEmakiCoreLibApi(this, platformCapabilities);
        }
        EmakiCoreLibApi.install(coreLibApiBridge);
        if (dialogService != null) {
            if (dialogApiBridge == null) {
                dialogApiBridge = new emaki.jiuwu.craft.corelib.dialog.DialogApiBridge(dialogService);
            }
            emaki.jiuwu.craft.corelib.api.dialog.DialogApi.install(dialogApiBridge);
        }
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

    private void registerMythicJavaScriptBridge() {
        if (mythicJavaScriptBridge != null || !getServer().getPluginManager().isPluginEnabled("MythicMobs")) {
            return;
        }
        mythicJavaScriptBridge = new MythicJavaScriptBridge(this);
        getServer().getPluginManager().registerEvents(mythicJavaScriptBridge, this);
        messageService.info("console.mythic_js_bridge_ready");
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

    public void releaseBundledScripts(JavaPlugin sourcePlugin, String directory, boolean skipWhenAnyFileExists, java.util.List<String> names) {
        releaseBundledScripts(sourcePlugin, directory, skipWhenAnyFileExists, names, releaseDefaultDataEnabled(sourcePlugin));
    }

    public void releaseBundledScripts(JavaPlugin sourcePlugin,
            String directory,
            boolean skipWhenAnyFileExists,
            java.util.List<String> names,
            boolean releaseDefaultData) {
        if (!releaseDefaultData) {
            return;
        }
        CoreLibConfig effectiveConfig = configModel == null ? CoreLibConfig.defaults() : configModel;
        var scriptConfig = effectiveConfig.scriptConfig() == null
                ? emaki.jiuwu.craft.corelib.script.ScriptConfig.defaults()
                : effectiveConfig.scriptConfig();
        new ScriptRepository(
                dataPath(scriptConfig.paths().root()),
                scriptConfig.security()
        ).releaseScriptGroup(sourcePlugin, directory, skipWhenAnyFileExists, names);
    }

    private boolean releaseDefaultDataEnabled(JavaPlugin sourcePlugin) {
        if (sourcePlugin == null) {
            return true;
        }
        if (sourcePlugin == this) {
            return configModel == null || configModel.releaseDefaultData();
        }
        File file = new File(sourcePlugin.getDataFolder(), "config.yml");
        if (!file.isFile()) {
            return true;
        }
        try {
            return YamlFiles.load(file).getBoolean("release_default_data", true);
        } catch (Exception _) {
            return true;
        }
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
                getLogger().warning("CoreLib GUI shutdown dispatch failed: " + shutdownError(throwable));
            }
        }
        return guiShutdown.handle((ignored, throwable) -> {
            if (throwable != null) {
                getLogger().warning("CoreLib GUI shutdown was incomplete: " + shutdownError(throwable));
            }
            return null;
        }).thenRunAsync(() -> {
            runShutdownStep("JavaScript registrations", () -> {
                if (javaScriptActionExtensionLoader != null) {
                    javaScriptActionExtensionLoader.closeAfterBukkitUnregister();
                    javaScriptActionExtensionLoader = null;
                }
            });
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
            runShutdownStep("JavaScript runtime", () -> {
                if (javaScriptService != null) {
                    javaScriptService.close();
                    javaScriptService = null;
                }
            });
            runShutdownStep("public API", () -> {
                if (coreLibApiBridge != null) {
                    EmakiCoreLibApi.uninstall(coreLibApiBridge);
                    coreLibApiBridge = null;
                }
                if (dialogApiBridge != null) {
                    emaki.jiuwu.craft.corelib.api.dialog.DialogApi.uninstall(dialogApiBridge);
                    dialogApiBridge = null;
                }
                dialogService = null;
            });
            mythicJavaScriptBridge = null;
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
            getLogger().warning("CoreLib " + name + " shutdown failed: " + shutdownError(throwable));
        }
    }

    private static String shutdownError(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return current.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
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
        dialogService = new emaki.jiuwu.craft.corelib.dialog.DialogService(
                this,
                new emaki.jiuwu.craft.corelib.dialog.DialogLoader(this, config.dialogConfig().directory()),
                itemSourceService);
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
                    "version",
                    previous -> previous.root().set("script.runtime_opt_in", null)
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

    public JavaScriptService javaScriptService() {
        return javaScriptService;
    }

    public ScriptModuleRegistry scriptModuleRegistry() {
        return scriptModuleRegistry;
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

    public JavaScriptRegistrationTracker javaScriptRegistrationTracker() {
        return javaScriptActionExtensionLoader == null ? null : javaScriptActionExtensionLoader.registrationTracker();
    }

    public java.util.Map<String, Object> javaScriptExtensionStatus() {
        return javaScriptActionExtensionLoader == null ? java.util.Map.of(
                "enabled", javaScriptService != null && javaScriptService.enabled(),
                "globalExtensionScripts", java.util.List.of(),
                "actions", java.util.List.of(),
                "placeholders", java.util.List.of(),
                "events", java.util.List.of(),
                "registrations", java.util.List.of(),
                "recentErrors", java.util.List.of()
        ) : javaScriptActionExtensionLoader.statusSnapshot();
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
        registerService(JavaScriptService.class, javaScriptService);
        registerService(ScriptService.class, javaScriptService);
        registerService(ScriptModuleRegistry.class, scriptModuleRegistry);
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
