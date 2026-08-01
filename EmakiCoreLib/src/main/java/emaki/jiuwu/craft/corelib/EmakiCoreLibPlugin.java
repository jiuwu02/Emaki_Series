package emaki.jiuwu.craft.corelib;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
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


import emaki.jiuwu.craft.corelib.action.builtin.BuiltinStages;
import emaki.jiuwu.craft.corelib.action.legacy.LegacyActionMigrator;
import emaki.jiuwu.craft.corelib.action.pipeline.ActionEngine;
import emaki.jiuwu.craft.corelib.action.pipeline.ActionLineRunner;
import emaki.jiuwu.craft.corelib.action.pipeline.PipelineBatchRunner;
import emaki.jiuwu.craft.corelib.action.pipeline.RegistryPlaceholderBridge;
import emaki.jiuwu.craft.corelib.action.pipeline.exec.ConfiguredSequenceRepository;
import emaki.jiuwu.craft.corelib.action.pipeline.exec.PipelineTaskService;
import emaki.jiuwu.craft.corelib.action.pipeline.exec.RegistryStageInvoker;
import emaki.jiuwu.craft.corelib.action.pipeline.exec.SequenceRepository;
import emaki.jiuwu.craft.corelib.action.pipeline.exec.StageDispatcher;
import emaki.jiuwu.craft.corelib.action.pipeline.registry.RegistryStageResolver;
import emaki.jiuwu.craft.corelib.action.pipeline.registry.StageRebuildListeners;
import emaki.jiuwu.craft.corelib.action.pipeline.registry.StageRegistry;

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
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.integration.CraftEngineBlockBridge;
import emaki.jiuwu.craft.corelib.apiimpl.DefaultEmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.integration.CraftEngineBlockBridgeProvider;
import emaki.jiuwu.craft.corelib.api.integration.CustomBlockBridge;
import emaki.jiuwu.craft.corelib.integration.ItemsAdderBlockBridgeProvider;
import emaki.jiuwu.craft.corelib.api.integration.MythicMobBridge;
import emaki.jiuwu.craft.corelib.integration.MythicMobBridgeProvider;
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
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.runtime.CapabilityProbe;
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
    private CapabilityProbe platformCapabilities;
    private ExecutionDispatcher executionDispatcher;
    private ThreadOwnership threadOwnership;
    private CorePluginLifecycle corePluginLifecycle;
    private PlaceholderRegistry placeholderRegistry;
    private EconomyManager economyManager;
    private StageRegistry stageRegistry;
    private StageDispatcher stageDispatcher;
    private ActionEngine actionEngine;
    private final PipelineBatchRunner pipelineBatchRunner = new PipelineBatchRunner();
    private PipelineTaskService pipelineTaskService;
    private volatile ConfiguredSequenceRepository sequenceRepository = ConfiguredSequenceRepository.empty();
    // Outlives every reload: it holds the business modules' re-registration routines, which is exactly what
    // rebuilding the stage table needs to replay.
    private final StageRebuildListeners stageRebuildListeners = new StageRebuildListeners();
    private ConfigPrecheckService configPrecheckService;
    private final PdcService pdcService = new PdcService("emaki_corelib");
    private final ItemSourceService itemSourceService = new ItemSourceService();
    private final emaki.jiuwu.craft.corelib.text.VanillaTranslationService vanillaTranslationService =
            new emaki.jiuwu.craft.corelib.text.VanillaTranslationService();
    private ConfiguredItemService configuredItemService;
    private ItemSourceIntegrationCoordinator itemSourceIntegrationCoordinator;
    private final EmakiNamespaceRegistry namespaceRegistry = new EmakiNamespaceRegistry();
    private final EmakiItemLayerCodecRegistry itemLayerCodecRegistry = new EmakiItemLayerCodecRegistry();
    private final CraftEngineBlockBridge craftEngineBlockBridge = new CraftEngineBlockBridgeProvider(this);
    private final CustomBlockBridge itemsAdderBlockBridge = new ItemsAdderBlockBridgeProvider(this);
    private final CustomBlockBridge nexoBlockBridge = new NexoBlockBridgeProvider(this);
    private final CustomBlockBridge oraxenBlockBridge = new OraxenBlockBridgeProvider(this);
    private final MythicMobBridge mythicMobBridge = new MythicMobBridgeProvider(this);
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
        platformCapabilities = CapabilityProbe.detect(getServer());
        ExecutionBackendLoader.LoadedExecution loadedExecution = ExecutionBackendLoader.load(getServer(), platformCapabilities);
        executionDispatcher = loadedExecution.dispatcher();
        threadOwnership = loadedExecution.ownership();
        coreLibApiBridge = new DefaultEmakiCoreLibApi(this);
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
        runLegacyActionMigration();
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
        if (pipelineTaskService != null) {
            pipelineTaskService.stopAll();
        }
        // Pipeline cleanup happens here rather than in the async shutdown step: cancelling scheduler handles and
        // detaching boss bars both touch Bukkit state, so they need the server thread the disable callback holds.
        if (stageDispatcher != null) {
            stageDispatcher.close();
        }
        if (stageRegistry != null) {
            stageRegistry.clear();
        }
        stageRebuildListeners.clear();
        BuiltinStages.shutdown();

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
        PlaceholderRegistry candidatePlaceholderRegistry = new PlaceholderRegistry(this::debugLogger);
        EconomyManager candidateEconomyManager = new EconomyManager(this);
        candidatePlaceholderRegistry.register(new ActionContextPlaceholderResolver());
        candidatePlaceholderRegistry.register(new ActionInlineTokenResolver());
        candidatePlaceholderRegistry.register(new PlaceholderApiResolver());
        if (pipelineTaskService == null) {
            pipelineTaskService = new PipelineTaskService(this, executionDispatcher,
                    // Late-bound on purpose: the runner needs the engine, the engine needs the stage
                    // registry, and the registry needs these stages. Reading the fields at call time is
                    // what breaks that cycle without leaving a half-built engine visible.
                    (owner, body, context, stopOnFailure) ->
                            pipelineBatchRunner.run(owner, actionEngine, body, context, stopOnFailure));
            getServer().getPluginManager().registerEvents(pipelineTaskService, this);
        }
        StageRegistry candidateStageRegistry = new StageRegistry();
        BuiltinStages.Report stageReport = BuiltinStages.registerAll(
                candidateStageRegistry,
                this,
                executionDispatcher,
                candidateEconomyManager,
                itemSourceService,
                craftEngineBlockBridge,
                itemsAdderBlockBridge,
                nexoBlockBridge,
                oraxenBlockBridge,
                pipelineTaskService,
                // Same reason: sequences are compiled by the engine that this registry is about to build,
                // so the stage resolves a body when it runs rather than when it registers.
                name -> sequenceRepository == null ? null : sequenceRepository.bodyOf(name)
        );
        configPrecheckService.configure(candidateStageRegistry);
        ConfigPrecheckReport report = configPrecheckService.checkModule(candidateConfig, "corelib");
        logPrecheckReport(report);
        if (!report.success()) {
            return false;
        }
        if (!stageReport.successful()) {
            // Fail closed. A stage that could not register means a duplicate id or an undeclared thread domain,
            // which is a coding error rather than a configuration one; starting anyway would hide it until a
            // server owner hit the missing stage at runtime.
            for (String failure : stageReport.failures()) {
                getLogger().severe("CoreLib pipeline stage registration failed: " + failure);
            }
            return false;
        }
        if (pipelineTaskService != null) {
            // Tasks reference the stage table that is about to be replaced, so they cannot outlive it.
            pipelineTaskService.stopAll();
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
        placeholderRegistry = candidatePlaceholderRegistry;
        economyManager = candidateEconomyManager;
        if (languageLoader != null && configModel != null) {
            languageLoader.load();
            languageLoader.setLanguage(configModel.language());
        }
        installStageRuntime(candidateStageRegistry);
        buildSequenceRepository();
        refreshServiceRegistry();
        return true;
    }

    /**
     * Swaps in a freshly built stage registry and rebuilds the engine around it.
     *
     * <p>The previous registry is revoked and this plugin's pending dispatches cancelled first, so a delayed
     * {@code after 20t} body compiled against the old stage table cannot run against the new one.</p>
     *
     * <p>Business modules are then asked to re-register: the candidate table holds only CoreLib's builtin
     * stages, so without this replay every module's stages would be lost on the first reload.</p>
     *
     * @param candidate the registry to install
     */
    private void installStageRuntime(StageRegistry candidate) {
        if (stageRegistry != null) {
            stageRegistry.revokeAll(this);
            stageRegistry.clear();
        }
        if (stageDispatcher != null) {
            stageDispatcher.cancelOwner(this);
        }
        stageRegistry = candidate;
        if (stageDispatcher == null) {
            stageDispatcher = new StageDispatcher(executionDispatcher, platformCapabilities);
        }
        actionEngine = new ActionEngine(
                new RegistryStageResolver(stageRegistry),
                new RegistryStageInvoker(stageRegistry),
                stageDispatcher,
                // Reads through the field rather than capturing it: the repository is compiled by this very
                // engine right after the registry is installed, so it does not exist yet at this point.
                new SequenceRepository() {

                    @Override
                    public emaki.jiuwu.craft.corelib.action.pipeline.compile.CompiledPipeline find(String name) {
                        ConfiguredSequenceRepository live = sequenceRepository;
                        return live == null ? null : live.find(name);
                    }

                    @Override
                    public boolean contains(String name) {
                        ConfiguredSequenceRepository live = sequenceRepository;
                        return live != null && live.contains(name);
                    }

                    @Override
                    public java.util.Set<String> requiredParameters(String name) {
                        ConfiguredSequenceRepository live = sequenceRepository;
                        return live == null ? java.util.Set.of() : live.requiredParameters(name);
                    }

                    @Override
                    public java.util.Set<String> calls(String name) {
                        ConfiguredSequenceRepository live = sequenceRepository;
                        return live == null ? java.util.Set.of() : live.calls(name);
                    }

                    @Override
                    public List<String> names() {
                        ConfiguredSequenceRepository live = sequenceRepository;
                        return live == null ? List.of() : live.names();
                    }
                },
                configModel.pipelineConfig().toLimits()
        );
        replayStageRegistrations();
    }

    /**
     * Re-runs the business modules' stage registrations against the newly installed table.
     *
     * <p>Runs after the engine is rebuilt so that a module's callback observes a consistent runtime. A module
     * whose callback throws is named in the log and skipped; the rest still get their stages, because one
     * module's broken registration must not silently disarm every pipeline on the server.</p>
     */
    private void replayStageRegistrations() {
        if (stageRebuildListeners.size() == 0) {
            return;
        }
        int replayed = stageRebuildListeners.notifyRebuilt(failure -> getLogger().warning(
                "Stage re-registration failed for " + failure.owner() + ": " + failure.error()));
        getLogger().info("Replayed pipeline stage registrations for " + replayed + " plugin(s).");
    }

    private void logPrecheckReport(ConfigPrecheckReport report) {
        ConfigPrecheckMessages.logReport(messageService, "corelib", report);
    }

    private void registerPublicApiService() {
        if (coreLibApiBridge == null) {
            coreLibApiBridge = new DefaultEmakiCoreLibApi(this);
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
        // The stage dispatcher outlives a reload: it owns pending scheduler handles per plugin, so rebuilding it
        // would strand them. reloadActionSystem cancels this plugin's handles instead.
        stageDispatcher = new StageDispatcher(executionDispatcher, platformCapabilities);
        performanceMonitor = new PerformanceMonitor();
        asyncTaskScheduler = AsyncTaskScheduler.forPlugin(
                "emaki-corelib-async",
                performanceMonitor);
        asyncFileService = new AsyncFileService(asyncTaskScheduler, 3, performanceMonitor);
        asyncYamlFiles = new AsyncYamlFiles(asyncFileService);
        loadVanillaLanguageTableAsync();
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
                this, executionDispatcher, eventBus,
                () -> configModel == null ? null : configModel.gameplayEventConfig(),
                mythicMobBridge);
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

    public PlaceholderRegistry placeholderRegistry() {
        return placeholderRegistry;
    }

    public EconomyManager economyManager() {
        return economyManager;
    }

    /** {@return the live pipeline stage registry, or {@code null} before the first successful reload} */
    public StageRegistry stageRegistry() {
        return stageRegistry;
    }

    /** {@return the per-owner callbacks replayed when the stage table is rebuilt} */
    public StageRebuildListeners stageRebuildListeners() {
        return stageRebuildListeners;
    }

    /** {@return the live pipeline engine, or {@code null} before the first successful reload} */
    public ActionEngine actionEngine() {
        return actionEngine;
    }

    /** {@return the shared batch runner for configured pipeline lines} */
    public PipelineBatchRunner pipelineBatchRunner() {
        return pipelineBatchRunner;
    }

    /**
     * Creates a runner a business module can hold for its lifetime.
     *
     * <p>The replacement for handing out {@code actionExecutor()}. The engine is read per call rather
     * than captured, because a reload installs a new engine and stage table; a module holding the old one
     * would keep running against retired stages and its actions would quietly stop working.</p>
     *
     * @param moduleOwner the plugin whose invocations these are
     * @return a runner bound to that plugin
     */
    public ActionLineRunner actionLineRunner(Plugin moduleOwner) {
        Plugin resolved = moduleOwner == null ? this : moduleOwner;
        return new ActionLineRunner(resolved, this::actionEngine, pipelineBatchRunner,
                new RegistryPlaceholderBridge(this::placeholderRegistry));
    }

    /** {@return the long-running task service, or {@code null} before the first reload} */
    public PipelineTaskService pipelineTaskService() {
        return pipelineTaskService;
    }

    /** {@return the compiled named sequences} */
    public ConfiguredSequenceRepository sequenceRepository() {
        return sequenceRepository;
    }

    /**
     * Compiles the configured sequences against the freshly installed engine.
     *
     * <p>Runs after {@code installStageRuntime} because compiling a sequence body needs the stage table it
     * references. Cached compilations are dropped first: they were produced by the previous engine and its
     * stage table, so keeping them would let a reload silently run against retired stages.</p>
     */
    private void buildSequenceRepository() {
        pipelineBatchRunner.invalidate();
        ActionEngine engine = actionEngine;
        if (engine == null) {
            sequenceRepository = ConfiguredSequenceRepository.empty();
            return;
        }
        sequenceRepository = ConfiguredSequenceRepository.build(configModel.actionTemplates(),
                (sequence, line, catalog) -> {
                    ActionEngine.Result result = engine.compile(line, null);
                    if (result.successful()) {
                        return result.pipeline();
                    }
                    String reason = result.diagnostics().isEmpty()
                            ? "did not compile"
                            : result.diagnostics().get(0).reasonKey();
                    getLogger().warning("Sequence '" + sequence + "' line rejected: " + reason
                            + " <- " + line);
                    return null;
                });
        List<String> failed = sequenceRepository.failed();
        if (!failed.isEmpty()) {
            getLogger().warning("Sequences unavailable because a line did not compile: " + failed);
        }
        if (pipelineTaskService != null) {
            CoreLibConfig.LoopConfig loop = configModel.loopConfig();
            pipelineTaskService.configure(new PipelineTaskService.Limits(
                    configModel.pipelineConfig().maxRepeatTimes(),
                    loop == null ? 1L : Math.max(1L, loop.minSyncIntervalTicks()),
                    loop == null ? 200 : loop.maxActiveLoopsTotal(),
                    loop == null ? 10 : loop.maxActiveLoopsPerPlayer(),
                    loop == null ? 100 : loop.maxActiveLoopsPerPlugin(),
                    loop == null || loop.cancelPlayerLoopsOnQuit()));
        }
    }

    /**
     * Runs the one-shot old-syntax migration across every plugin data folder.
     *
     * <p>The single call site of the {@code action.legacy} package: removing the migration means deleting
     * this method, its one call, and that package. Nothing else in the runtime refers to it.</p>
     *
     * <p>Runs after the stage registry is live so that every converted line can be compiled before it is
     * written. A file whose converted form does not compile is left exactly as it was.</p>
     */
    private void runLegacyActionMigration() {
        ActionEngine engine = actionEngine;
        if (engine == null) {
            return;
        }
        List<Path> folders = new ArrayList<>();
        File pluginsRoot = getDataFolder().getParentFile();
        File[] candidates = pluginsRoot == null ? null : pluginsRoot.listFiles();
        if (candidates != null) {
            for (File candidate : candidates) {
                // Only Emaki's own data folders: rewriting another plugin's configs would be well
                // beyond what this migration was asked to do.
                if (candidate.isDirectory() && candidate.getName().startsWith("Emaki")) {
                    folders.add(candidate.toPath());
                }
            }
        }
        LegacyActionMigrator migrator = new LegacyActionMigrator(pipeline -> {
            ActionEngine.Result result = engine.compile(pipeline, null);
            if (result.successful()) {
                return null;
            }
            return result.diagnostics().isEmpty()
                    ? "did not compile"
                    : result.diagnostics().get(0).reasonKey();
        });
        LegacyActionMigrator.Report report = migrator.run(getDataFolder().toPath(), folders, false);
        report.describe().forEach(line -> getLogger().info(line));
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

    public CapabilityProbe platformCapabilities() {
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

    /**
     * {@return the shared server-side vanilla translation table}
     *
     * <p>Never {@code null}. When the table is disabled or could not be downloaded
     * it simply reports unavailable, so callers can query it unconditionally.
     */
    public emaki.jiuwu.craft.corelib.text.VanillaTranslationService vanillaTranslationService() {
        return vanillaTranslationService;
    }

    /**
     * Loads the vanilla language table off the server thread.
     *
     * <p>Opt-in through {@code vanilla_language.enabled}, because it performs
     * outbound network access on first run. The download is cached on disk, so
     * later starts do no network IO. Any failure is reported once and leaves the
     * table unavailable rather than delaying or aborting startup.
     */
    private void loadVanillaLanguageTableAsync() {
        CoreLibConfig currentConfig = configModel == null ? CoreLibConfig.defaults() : configModel;
        CoreLibConfig.VanillaLanguageConfig languageConfig = currentConfig.vanillaLanguageConfig();
        if (languageConfig == null || !languageConfig.enabled()) {
            return;
        }
        String locale = languageConfig.locale();
        String minecraftVersion = resolveMinecraftVersion();
        java.nio.file.Path cacheDirectory = getDataFolder().toPath().resolve("lang-cache");
        asyncTaskScheduler.runAsync("corelib-vanilla-language", () -> {
            emaki.jiuwu.craft.corelib.text.VanillaLanguageDownloader downloader =
                    new emaki.jiuwu.craft.corelib.text.VanillaLanguageDownloader(getLogger(), cacheDirectory);
            java.util.Map<String, String> table = downloader.load(minecraftVersion, locale);
            if (table.isEmpty()) {
                getLogger().info("Vanilla language table for '" + locale
                        + "' is unavailable; features that need localized vanilla names stay disabled.");
                return;
            }
            vanillaTranslationService.install(table);
            getLogger().info("Loaded " + table.size() + " vanilla translations for '" + locale + "'.");
        });
    }

    private String resolveMinecraftVersion() {
        try {
            return java.util.Objects.requireNonNullElse(getServer().getMinecraftVersion(), "").trim();
        } catch (RuntimeException | LinkageError _) {
            return "";
        }
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

    public MythicMobBridge mythicMobBridge() {
        return mythicMobBridge;
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
        registerService(CapabilityProbe.class, platformCapabilities);
        registerService(ExecutionDispatcher.class, executionDispatcher);
        registerService(ThreadOwnership.class, threadOwnership);
        registerService(CorePluginLifecycle.class, corePluginLifecycle);
        registerService(StageRegistry.class, stageRegistry);
        registerService(ActionEngine.class, actionEngine);
        registerService(PipelineTaskService.class, pipelineTaskService);
        registerService(PlaceholderRegistry.class, placeholderRegistry);
        registerService(EconomyManager.class, economyManager);
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
        registerService(MythicMobBridge.class, mythicMobBridge);
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
