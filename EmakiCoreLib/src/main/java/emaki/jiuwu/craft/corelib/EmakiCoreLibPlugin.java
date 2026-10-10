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
import emaki.jiuwu.craft.corelib.api.integration.CraftEngineBlockBridge;
import emaki.jiuwu.craft.corelib.apiimpl.DefaultEmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.integration.CraftEngineBlockBridgeProvider;
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

public class EmakiCoreLibPlugin extends JavaPlugin implements LogMessagesProvider, EmakiServiceRegistry, DebugLoggerProvider {

    @Override
    public Logger getLogger() {
        return EmakiLog.of(this);
    }

    private static final String PLUGIN_NAME = "EmakiCoreLib";

    public static EmakiCoreLibPlugin lookup() {
        org.bukkit.plugin.Plugin plugin = org.bukkit.Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (plugin instanceof EmakiCoreLibPlugin coreLib) {
            return coreLib;
        }
        throw new IllegalStateException("插件 " + PLUGIN_NAME + " 未加载");
    }

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
    CapabilityProbe platformCapabilities;
    private ExecutionDispatcher executionDispatcher;
    private ThreadOwnership threadOwnership;
    private CorePluginLifecycle corePluginLifecycle;
    private PlaceholderRegistry placeholderRegistry;
    private EconomyManager economyManager;
    private StageRegistry stageRegistry;

    private final TriggerRegistry triggerRegistry = new TriggerRegistry();

    private final AnimationRegistry animationRegistry = new AnimationRegistry();
    private final AnimationPlaybackService animationPlaybackService =
            new AnimationPlaybackService(this, animationRegistry);
    private StageDispatcher stageDispatcher;
    private ActionEngine actionEngine;
    private final PipelineBatchRunner pipelineBatchRunner = new PipelineBatchRunner();
    private PipelineTaskService pipelineTaskService;
    private volatile ConfiguredSequenceRepository sequenceRepository = ConfiguredSequenceRepository.empty();

    private final StageRebuildListeners stageRebuildListeners = new StageRebuildListeners();

    private final CapabilityRegistry capabilityRegistry = new CapabilityRegistry();

    private final ModuleReadinessRegistry moduleReadinessRegistry = new ModuleReadinessRegistry();

    private final TargetConditionRegistry targetConditionRegistry = new TargetConditionRegistry();
    private final TargetIdentityRegistry targetIdentityRegistry =
            new TargetIdentityRegistry(this::reportTargetIdentityFailure);

    private volatile boolean contentReady;
    private ConfigPrecheckService configPrecheckService;
    private final PdcService pdcService = new PdcService("emaki_corelib");
    private final ItemSourceService itemSourceService = new ItemSourceService();
    private final VanillaTranslationService vanillaTranslationService =
            new VanillaTranslationService();
    private ConfiguredItemService configuredItemService;
    private ItemSourceIntegrationCoordinator itemSourceIntegrationCoordinator;
    private final EmakiNamespaceRegistry namespaceRegistry = new EmakiNamespaceRegistry();
    final EmakiItemLayerCodecRegistry itemLayerCodecRegistry = new EmakiItemLayerCodecRegistry();
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
    private CoreLibScriptsCoordinator scriptsCoordinator;
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
                    getLogger().info("[shutdown] 已停止。");
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
        animationPlaybackService.invalidateCompiledFrames();
        replayStageRegistrations();
    }

    private void replayStageRegistrations() {
        if (stageRebuildListeners.size() == 0) {
            return;
        }
        int replayed = stageRebuildListeners.notifyRebuilt(failure -> getLogger().warning(
                "[pipeline] 段重新注册失败，插件 " + failure.owner() + ": " + failure.error()));
        getLogger().info("[pipeline] 已重放管道段注册，共 " + replayed + " 个插件。");
    }

    private void loadScriptsSafely() {
        try {
            if (scriptsCoordinator == null) {
                scriptsCoordinator = new CoreLibScriptsCoordinator(this);
            }
            scriptsCoordinator.loadFromConfig();
        } catch (Throwable throwable) {
            scriptsCoordinator = null;
            warnScriptsUnavailable(throwable);
        }
    }

    private void closeScriptsSafely() {
        CoreLibScriptsCoordinator coordinator = scriptsCoordinator;
        scriptsCoordinator = null;
        if (coordinator == null) {
            return;
        }
        try {
            coordinator.close();
        } catch (Throwable throwable) {
            warnScriptsUnavailable(throwable);
        }
    }

    private void warnScriptsUnavailable(Throwable throwable) {
        String reason = throwable.getMessage();
        String detail = throwable.getClass().getSimpleName()
                + (reason == null || reason.isBlank() ? "" : (": " + reason));
        if (messageService != null) {
            messageService.warning("console.scripts.engine_unavailable", Map.of("reason", detail));
            return;
        }
        getLogger().warning("[script] 脚本扩展不可用: " + throwable);
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
            getLogger().warning("[startup] 发布自身能力失败: " + registration.reasonKey());
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
                "EmakiCoreLib 命令",
                List.of("corelib", "emakicore"),
                new CoreLibBasicCommand(commandRouter)
        );
    }

    CompletionStage<Void> finalizeCoreRuntimeAsync() {
        CompletionStage<Void> guiShutdown = CompletableFuture.completedFuture(null);
        if (guiBackendRegistry != null) {
            try {
                guiShutdown = guiBackendRegistry.shutdownAllAsync();
            } catch (Throwable throwable) {
                getLogger().warning("[shutdown] GUI 关闭分派失败: " + AsyncFailures.describe(throwable));
            }
        }
        return guiShutdown.handle((ignored, throwable) -> {
            if (throwable != null) {
                getLogger().warning("[shutdown] GUI 关闭未完成: " + AsyncFailures.describe(throwable));
            }
            return null;
        }).thenRunAsync(() -> {
            runShutdownStep("物品来源集成", () -> {
                if (itemSourceIntegrationCoordinator != null) {
                    itemSourceIntegrationCoordinator.closeAfterBukkitUnregister();
                    itemSourceIntegrationCoordinator = null;
                }
            });
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
            getLogger().warning("[shutdown] " + name + " 关闭失败: " + AsyncFailures.describe(throwable));
        }
    }

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
                    getLogger().warning("[loader] 内置资源缺失: " + relativePath);
                }
            }
        } catch (Exception exception) {
            if (messageService != null) {
                messageService.warning("loader.bundled_resource_write_failed", Map.of(
                        "path", target.getPath(),
                        "error", String.valueOf(exception.getMessage())
                ));
            } else {
                getLogger().warning("[loader] 写入内置资源失败 " + relativePath + ": " + exception.getMessage());
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
                getLogger().warning("[config] 加载 CoreLib 配置失败: " + exception.getMessage());
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
                    "old_version", versionedFile.previousVersion().isBlank() ? "未知" : versionedFile.previousVersion(),
                    "new_version", versionedFile.updatedVersion()
            ));
        } else {
            getLogger().info("[config] 已更新内置文件版本: " + relativePath + " ("
                    + (versionedFile.previousVersion().isBlank() ? "未知" : versionedFile.previousVersion())
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

    public TargetConditionRegistry targetConditionRegistry() {
        return targetConditionRegistry;
    }

    public TargetIdentityRegistry targetIdentityRegistry() {
        return targetIdentityRegistry;
    }

    private void reportTargetIdentityFailure(String message) {
        getLogger().warning("[integration] " + message);
    }

    public boolean contentReady() {
        return contentReady;
    }

    public void markModuleReady(String moduleName) {
        moduleReadinessRegistry.markReady(moduleName, this::logReadinessFailure);
    }

    private void logReadinessFailure(ModuleReadinessRegistry.Failure failure) {
        getLogger().warning("[startup] 就绪回调失败，插件 " + failure.owner()
                + " 正在等待 " + failure.moduleName() + ": " + failure.error());
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

    public AnimationRegistry animationRegistry() {
        return animationRegistry;
    }

    public AnimationPlaybackService animationPlaybackService() {
        return animationPlaybackService;
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
                            ? "未通过编译"
                            : messageService().renderFirstDiagnostic(result.diagnostics());
                    getLogger().warning("[pipeline] 序列 '" + sequence + "' 的行被拒绝: " + reason
                            + " <- " + line);
                    return null;
                });
        List<String> failed = sequenceRepository.failed();
        if (!failed.isEmpty()) {
            getLogger().warning("[pipeline] 序列不可用，因为某行未通过编译: " + failed);
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
        getLogger().info("[gui] PacketEvents 尚未启用；在其启用后会自动注册 packet GUI 后端。");
    }

    private void registerPacketBackend() {
        try {
            PacketBackendInstaller.install(this, guiBackendRegistry, executionDispatcher);
        } catch (LinkageError | RuntimeException exception) {
            getLogger().warning("[gui] 注册 packet GUI 后端失败: " + exception.getMessage()
                    + "。EmakiCoreLib 将使用 Bukkit（实体）后端。");
        }
    }

    public AsyncYamlFiles asyncYamlFiles() {
        return asyncYamlFiles;
    }

    public AsyncYamlFiles asyncYamlFiles(Plugin owner) {
        if (asyncFileService == null) {
            throw new IllegalStateException("异步文件服务不可用");
        }
        String ownerName = owner == null ? "anonymous" : owner.getName();
        return new AsyncYamlFiles(asyncFileService.openScope(ownerName));
    }

    public AsyncFileService.FileScope asyncFileScope(Plugin owner) {
        if (asyncFileService == null) {
            throw new IllegalStateException("异步文件服务不可用");
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

    void loadVanillaLanguageTableAsync(AsyncTaskScheduler scheduler) {
        CoreLibConfig currentConfig = configModel == null ? CoreLibConfig.defaults() : configModel;
        CoreLibConfig.VanillaLanguageConfig languageConfig = currentConfig.vanillaLanguageConfig();
        if (languageConfig == null || !languageConfig.enabled()) {
            return;
        }
        String locale = languageConfig.locale();
        String minecraftVersion = resolveMinecraftVersion();
        Path cacheDirectory = getDataFolder().toPath().resolve("lang-cache");
        scheduler.runAsync("corelib-vanilla-language", () -> {
            VanillaLanguageDownloader downloader =
                    new VanillaLanguageDownloader(getLogger(), cacheDirectory);
            Map<String, String> table = downloader.load(minecraftVersion, locale);
            if (table.isEmpty()) {
                getLogger().info("[text] 原版语言表（'" + locale
                        + "'）不可用；需要本地化原版名称的功能将保持禁用。");
                return;
            }
            vanillaTranslationService.install(table);
            getLogger().info("[text] 已加载 " + table.size() + " 条原版翻译，语言 '" + locale + "'。");
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
