package emaki.jiuwu.craft.corelib;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
import emaki.jiuwu.craft.corelib.action.pipeline.ActionEngine;
import emaki.jiuwu.craft.corelib.action.pipeline.ActionLineRunner;
import emaki.jiuwu.craft.corelib.action.pipeline.PipelineBatchRunner;
import emaki.jiuwu.craft.corelib.action.pipeline.RegistryPlaceholderBridge;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.CompiledPipeline;
import emaki.jiuwu.craft.corelib.action.pipeline.exec.ConfiguredSequenceRepository;
import emaki.jiuwu.craft.corelib.action.pipeline.exec.PipelineTaskService;
import emaki.jiuwu.craft.corelib.action.pipeline.exec.RegistryStageInvoker;
import emaki.jiuwu.craft.corelib.action.pipeline.exec.SequenceRepository;
import emaki.jiuwu.craft.corelib.action.pipeline.exec.StageDispatcher;
import emaki.jiuwu.craft.corelib.action.pipeline.registry.RegistryStageResolver;
import emaki.jiuwu.craft.corelib.action.pipeline.registry.StageRebuildListeners;
import emaki.jiuwu.craft.corelib.action.pipeline.registry.StageRegistry;
import emaki.jiuwu.craft.corelib.action.pipeline.registry.TriggerRegistry;
import emaki.jiuwu.craft.corelib.api.dialog.CoreLibDialogs;
import emaki.jiuwu.craft.corelib.assembly.OperationTemplateRenderer;
import emaki.jiuwu.craft.corelib.dialog.DialogApiBridge;
import emaki.jiuwu.craft.corelib.dialog.DialogLoader;
import emaki.jiuwu.craft.corelib.dialog.DialogService;
import emaki.jiuwu.craft.corelib.display.DisplayRuntimeSettings;
import emaki.jiuwu.craft.corelib.display.DisplayServiceFactory;
import emaki.jiuwu.craft.corelib.display.ItemDisplayService;
import emaki.jiuwu.craft.corelib.display.TextDisplayService;
import emaki.jiuwu.craft.corelib.event.EmakiEventBus;
import emaki.jiuwu.craft.corelib.event.gameplay.GameplayEventPublisher;
import emaki.jiuwu.craft.corelib.gui.GuiBackend;
import emaki.jiuwu.craft.corelib.gui.GuiBackendRegistry;
import emaki.jiuwu.craft.corelib.gui.GuiClickThrottle;
import emaki.jiuwu.craft.corelib.gui.RegistryBackedGuiBackend;
import emaki.jiuwu.craft.corelib.gui.packet.PacketBackendInstaller;
import emaki.jiuwu.craft.corelib.text.VanillaLanguageDownloader;
import emaki.jiuwu.craft.corelib.text.VanillaTranslationService;

import emaki.jiuwu.craft.corelib.api.async.AsyncFailures;
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
import emaki.jiuwu.craft.corelib.api.capability.ApiCapability;
import emaki.jiuwu.craft.corelib.api.capability.CapabilityRegistration;
import emaki.jiuwu.craft.corelib.capability.CapabilityRegistry;
import emaki.jiuwu.craft.corelib.readiness.ModuleReadinessRegistry;
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
import emaki.jiuwu.craft.corelib.api.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.yaml.AsyncYamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.VersionedYamlFile;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;

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
    private GuiBackendRegistry guiBackendRegistry;
    private GuiBackend guiBackend;
    private AsyncFileService asyncFileService;
    private AsyncYamlFiles asyncYamlFiles;
    private CapabilityProbe platformCapabilities;
    private ExecutionDispatcher executionDispatcher;
    private ThreadOwnership threadOwnership;
    private CorePluginLifecycle corePluginLifecycle;
    private PlaceholderRegistry placeholderRegistry;
    private EconomyManager economyManager;
    private StageRegistry stageRegistry;

    private final TriggerRegistry triggerRegistry = new TriggerRegistry();
    private StageDispatcher stageDispatcher;
    private ActionEngine actionEngine;
    private final PipelineBatchRunner pipelineBatchRunner = new PipelineBatchRunner();
    private PipelineTaskService pipelineTaskService;
    private volatile ConfiguredSequenceRepository sequenceRepository = ConfiguredSequenceRepository.empty();

    private final StageRebuildListeners stageRebuildListeners = new StageRebuildListeners();

    private final CapabilityRegistry capabilityRegistry = new CapabilityRegistry();

    private final ModuleReadinessRegistry moduleReadinessRegistry = new ModuleReadinessRegistry();

    private volatile boolean contentReady;
    private ConfigPrecheckService configPrecheckService;
    private final PdcService pdcService = new PdcService("emaki_corelib");
    private final ItemSourceService itemSourceService = new ItemSourceService();
    private final VanillaTranslationService vanillaTranslationService =
            new VanillaTranslationService();
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
    private final EmakiEventBus eventBus
            = new EmakiEventBus();
    private final Map<Class<?>, Object> serviceRegistry = new ConcurrentHashMap<>();
    private DebugLogger debugLogger;
    private CoreLibCommandRouter commandRouter;
    private DefaultEmakiCoreLibApi coreLibApiBridge;
    private DialogService dialogService;
    private CoreLibDialogs dialogApiBridge;
    private TextDisplayService textDisplayService;
    private ItemDisplayService itemDisplayService;
    private GameplayEventPublisher gameplayEventPublisher;

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
        publishOwnCapabilities();
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
        if (pipelineTaskService != null) {
            pipelineTaskService.stopAll();
        }

        if (stageDispatcher != null) {
            stageDispatcher.close();
        }
        if (stageRegistry != null) {
            stageRegistry.clear();
        }
        triggerRegistry.clear();
        stageRebuildListeners.clear();
        capabilityRegistry.clear();
        markModuleAbsent(getName());
        moduleReadinessRegistry.clear();
        BuiltinStages.shutdown();

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

                name -> sequenceRepository == null ? null : sequenceRepository.bodyOf(name)
        );
        configPrecheckService.configure(candidateStageRegistry);
        ConfigPrecheckReport report = configPrecheckService.checkModule(candidateConfig, "corelib");
        logPrecheckReport(report);
        if (!report.success()) {
            return false;
        }
        if (!stageReport.successful()) {

            for (String failure : stageReport.failures()) {
                getLogger().severe("CoreLib pipeline stage registration failed: " + failure);
            }
            return false;
        }
        if (pipelineTaskService != null) {

            pipelineTaskService.stopAll();
        }

        contentReady = false;
        moduleReadinessRegistry.markLoading(getName(), this::logReadinessFailure);
        configModel = candidateConfig;
        DebugLogger.setGlobalAllEnabled(configModel.debugConfig().globalAll());
        MiniMessages.configureDefaultNoItalic(configModel.miniMessageConfig().defaultNoItalic());
        if (configModel.guiConfig() != null) {
            GuiClickThrottle.configureIntervalMs(configModel.guiConfig().clickIntervalMs());
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
        contentReady = true;

        markModuleReady(getName());
        return true;
    }

    private void installStageRuntime(StageRegistry candidate) {
        if (stageRegistry != null) {
            stageRegistry.revokeAll(this);
            stageRegistry.clear();
        }
        if (stageDispatcher != null) {
            stageDispatcher.cancelOwner(this);
        }

        triggerRegistry.clear();
        stageRegistry = candidate;
        if (stageDispatcher == null) {
            stageDispatcher = new StageDispatcher(executionDispatcher, platformCapabilities);
        }
        actionEngine = new ActionEngine(
                new RegistryStageResolver(stageRegistry),
                new RegistryStageInvoker(stageRegistry),
                stageDispatcher,

                new SequenceRepository() {

                    @Override
                    public CompiledPipeline find(String name) {
                        ConfiguredSequenceRepository live = sequenceRepository;
                        return live == null ? null : live.find(name);
                    }

                    @Override
                    public boolean contains(String name) {
                        ConfiguredSequenceRepository live = sequenceRepository;
                        return live != null && live.contains(name);
                    }

                    @Override
                    public Set<String> requiredParameters(String name) {
                        ConfiguredSequenceRepository live = sequenceRepository;
                        return live == null ? Set.of() : live.requiredParameters(name);
                    }

                    @Override
                    public Set<String> calls(String name) {
                        ConfiguredSequenceRepository live = sequenceRepository;
                        return live == null ? Set.of() : live.calls(name);
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
                dialogApiBridge = new DialogApiBridge(dialogService);
            }
            coreLibApiBridge.installDialogs(dialogApiBridge);
        }
        EmakiCoreLibApi.install(coreLibApiBridge);
    }

    private void publishOwnCapabilities() {
        CapabilityRegistration registration = capabilityRegistry.publish(
                this,
                Set.of(ApiCapability.of("emakicorelib:itemsource_registry"))
        );
        if (!registration.successful()) {
            getLogger().warning("Failed to publish own capabilities: " + registration.reasonKey());
        }
    }

    public BStatsRegistration registerBStats(JavaPlugin plugin, int pluginId) {
        if (bStatsService == null) {
            return BStatsRegistration.noop(plugin, pluginId);
        }
        return bStatsService.register(plugin, pluginId);
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
                List.of("corelib", "emakicore"),
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
            OperationTemplateRenderer.clearRegexCache();
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
        guiBackendRegistry = new GuiBackendRegistry(messageService);
        guiBackendRegistry.setConfiguredName(config.guiConfig().backend());
        GuiClickThrottle.configureIntervalMs(config.guiConfig().clickIntervalMs());
        guiBackend = new RegistryBackedGuiBackend(guiBackendRegistry, configuredItemService);
        itemAssemblyService = new EmakiItemAssemblyService(
                namespaceRegistry,
                itemLayerCodecRegistry,
                itemSourceService,
                debugLogger
        );
        itemAssemblyService.configureAsync(asyncTaskScheduler, executionDispatcher, this, performanceMonitor);
        String displayBackend = config.displayConfig().resolveBackend(config.guiConfig().backend());
        DisplayRuntimeSettings displaySettings =
                DisplayRuntimeSettings.of(
                        config.displayConfig().viewDistanceBlocks(),
                        config.displayConfig().refreshIntervalTicks());
        textDisplayService = DisplayServiceFactory.createTextService(
                this, displayBackend, displaySettings, executionDispatcher);
        itemDisplayService = DisplayServiceFactory.createItemService(
                this, displayBackend, displaySettings, executionDispatcher);
        dialogService = new DialogService(
                this,
                new DialogLoader(this, config.dialogConfig().directory()),
                itemSourceService,
                executionDispatcher);
        dialogService.setEnabled(config.dialogConfig().enabled());
        dialogService.load();
        gameplayEventPublisher = new GameplayEventPublisher(
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
                    messageService.warning("loader.bundled_resource_missing", Map.of(
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
                messageService.warning("loader.bundled_resource_write_failed", Map.of(
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
                messageService.warning("console.action_config_load_failed", Map.of(
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

    public DialogService dialogService() {
        return dialogService;
    }

    public TextDisplayService textDisplayService() {
        return textDisplayService;
    }

    public CoreLibConfig configModel() {
        return configModel;
    }

    public EmakiEventBus eventBus() {
        return eventBus;
    }

    public PlaceholderRegistry placeholderRegistry() {
        return placeholderRegistry;
    }

    public EconomyManager economyManager() {
        return economyManager;
    }

    public StageRegistry stageRegistry() {
        return stageRegistry;
    }

    public TriggerRegistry triggerRegistry() {
        return triggerRegistry;
    }

    public StageRebuildListeners stageRebuildListeners() {
        return stageRebuildListeners;
    }

    public CapabilityRegistry capabilityRegistry() {
        return capabilityRegistry;
    }

    public ModuleReadinessRegistry moduleReadinessRegistry() {
        return moduleReadinessRegistry;
    }

    public boolean contentReady() {
        return contentReady;
    }

    public void markModuleReady(String moduleName) {
        moduleReadinessRegistry.markReady(moduleName, this::logReadinessFailure);
    }

    private void logReadinessFailure(ModuleReadinessRegistry.Failure failure) {
        getLogger().warning("Readiness callback failed for " + failure.owner()
                + " waiting on " + failure.moduleName() + ": " + failure.error());
    }

    public void markModuleLoading(String moduleName) {
        moduleReadinessRegistry.markLoading(moduleName, this::logReadinessFailure);
    }

    public void markModuleAbsent(String moduleName) {
        moduleReadinessRegistry.markAbsent(moduleName, this::logReadinessFailure);
    }

    public ActionEngine actionEngine() {
        return actionEngine;
    }

    public ActionLineRunner actionLineRunner(Plugin moduleOwner) {
        Plugin resolved = moduleOwner == null ? this : moduleOwner;
        return new ActionLineRunner(resolved, this::actionEngine, pipelineBatchRunner,
                new RegistryPlaceholderBridge(this::placeholderRegistry),
                diagnostic -> messageService().renderDiagnostic(diagnostic));
    }

    public PipelineTaskService pipelineTaskService() {
        return pipelineTaskService;
    }

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
                            : messageService().renderFirstDiagnostic(result.diagnostics());
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

    public ConfigPrecheckService configPrecheckService() {
        return configPrecheckService;
    }

    public AsyncTaskScheduler asyncTaskScheduler() {
        return asyncTaskScheduler;
    }

    public PerformanceMonitor performanceMonitor() {
        return performanceMonitor;
    }

    public GuiBackend guiBackend() {
        return guiBackend;
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
            PacketBackendInstaller.install(this, guiBackendRegistry, executionDispatcher);
        } catch (LinkageError | RuntimeException exception) {
            getLogger().warning("Failed to register the packet GUI backend: " + exception.getMessage()
                    + ". EmakiCoreLib will use the Bukkit (entity) backend.");
        }
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

    public ExecutionDispatcher executionDispatcher() {
        return executionDispatcher;
    }

    public ThreadOwnership threadOwnership() {
        return threadOwnership;
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

    public VanillaTranslationService vanillaTranslationService() {
        return vanillaTranslationService;
    }

    private void loadVanillaLanguageTableAsync() {
        CoreLibConfig currentConfig = configModel == null ? CoreLibConfig.defaults() : configModel;
        CoreLibConfig.VanillaLanguageConfig languageConfig = currentConfig.vanillaLanguageConfig();
        if (languageConfig == null || !languageConfig.enabled()) {
            return;
        }
        String locale = languageConfig.locale();
        String minecraftVersion = resolveMinecraftVersion();
        Path cacheDirectory = getDataFolder().toPath().resolve("lang-cache");
        asyncTaskScheduler.runAsync("corelib-vanilla-language", () -> {
            VanillaLanguageDownloader downloader =
                    new VanillaLanguageDownloader(getLogger(), cacheDirectory);
            Map<String, String> table = downloader.load(minecraftVersion, locale);
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
            return Objects.requireNonNullElse(getServer().getMinecraftVersion(), "").trim();
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
        registerService(CapabilityRegistry.class, capabilityRegistry);
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
        registerService(EmakiEventBus.class, eventBus);
    }

    private <T> void registerService(Class<T> type, T service) {
        if (service != null) {
            serviceRegistry.put(type, service);
        }
    }
}
