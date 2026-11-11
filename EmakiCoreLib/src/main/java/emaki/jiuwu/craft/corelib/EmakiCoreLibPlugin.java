package emaki.jiuwu.craft.corelib;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import emaki.jiuwu.craft.corelib.api.action.CoreStageKind;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
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
import emaki.jiuwu.craft.corelib.action.select.ConfiguredSelectorRepository;
import emaki.jiuwu.craft.corelib.action.select.TargetConditionEvaluator;
import emaki.jiuwu.craft.corelib.action.select.TargetConditionRegistry;
import emaki.jiuwu.craft.corelib.action.select.TargetFactsReader;
import emaki.jiuwu.craft.corelib.action.select.TargetIdentityRegistry;
import emaki.jiuwu.craft.corelib.action.select.TargetSelectorServices;
import emaki.jiuwu.craft.corelib.animation.AnimationPlaybackService;
import emaki.jiuwu.craft.corelib.animation.AnimationRegistry;
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
import emaki.jiuwu.craft.corelib.script.GraalJsEngine;
import emaki.jiuwu.craft.corelib.script.bridge.CoreLibScriptsCoordinator;
import emaki.jiuwu.craft.corelib.text.VanillaLanguageDownloader;
import emaki.jiuwu.craft.corelib.text.VanillaTranslationService;

import emaki.jiuwu.craft.corelib.api.async.AsyncFailures;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckMessages;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckReport;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckService;
import emaki.jiuwu.craft.corelib.debug.ActionAuditLogger;
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
import emaki.jiuwu.craft.corelib.api.furniture.FurnitureBridge;
import emaki.jiuwu.craft.corelib.api.integration.CraftEngineBlockBridge;
import emaki.jiuwu.craft.corelib.apiimpl.DefaultEmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.integration.CraftEngineBlockBridgeProvider;
import emaki.jiuwu.craft.corelib.integration.furniture.CraftEngineFurnitureBridgeProvider;
import emaki.jiuwu.craft.corelib.integration.furniture.FurnitureEventRelay;
import emaki.jiuwu.craft.corelib.integration.furniture.ItemsAdderFurnitureBridgeProvider;
import emaki.jiuwu.craft.corelib.integration.furniture.NexoFurnitureBridgeProvider;
import emaki.jiuwu.craft.corelib.integration.furniture.OraxenFurnitureBridgeProvider;
import emaki.jiuwu.craft.corelib.api.integration.CustomBlockBridge;
import emaki.jiuwu.craft.corelib.integration.ItemsAdderBlockBridgeProvider;
import emaki.jiuwu.craft.corelib.api.integration.MythicMobBridge;
import emaki.jiuwu.craft.corelib.integration.MythicMobBridgeProvider;
import emaki.jiuwu.craft.corelib.integration.MythicTargetIdentityProvider;
import emaki.jiuwu.craft.corelib.integration.NexoBlockBridgeProvider;
import emaki.jiuwu.craft.corelib.integration.OraxenBlockBridgeProvider;
import emaki.jiuwu.craft.corelib.item.ConfiguredItemService;
import emaki.jiuwu.craft.corelib.item.ItemSourceIntegrationCoordinator;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.MinecraftServerVersions;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.log.EmakiLog;
import emaki.jiuwu.craft.corelib.metrics.BStatsRegistration;
import emaki.jiuwu.craft.corelib.metrics.BStatsService;
import emaki.jiuwu.craft.corelib.monitor.PerformanceMonitor;
import emaki.jiuwu.craft.corelib.pdc.PdcMigrationJoinListener;
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
import java.util.LinkedHashMap;
import org.bukkit.Bukkit;

public class EmakiCoreLibPlugin extends JavaPlugin implements LogMessagesProvider, EmakiServiceRegistry, DebugLoggerProvider {

    @Override
    public Logger getLogger() { return new EmakiCoreLibPluginDelegate(this).getLogger(); }

    static final String PLUGIN_NAME = "EmakiCoreLib";

    public static EmakiCoreLibPlugin lookup() { return EmakiCoreLibPluginDelegate.lookup(); }

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
    BStatsService bStatsService;

    LanguageLoader languageLoader;
    MessageService messageService;
    CoreLibConfig configModel = CoreLibConfig.defaults();
    PerformanceMonitor performanceMonitor;
    AsyncTaskScheduler asyncTaskScheduler;
    GuiBackendRegistry guiBackendRegistry;
    GuiBackend guiBackend;
    AsyncFileService asyncFileService;
    AsyncYamlFiles asyncYamlFiles;
    CapabilityProbe platformCapabilities;
    ExecutionDispatcher executionDispatcher;
    ThreadOwnership threadOwnership;
    CorePluginLifecycle corePluginLifecycle;
    PlaceholderRegistry placeholderRegistry;
    EconomyManager economyManager;
    StageRegistry stageRegistry;

    final TriggerRegistry triggerRegistry = new TriggerRegistry();

    final AnimationRegistry animationRegistry = new AnimationRegistry();
    final AnimationPlaybackService animationPlaybackService =
            new AnimationPlaybackService(this, animationRegistry);
    private StageDispatcher stageDispatcher;
    ActionEngine actionEngine;
    final PipelineBatchRunner pipelineBatchRunner = new PipelineBatchRunner();
    PipelineTaskService pipelineTaskService;
    volatile ConfiguredSequenceRepository sequenceRepository = ConfiguredSequenceRepository.empty();

    final StageRebuildListeners stageRebuildListeners = new StageRebuildListeners();

    final CapabilityRegistry capabilityRegistry = new CapabilityRegistry();

    final ModuleReadinessRegistry moduleReadinessRegistry = new ModuleReadinessRegistry();

    final TargetConditionRegistry targetConditionRegistry = new TargetConditionRegistry();
    final TargetIdentityRegistry targetIdentityRegistry =
            new TargetIdentityRegistry(this::reportTargetIdentityFailure);

    volatile boolean contentReady;
    ConfigPrecheckService configPrecheckService;
    final PdcService pdcService = new PdcService("emaki_corelib");
    final ItemSourceService itemSourceService = new ItemSourceService();
    final VanillaTranslationService vanillaTranslationService =
            new VanillaTranslationService();
    ConfiguredItemService configuredItemService;
    ItemSourceIntegrationCoordinator itemSourceIntegrationCoordinator;
    final EmakiNamespaceRegistry namespaceRegistry = new EmakiNamespaceRegistry();
    final EmakiItemLayerCodecRegistry itemLayerCodecRegistry = new EmakiItemLayerCodecRegistry();
    final CraftEngineBlockBridge craftEngineBlockBridge = new CraftEngineBlockBridgeProvider(this);
    final CustomBlockBridge itemsAdderBlockBridge = new ItemsAdderBlockBridgeProvider(this);
    final CustomBlockBridge nexoBlockBridge = new NexoBlockBridgeProvider(this);
    final CustomBlockBridge oraxenBlockBridge = new OraxenBlockBridgeProvider(this);
    final MythicMobBridge mythicMobBridge = new MythicMobBridgeProvider(this);
    final FurnitureBridge craftEngineFurnitureBridge = new CraftEngineFurnitureBridgeProvider(this);
    final FurnitureBridge itemsAdderFurnitureBridge = new ItemsAdderFurnitureBridgeProvider(this);
    final FurnitureBridge nexoFurnitureBridge = new NexoFurnitureBridgeProvider(this);
    final FurnitureBridge oraxenFurnitureBridge = new OraxenFurnitureBridgeProvider(this);
    FurnitureEventRelay furnitureEventRelay;
    EmakiItemAssemblyService itemAssemblyService;
     final EmakiEventBus eventBus
            = new EmakiEventBus();
    final Map<Class<?>, Object> serviceRegistry = new ConcurrentHashMap<>();
    DebugLogger debugLogger;
    CoreLibCommandRouter commandRouter;
    DefaultEmakiCoreLibApi coreLibApiBridge;
    CoreLibScriptsCoordinator scriptsCoordinator;
    DialogService dialogService;
    CoreLibDialogs dialogApiBridge;
    TextDisplayService textDisplayService;
    ItemDisplayService itemDisplayService;
    volatile CompletionStage<Void> guiShutdownStage;
    private GameplayEventPublisher gameplayEventPublisher;

    @Override
    public void onLoad() { new EmakiCoreLibPluginDelegate(this).onLoad(); }

    @Override
    public void onEnable() {
        platformCapabilities = CapabilityProbe.detect(getServer());
        ExecutionBackendLoader.LoadedExecution loadedExecution = ExecutionBackendLoader.load(getServer(), platformCapabilities);
        executionDispatcher = loadedExecution.dispatcher();
        threadOwnership = loadedExecution.ownership();
        coreLibApiBridge = new DefaultEmakiCoreLibApi(this);
        furnitureEventRelay = new FurnitureEventRelay(this);
        getServer().getPluginManager().registerEvents(furnitureEventRelay, this);
        ensureBundledFile("config.yml");
        configModel = loadConfigModel();
        initializeServices();
        targetIdentityRegistry.register(this, new MythicTargetIdentityProvider(mythicMobBridge));
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
        animationPlaybackService.stopAll();

        closeScriptsSafely();

        if (stageDispatcher != null) {
            stageDispatcher.close();
        }
        if (stageRegistry != null) {
            stageRegistry.clear();
        }
        triggerRegistry.clear();
        stageRebuildListeners.clear();
        capabilityRegistry.clear();
        targetConditionRegistry.clear();
        targetIdentityRegistry.clear();
        markModuleAbsent(getName());
        moduleReadinessRegistry.clear();
        BuiltinStages.shutdown();

        HandlerList.unregisterAll(this);
        getServer().getServicesManager().unregisterAll(this);
        shutdownBukkitComponents();
        ExpressionEngine.clearThreadLocalCache();

        CorePluginLifecycle lifecycle = corePluginLifecycle;
        if (lifecycle == null) {
            lifecycle = new CorePluginLifecycle(this::finalizeCoreRuntimeAsync);
            corePluginLifecycle = lifecycle;
        }
        lifecycle.shutdownAsync(15L, TimeUnit.SECONDS).whenComplete((report, throwable) -> {
            if (throwable != null) {
                getLogger().warning("[shutdown] 异步关闭失败: " + AsyncFailures.describe(throwable));
            } else if (report != null && !report.clean()) {
                getLogger().warning("[shutdown] 异步关闭未完成: pendingFiles="
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
                    getLogger().info("[shutdown] 已停止");
                }
            }
        });
    }

    private void shutdownBukkitComponents() { new EmakiCoreLibPluginDelegate(this).shutdownBukkitComponents(); }

    @Override
    public MessageService messageService() { return new EmakiCoreLibPluginDelegate(this).messageService(); }

    @Override
    public <T> T getService(Class<T> type) { return new EmakiCoreLibPluginDelegate(this).getService(type); }

    public LanguageLoader languageLoader() { return new EmakiCoreLibPluginDelegate(this).languageLoader(); }

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
            getServer().getPluginManager().registerEvents(new Listener() {

                @EventHandler(priority = EventPriority.MONITOR)
                public void onPluginDisable(PluginDisableEvent event) {
                    Plugin owner = event.getPlugin();
                    StageRegistry registry = stageRegistry;
                    if (registry != null) {
                        registry.revokeAll(owner);
                    }
                    triggerRegistry.revokeAll(owner);
                    animationPlaybackService.revokeOwner(owner);
                    animationRegistry.revokeAll(owner);
                    targetConditionRegistry.revokeAll(owner);
                    targetIdentityRegistry.revokeAll(owner);
                    stageRebuildListeners.remove(owner);
                }
            }, this);

            getServer().getPluginManager().registerEvents(
                    new PdcMigrationJoinListener(this::debugLogger), this);
        }
        StageRegistry candidateStageRegistry = new StageRegistry();
        BuiltinStages.Report stageReport = BuiltinStages.registerAll(
                candidateStageRegistry,
                this,
                executionDispatcher,
                candidateEconomyManager,
                new ActionAuditLogger(debugLogger),
                itemSourceService,
                craftEngineBlockBridge,
                itemsAdderBlockBridge,
                nexoBlockBridge,
                oraxenBlockBridge,
                pipelineTaskService,

                name -> sequenceRepository == null ? null : sequenceRepository.bodyOf(name),
                new TargetSelectorServices(
                        ConfiguredSelectorRepository.build(candidateConfig.actionSelectors()),
                        new TargetFactsReader(targetIdentityRegistry),
                        new TargetConditionEvaluator(targetConditionRegistry, getLogger()::warning))
        );
        configPrecheckService.configure(candidateStageRegistry);
        ConfigPrecheckReport report = configPrecheckService.checkModule(candidateConfig, "corelib");
        logPrecheckReport(report);
        if (!report.success()) {
            return false;
        }
        if (!stageReport.successful()) {

            for (String failure : stageReport.failures()) {
                getLogger().severe("[pipeline] 管道段注册失败: " + failure);
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
        loadScriptsSafely();
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
        Map<String, String> aliases = validatedAliases(configModel.actionAliases());
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
                configModel.pipelineConfig().toLimits(),
                aliases
        );
        animationPlaybackService.invalidateCompiledFrames();
        replayStageRegistrations();
    }

    private Map<String, String> validatedAliases(Map<String, String> declared) { return new EmakiCoreLibPluginDelegate(this).validatedAliases(declared); }

    private void replayStageRegistrations() { new EmakiCoreLibPluginDelegate(this).replayStageRegistrations(); }

    private void loadScriptsSafely() { new EmakiCoreLibPluginDelegate(this).loadScriptsSafely(); }

    private void closeScriptsSafely() { new EmakiCoreLibPluginDelegate(this).closeScriptsSafely(); }

    void warnScriptsUnavailable(Throwable throwable) { new EmakiCoreLibPluginDelegate(this).warnScriptsUnavailable(throwable); }

    private void logPrecheckReport(ConfigPrecheckReport report) { new EmakiCoreLibPluginDelegate(this).logPrecheckReport(report); }

    private void registerPublicApiService() { new EmakiCoreLibPluginDelegate(this).registerPublicApiService(); }

    private void publishOwnCapabilities() { new EmakiCoreLibPluginDelegate(this).publishOwnCapabilities(); }

    public BStatsRegistration registerBStats(JavaPlugin plugin, int pluginId) { return new EmakiCoreLibPluginDelegate(this).registerBStats(plugin, pluginId); }

    private void logStartupAudit() { new EmakiCoreLibPluginDelegate(this).logStartupAudit(); }

    public Path dataPath(String first, String... more) { return new EmakiCoreLibPluginDelegate(this).dataPath(first, more); }

    private void registerCommandHandler() { new EmakiCoreLibPluginDelegate(this).registerCommandHandler(); }

    CompletionStage<Void> finalizeCoreRuntimeAsync() {
        CompletionStage<Void> guiShutdown = guiShutdownStage;
        guiShutdownStage = null;
        if (guiShutdown == null) {
            guiShutdown = CompletableFuture.completedFuture(null);
        }
        return guiShutdown.handle((ignored, throwable) -> {
            if (throwable != null) {
                getLogger().warning("[shutdown] GUI 关闭未完成: " + AsyncFailures.describe(throwable));
            }
            return null;
        }).thenRun(() -> {
            runShutdownStep("事件总线", () -> {
                if (eventBus != null) {
                    eventBus.clear();
                }
            });
            runShutdownStep("公共 API", () -> {
                if (coreLibApiBridge != null) {
                    coreLibApiBridge.installDialogs(null);
                    EmakiCoreLibApi.uninstall(coreLibApiBridge);
                    coreLibApiBridge = null;
                }
                dialogApiBridge = null;
                dialogService = null;
            });
            runShutdownStep("脚本引擎", GraalJsEngine::shutdownInstance);
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

    void runShutdownStep(String name, Runnable step) { new EmakiCoreLibPluginDelegate(this).runShutdownStep(name, step); }

    private void initializeServices() {
        CoreLibRuntimeComponents components = new CoreLibLifecycleCoordinator().initialize(this);
        languageLoader = components.languageLoader();
        messageService = components.messageService();
        bStatsService = components.bStatsService();
        debugLogger = components.debugLogger();
        itemSourceIntegrationCoordinator = components.itemSourceIntegrationCoordinator();
        configuredItemService = components.configuredItemService();
        configPrecheckService = components.configPrecheckService();
        stageDispatcher = components.stageDispatcher();
        performanceMonitor = components.performanceMonitor();
        asyncTaskScheduler = components.asyncTaskScheduler();
        asyncFileService = components.asyncFileService();
        asyncYamlFiles = components.asyncYamlFiles();
        corePluginLifecycle = components.corePluginLifecycle();
        guiBackendRegistry = components.guiBackendRegistry();
        guiBackend = components.guiBackend();
        itemAssemblyService = components.itemAssemblyService();
        textDisplayService = components.textDisplayService();
        itemDisplayService = components.itemDisplayService();
        dialogService = components.dialogService();
        gameplayEventPublisher = components.gameplayEventPublisher();
        refreshServiceRegistry();
    }

    private void ensureBundledFile(String relativePath) { new EmakiCoreLibPluginDelegate(this).ensureBundledFile(relativePath); }

    private CoreLibConfig loadConfigModel() { return new EmakiCoreLibPluginDelegate(this).loadConfigModel(); }

    void logVersionUpdate(String relativePath, VersionedYamlFile versionedFile) { new EmakiCoreLibPluginDelegate(this).logVersionUpdate(relativePath, versionedFile); }

    public DialogService dialogService() { return new EmakiCoreLibPluginDelegate(this).dialogService(); }

    public TextDisplayService textDisplayService() { return new EmakiCoreLibPluginDelegate(this).textDisplayService(); }

    public CoreLibConfig configModel() { return new EmakiCoreLibPluginDelegate(this).configModel(); }

    public EmakiEventBus eventBus() { return new EmakiCoreLibPluginDelegate(this).eventBus(); }

    public PlaceholderRegistry placeholderRegistry() { return new EmakiCoreLibPluginDelegate(this).placeholderRegistry(); }

    public EconomyManager economyManager() { return new EmakiCoreLibPluginDelegate(this).economyManager(); }

    public StageRegistry stageRegistry() { return new EmakiCoreLibPluginDelegate(this).stageRegistry(); }

    public TriggerRegistry triggerRegistry() { return new EmakiCoreLibPluginDelegate(this).triggerRegistry(); }

    public StageRebuildListeners stageRebuildListeners() { return new EmakiCoreLibPluginDelegate(this).stageRebuildListeners(); }

    public CapabilityRegistry capabilityRegistry() { return new EmakiCoreLibPluginDelegate(this).capabilityRegistry(); }

    public ModuleReadinessRegistry moduleReadinessRegistry() { return new EmakiCoreLibPluginDelegate(this).moduleReadinessRegistry(); }

    public TargetConditionRegistry targetConditionRegistry() { return new EmakiCoreLibPluginDelegate(this).targetConditionRegistry(); }

    public TargetIdentityRegistry targetIdentityRegistry() { return new EmakiCoreLibPluginDelegate(this).targetIdentityRegistry(); }

    private void reportTargetIdentityFailure(String message) { new EmakiCoreLibPluginDelegate(this).reportTargetIdentityFailure(message); }

    public boolean contentReady() { return new EmakiCoreLibPluginDelegate(this).contentReady(); }

    public void markModuleReady(String moduleName) {
        moduleReadinessRegistry.markReady(moduleName, this::logReadinessFailure);
    }

    private void logReadinessFailure(ModuleReadinessRegistry.Failure failure) { new EmakiCoreLibPluginDelegate(this).logReadinessFailure(failure); }

    public void markModuleLoading(String moduleName) {
        moduleReadinessRegistry.markLoading(moduleName, this::logReadinessFailure);
    }

    public void markModuleAbsent(String moduleName) {
        moduleReadinessRegistry.markAbsent(moduleName, this::logReadinessFailure);
    }

    public ActionEngine actionEngine() { return new EmakiCoreLibPluginDelegate(this).actionEngine(); }

    public ActionLineRunner actionLineRunner(Plugin moduleOwner) {
        Plugin resolved = moduleOwner == null ? this : moduleOwner;
        return new ActionLineRunner(resolved, this::actionEngine, pipelineBatchRunner,
                new RegistryPlaceholderBridge(this::placeholderRegistry),
                diagnostic -> messageService().renderDiagnostic(diagnostic));
    }

    public PipelineTaskService pipelineTaskService() { return new EmakiCoreLibPluginDelegate(this).pipelineTaskService(); }

    public AnimationRegistry animationRegistry() { return new EmakiCoreLibPluginDelegate(this).animationRegistry(); }

    public AnimationPlaybackService animationPlaybackService() { return new EmakiCoreLibPluginDelegate(this).animationPlaybackService(); }

    private void buildSequenceRepository() { new EmakiCoreLibPluginDelegate(this).buildSequenceRepository(); }

    public ConfigPrecheckService configPrecheckService() { return new EmakiCoreLibPluginDelegate(this).configPrecheckService(); }

    public AsyncTaskScheduler asyncTaskScheduler() { return new EmakiCoreLibPluginDelegate(this).asyncTaskScheduler(); }

    public PerformanceMonitor performanceMonitor() { return new EmakiCoreLibPluginDelegate(this).performanceMonitor(); }

    public GuiBackend guiBackend() { return new EmakiCoreLibPluginDelegate(this).guiBackend(); }

    private void installPacketBackend() {
        if (guiBackendRegistry == null) {
            return;
        }
        var packetEvents = getServer().getPluginManager().getPlugin("packetevents");
        if (packetEvents != null && packetEvents.isEnabled()) {
            registerPacketBackend();
            return;
        }
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPluginEnable(PluginEnableEvent event) {
                if (event.getPlugin().getName().equalsIgnoreCase("packetevents")) {
                    HandlerList.unregisterAll(this);
                    registerPacketBackend();
                }
            }
        }, this);
        getLogger().info("[gui] PacketEvents 尚未启用; 在其启用后会自动注册 packet GUI 后端");
    }

    private void registerPacketBackend() { new EmakiCoreLibPluginDelegate(this).registerPacketBackend(); }

    public AsyncYamlFiles asyncYamlFiles() { return new EmakiCoreLibPluginDelegate(this).asyncYamlFiles(); }

    public AsyncYamlFiles asyncYamlFiles(Plugin owner) { return new EmakiCoreLibPluginDelegate(this).asyncYamlFiles(owner); }

    public AsyncFileService.FileScope asyncFileScope(Plugin owner) { return new EmakiCoreLibPluginDelegate(this).asyncFileScope(owner); }

    public ExecutionDispatcher executionDispatcher() { return new EmakiCoreLibPluginDelegate(this).executionDispatcher(); }

    public ThreadOwnership threadOwnership() { return new EmakiCoreLibPluginDelegate(this).threadOwnership(); }

    public boolean registerDependentShutdown(String ownerKey, CompletionStage<?> shutdown) { return new EmakiCoreLibPluginDelegate(this).registerDependentShutdown(ownerKey, shutdown); }

    public boolean registerDependentShutdownFuture(String ownerKey, Future<?> shutdown) { return new EmakiCoreLibPluginDelegate(this).registerDependentShutdownFuture(ownerKey, shutdown); }

    public PdcService pdcService() { return new EmakiCoreLibPluginDelegate(this).pdcService(); }

    public VanillaTranslationService vanillaTranslationService() { return new EmakiCoreLibPluginDelegate(this).vanillaTranslationService(); }

    void loadVanillaLanguageTableAsync(AsyncTaskScheduler scheduler) { new EmakiCoreLibPluginDelegate(this).loadVanillaLanguageTableAsync(scheduler); }

    public ItemSourceService itemSourceService() { return new EmakiCoreLibPluginDelegate(this).itemSourceService(); }

    public ConfiguredItemService configuredItemService() { return new EmakiCoreLibPluginDelegate(this).configuredItemService(); }

    public EmakiNamespaceRegistry namespaceRegistry() { return new EmakiCoreLibPluginDelegate(this).namespaceRegistry(); }

    public EmakiItemAssemblyService itemAssemblyService() { return new EmakiCoreLibPluginDelegate(this).itemAssemblyService(); }

    public CraftEngineBlockBridge craftEngineBlockBridge() { return new EmakiCoreLibPluginDelegate(this).craftEngineBlockBridge(); }

    public CustomBlockBridge itemsAdderBlockBridge() { return new EmakiCoreLibPluginDelegate(this).itemsAdderBlockBridge(); }

    public CustomBlockBridge nexoBlockBridge() { return new EmakiCoreLibPluginDelegate(this).nexoBlockBridge(); }

    public MythicMobBridge mythicMobBridge() { return new EmakiCoreLibPluginDelegate(this).mythicMobBridge(); }

    public CustomBlockBridge oraxenBlockBridge() { return new EmakiCoreLibPluginDelegate(this).oraxenBlockBridge(); }

    public FurnitureBridge craftEngineFurnitureBridge() { return new EmakiCoreLibPluginDelegate(this).craftEngineFurnitureBridge(); }

    public FurnitureBridge itemsAdderFurnitureBridge() { return new EmakiCoreLibPluginDelegate(this).itemsAdderFurnitureBridge(); }

    public FurnitureBridge nexoFurnitureBridge() { return new EmakiCoreLibPluginDelegate(this).nexoFurnitureBridge(); }

    public FurnitureBridge oraxenFurnitureBridge() { return new EmakiCoreLibPluginDelegate(this).oraxenFurnitureBridge(); }

    public FurnitureEventRelay furnitureEventRelay() { return new EmakiCoreLibPluginDelegate(this).furnitureEventRelay(); }

    @Override
    public DebugLogger debugLogger() { return new EmakiCoreLibPluginDelegate(this).debugLogger(); }

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
        registerService(CraftEngineFurnitureBridgeProvider.class, (CraftEngineFurnitureBridgeProvider) craftEngineFurnitureBridge);
        registerService(ItemsAdderFurnitureBridgeProvider.class, (ItemsAdderFurnitureBridgeProvider) itemsAdderFurnitureBridge);
        registerService(NexoFurnitureBridgeProvider.class, (NexoFurnitureBridgeProvider) nexoFurnitureBridge);
        registerService(OraxenFurnitureBridgeProvider.class, (OraxenFurnitureBridgeProvider) oraxenFurnitureBridge);
        registerService(FurnitureEventRelay.class, furnitureEventRelay);
        registerService(EmakiItemAssemblyService.class, itemAssemblyService);
        registerService(EmakiEventBus.class, eventBus);
        registerService(EmakiCoreLibPlugin.class, this);
    }

    private <T> void registerService(Class<T> type, T service) { new EmakiCoreLibPluginDelegate(this).registerService(type, service); }
}
