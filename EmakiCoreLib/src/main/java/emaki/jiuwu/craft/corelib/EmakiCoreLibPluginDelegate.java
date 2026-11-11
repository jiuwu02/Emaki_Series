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

final class EmakiCoreLibPluginDelegate {

    private final EmakiCoreLibPlugin ownerRef;

    EmakiCoreLibPluginDelegate(EmakiCoreLibPlugin ownerRef) {
        this.ownerRef = ownerRef;
    }

    Logger getLogger() {
        return EmakiLog.of(ownerRef);
    }

    static EmakiCoreLibPlugin lookup() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(EmakiCoreLibPlugin.PLUGIN_NAME);
        if (plugin instanceof EmakiCoreLibPlugin coreLib) {
            return coreLib;
        }
        throw new IllegalStateException("插件 " + EmakiCoreLibPlugin.PLUGIN_NAME + " 未加载");
    }

    void onLoad() {

    }

    void shutdownBukkitComponents() {
        ownerRef.runShutdownStep("物品来源集成", () -> {
            if (ownerRef.itemSourceIntegrationCoordinator != null) {
                ownerRef.itemSourceIntegrationCoordinator.closeAfterBukkitUnregister();
                ownerRef.itemSourceIntegrationCoordinator = null;
            }
        });
        ownerRef.runShutdownStep("文本展示", () -> {
            if (ownerRef.textDisplayService != null) {
                ownerRef.textDisplayService.shutdown();
                ownerRef.textDisplayService = null;
            }
        });
        ownerRef.runShutdownStep("物品展示", () -> {
            if (ownerRef.itemDisplayService != null) {
                ownerRef.itemDisplayService.shutdown();
                ownerRef.itemDisplayService = null;
            }
        });
        ownerRef.runShutdownStep("菜单后端", () -> {
            if (ownerRef.guiBackendRegistry != null) {
                ownerRef.guiShutdownStage = ownerRef.guiBackendRegistry.shutdownAllAsync();
            }
        });
    }

    MessageService messageService() {
        return ownerRef.messageService;
    }

    <T> T getService(Class<T> type) {
        if (type == null) {
            return null;
        }
        Object service = ownerRef.serviceRegistry.get(type);
        return type.isInstance(service) ? type.cast(service) : null;
    }

    LanguageLoader languageLoader() {
        return ownerRef.languageLoader;
    }

    Map<String, String> validatedAliases(Map<String, String> declared) {
        if (declared == null || declared.isEmpty()) {
            return Map.of();
        }
        Map<String, String> accepted = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : declared.entrySet()) {
            String name = entry.getKey();
            CoreStageKind kind = ownerRef.stageRegistry == null ? null : ownerRef.stageRegistry.kindOf(name);
            if (kind != null) {
                ownerRef.getLogger().warning("[pipeline] 动作别名 '" + name
                        + "' 与已注册段 id 同名, 已忽略该别名以免静默遮蔽原段");
                continue;
            }
            accepted.put(name, entry.getValue());
        }
        return accepted.isEmpty() ? Map.of() : Map.copyOf(accepted);
    }

    void replayStageRegistrations() {
        if (ownerRef.stageRebuildListeners.size() == 0) {
            return;
        }
        int replayed = ownerRef.stageRebuildListeners.notifyRebuilt(failure -> ownerRef.getLogger().warning(
                "[pipeline] 段重新注册失败, 插件 " + failure.owner() + ": " + failure.error()));
        ownerRef.getLogger().info("[pipeline] 已重放管道段注册, 共 " + replayed + " 个插件");
    }

    void loadScriptsSafely() {
        try {
            if (ownerRef.scriptsCoordinator == null) {
                ownerRef.scriptsCoordinator = new CoreLibScriptsCoordinator(ownerRef);
            }
            ownerRef.scriptsCoordinator.loadFromConfig();
        } catch (Throwable throwable) {
            ownerRef.scriptsCoordinator = null;
            ownerRef.warnScriptsUnavailable(throwable);
        }
    }

    void closeScriptsSafely() {
        CoreLibScriptsCoordinator coordinator = ownerRef.scriptsCoordinator;
        ownerRef.scriptsCoordinator = null;
        if (coordinator == null) {
            return;
        }
        try {
            coordinator.close();
        } catch (Throwable throwable) {
            ownerRef.warnScriptsUnavailable(throwable);
        }
    }

    void warnScriptsUnavailable(Throwable throwable) {
        String reason = throwable.getMessage();
        String detail = throwable.getClass().getSimpleName()
                + (reason == null || reason.isBlank() ? "" : (": " + reason));
        if (ownerRef.messageService != null) {
            ownerRef.messageService.warning("console.scripts.engine_unavailable", Map.of("reason", detail));
            return;
        }
        ownerRef.getLogger().warning("[script] 脚本扩展不可用: " + throwable);
    }

    void logPrecheckReport(ConfigPrecheckReport report) {
        ConfigPrecheckMessages.logReport(ownerRef.messageService, "corelib", report);
    }

    void registerPublicApiService() {
        if (ownerRef.coreLibApiBridge == null) {
            ownerRef.coreLibApiBridge = new DefaultEmakiCoreLibApi(ownerRef);
        }
        if (ownerRef.dialogService != null) {
            if (ownerRef.dialogApiBridge == null) {
                ownerRef.dialogApiBridge = new DialogApiBridge(ownerRef.dialogService);
            }
            ownerRef.coreLibApiBridge.installDialogs(ownerRef.dialogApiBridge);
        }
        EmakiCoreLibApi.install(ownerRef.coreLibApiBridge);
    }

    void publishOwnCapabilities() {
        CapabilityRegistration registration = ownerRef.capabilityRegistry.publish(
                ownerRef,
                Set.of(ApiCapability.of("emakicorelib:itemsource_registry"))
        );
        if (!registration.successful()) {
            ownerRef.getLogger().warning("[startup] 发布自身能力失败: " + registration.reasonKey());
        }
    }

    BStatsRegistration registerBStats(JavaPlugin plugin, int pluginId) {
        if (ownerRef.bStatsService == null) {
            return BStatsRegistration.noop(plugin, pluginId);
        }
        return ownerRef.bStatsService.register(plugin, pluginId);
    }

    void logStartupAudit() {
        if (ownerRef.economyManager == null) {
            return;
        }
        for (String providerId : ownerRef.economyManager.availableProviderIds()) {
            ownerRef.messageService.info("console.economy_bridge_ready", Map.of("provider", providerId));
        }
        for (String blockProvider : new String[]{"CraftEngine", "ItemsAdder", "Nexo", "Oraxen"}) {
            if (ownerRef.getServer().getPluginManager().isPluginEnabled(blockProvider)) {
                ownerRef.messageService.info("console.block_source_bridge_ready", Map.of("provider", blockProvider));
            }
        }
    }

    Path dataPath(String first, String... more) {
        return ownerRef.getDataFolder().toPath().resolve(Path.of(first, more));
    }

    void registerCommandHandler() {
        ownerRef.commandRouter = new CoreLibCommandRouter(ownerRef, ownerRef.executionDispatcher);

        ownerRef.registerCommand(
                "emakicorelib",
                "EmakiCoreLib 命令",
                List.of("corelib", "emakicore"),
                new CoreLibBasicCommand(ownerRef.commandRouter)
        );
    }

    void runShutdownStep(String name, Runnable step) {
        try {
            step.run();
        } catch (Throwable throwable) {
            ownerRef.getLogger().warning("[shutdown] " + name + " 关闭失败: " + AsyncFailures.describe(throwable));
        }
    }

    void ensureBundledFile(String relativePath) {
        File target = new File(ownerRef.getDataFolder(), relativePath);
        try {
            boolean copied = YamlFiles.copyResourceIfMissing(ownerRef, relativePath, target);
            if (!copied && !target.exists()) {
                if (ownerRef.messageService != null) {
                    ownerRef.messageService.warning("loader.bundled_resource_missing", Map.of(
                            "type", "资源",
                            "path", target.getPath(),
                            "resource", relativePath
                    ));
                } else {
                    ownerRef.getLogger().warning("[loader] 内置资源缺失: " + relativePath);
                }
            }
        } catch (Exception exception) {
            if (ownerRef.messageService != null) {
                ownerRef.messageService.warning("loader.bundled_resource_write_failed", Map.of(
                        "path", target.getPath(),
                        "error", String.valueOf(exception.getMessage())
                ));
            } else {
                ownerRef.getLogger().warning("[loader] 写入内置资源失败 " + relativePath + ": " + exception.getMessage());
            }
        }
    }

    CoreLibConfig loadConfigModel() {
        try {
            File file = new File(ownerRef.getDataFolder(), "config.yml");
            VersionedYamlFile versionedFile = YamlFiles.pollStartupCache(file);
            if (versionedFile == null) {
                versionedFile = YamlFiles.syncVersionedResource(
                        ownerRef,
                        file,
                        "config.yml",
                        "version"
                );
            }
            ownerRef.logVersionUpdate("config.yml", versionedFile);
            return CoreLibConfig.fromConfig(versionedFile == null ? YamlFiles.load(file) : versionedFile.root());
        } catch (Exception exception) {
            if (ownerRef.messageService != null) {
                ownerRef.messageService.warning("console.action_config_load_failed", Map.of(
                        "error", String.valueOf(exception.getMessage())
                ));
            } else {
                ownerRef.getLogger().warning("[config] 加载 CoreLib 配置失败: " + exception.getMessage());
            }
            return CoreLibConfig.defaults();
        }
    }

    void logVersionUpdate(String relativePath, VersionedYamlFile versionedFile) {
        if (versionedFile == null || !versionedFile.versionUpdated()) {
            return;
        }
        if (ownerRef.messageService != null) {
            ownerRef.messageService.info("console.versioned_file_updated", Map.of(
                    "path", relativePath,
                    "old_version", versionedFile.previousVersion().isBlank() ? "未知" : versionedFile.previousVersion(),
                    "new_version", versionedFile.updatedVersion()
            ));
        } else {
            ownerRef.getLogger().info("[config] 已更新内置文件版本: " + relativePath + " ("
                    + (versionedFile.previousVersion().isBlank() ? "未知" : versionedFile.previousVersion())
                    + " -> " + versionedFile.updatedVersion() + ")");
        }
    }

    DialogService dialogService() {
        return ownerRef.dialogService;
    }

    TextDisplayService textDisplayService() {
        return ownerRef.textDisplayService;
    }

    CoreLibConfig configModel() {
        return ownerRef.configModel;
    }

    EmakiEventBus eventBus() {
        return ownerRef.eventBus;
    }

    PlaceholderRegistry placeholderRegistry() {
        return ownerRef.placeholderRegistry;
    }

    EconomyManager economyManager() {
        return ownerRef.economyManager;
    }

    StageRegistry stageRegistry() {
        return ownerRef.stageRegistry;
    }

    TriggerRegistry triggerRegistry() {
        return ownerRef.triggerRegistry;
    }

    StageRebuildListeners stageRebuildListeners() {
        return ownerRef.stageRebuildListeners;
    }

    CapabilityRegistry capabilityRegistry() {
        return ownerRef.capabilityRegistry;
    }

    ModuleReadinessRegistry moduleReadinessRegistry() {
        return ownerRef.moduleReadinessRegistry;
    }

    TargetConditionRegistry targetConditionRegistry() {
        return ownerRef.targetConditionRegistry;
    }

    TargetIdentityRegistry targetIdentityRegistry() {
        return ownerRef.targetIdentityRegistry;
    }

    void reportTargetIdentityFailure(String message) {
        ownerRef.getLogger().warning("[integration] " + message);
    }

    boolean contentReady() {
        return ownerRef.contentReady;
    }

    void logReadinessFailure(ModuleReadinessRegistry.Failure failure) {
        ownerRef.getLogger().warning("[startup] 就绪回调失败, 插件 " + failure.owner()
                + " 正在等待 " + failure.moduleName() + ": " + failure.error());
    }

    ActionEngine actionEngine() {
        return ownerRef.actionEngine;
    }

    PipelineTaskService pipelineTaskService() {
        return ownerRef.pipelineTaskService;
    }

    AnimationRegistry animationRegistry() {
        return ownerRef.animationRegistry;
    }

    AnimationPlaybackService animationPlaybackService() {
        return ownerRef.animationPlaybackService;
    }

    void buildSequenceRepository() {
        ownerRef.pipelineBatchRunner.invalidate();
        ActionEngine engine = ownerRef.actionEngine;
        if (engine == null) {
            ownerRef.sequenceRepository = ConfiguredSequenceRepository.empty();
            return;
        }
        ownerRef.sequenceRepository = ConfiguredSequenceRepository.build(ownerRef.configModel.actionTemplates(),
                (sequence, line, catalog) -> {
                    ActionEngine.Result result = engine.compile(line, null);
                    if (result.successful()) {
                        return result.pipeline();
                    }
                    String reason = result.diagnostics().isEmpty()
                            ? "未通过编译"
                            : ownerRef.messageService().renderFirstDiagnostic(result.diagnostics());
                    ownerRef.getLogger().warning("[pipeline] 序列 '" + sequence + "' 的行被拒绝: " + reason
                            + " <- " + line);
                    return null;
                });
        List<String> failed = ownerRef.sequenceRepository.failed();
        if (!failed.isEmpty()) {
            ownerRef.getLogger().warning("[pipeline] 序列不可用, 因为某行未通过编译: " + failed);
        }
        if (ownerRef.pipelineTaskService != null) {
            CoreLibConfig.LoopConfig loop = ownerRef.configModel.loopConfig();
            ownerRef.pipelineTaskService.configure(new PipelineTaskService.Limits(
                    ownerRef.configModel.pipelineConfig().maxRepeatTimes(),
                    loop == null ? 1L : Math.max(1L, loop.minSyncIntervalTicks()),
                    loop == null ? 200 : loop.maxActiveLoopsTotal(),
                    loop == null ? 10 : loop.maxActiveLoopsPerPlayer(),
                    loop == null ? 100 : loop.maxActiveLoopsPerPlugin(),
                    loop == null || loop.cancelPlayerLoopsOnQuit()));
        }
    }

    ConfigPrecheckService configPrecheckService() {
        return ownerRef.configPrecheckService;
    }

    AsyncTaskScheduler asyncTaskScheduler() {
        return ownerRef.asyncTaskScheduler;
    }

    PerformanceMonitor performanceMonitor() {
        return ownerRef.performanceMonitor;
    }

    GuiBackend guiBackend() {
        return ownerRef.guiBackend;
    }

    void registerPacketBackend() {
        try {
            PacketBackendInstaller.install(ownerRef, ownerRef.guiBackendRegistry, ownerRef.executionDispatcher);
        } catch (LinkageError | RuntimeException exception) {
            ownerRef.getLogger().warning("[gui] 注册 packet GUI 后端失败: " + exception.getMessage()
                    + ", EmakiCoreLib 将使用 Bukkit(实体)后端");
        }
    }

    AsyncYamlFiles asyncYamlFiles() {
        return ownerRef.asyncYamlFiles;
    }

    AsyncYamlFiles asyncYamlFiles(Plugin owner) {
        if (ownerRef.asyncFileService == null) {
            throw new IllegalStateException("异步文件服务不可用");
        }
        String ownerName = owner == null ? "anonymous" : owner.getName();
        return new AsyncYamlFiles(ownerRef.asyncFileService.openScope(ownerName));
    }

    AsyncFileService.FileScope asyncFileScope(Plugin owner) {
        if (ownerRef.asyncFileService == null) {
            throw new IllegalStateException("异步文件服务不可用");
        }
        String ownerName = owner == null ? "anonymous" : owner.getName();
        return ownerRef.asyncFileService.openScope(ownerName);
    }

    ExecutionDispatcher executionDispatcher() {
        return ownerRef.executionDispatcher;
    }

    ThreadOwnership threadOwnership() {
        return ownerRef.threadOwnership;
    }

    boolean registerDependentShutdown(String ownerKey, CompletionStage<?> shutdown) {
        CorePluginLifecycle lifecycle = ownerRef.corePluginLifecycle;
        return lifecycle != null && lifecycle.registerDependentShutdown(ownerKey, shutdown);
    }

    boolean registerDependentShutdownFuture(String ownerKey, Future<?> shutdown) {
        CorePluginLifecycle lifecycle = ownerRef.corePluginLifecycle;
        return lifecycle != null && lifecycle.registerDependentShutdownFuture(ownerKey, shutdown);
    }

    PdcService pdcService() {
        return ownerRef.pdcService;
    }

    VanillaTranslationService vanillaTranslationService() {
        return ownerRef.vanillaTranslationService;
    }

    void loadVanillaLanguageTableAsync(AsyncTaskScheduler scheduler) {
        CoreLibConfig currentConfig = ownerRef.configModel == null ? CoreLibConfig.defaults() : ownerRef.configModel;
        CoreLibConfig.VanillaLanguageConfig languageConfig = currentConfig.vanillaLanguageConfig();
        if (languageConfig == null || !languageConfig.enabled()) {
            return;
        }
        String locale = languageConfig.locale();
        String minecraftVersion = MinecraftServerVersions.currentServerVersion().trim();
        Path cacheDirectory = ownerRef.getDataFolder().toPath().resolve("lang-cache");
        scheduler.runAsync("corelib-vanilla-language", () -> {
            VanillaLanguageDownloader downloader =
                    new VanillaLanguageDownloader(ownerRef.getLogger(), cacheDirectory);
            Map<String, String> table = downloader.load(minecraftVersion, locale);
            if (table.isEmpty()) {
                ownerRef.getLogger().info("[text] 原版语言表('" + locale
                        + "')不可用; 需要本地化原版名称的功能将保持禁用");
                return;
            }
            ownerRef.vanillaTranslationService.install(table);
            ownerRef.getLogger().info("[text] 已加载 " + table.size() + " 条原版翻译, 语言 '" + locale + "'");
        });
    }

    ItemSourceService itemSourceService() {
        return ownerRef.itemSourceService;
    }

    ConfiguredItemService configuredItemService() {
        return ownerRef.configuredItemService;
    }

    EmakiNamespaceRegistry namespaceRegistry() {
        return ownerRef.namespaceRegistry;
    }

    EmakiItemAssemblyService itemAssemblyService() {
        return ownerRef.itemAssemblyService;
    }

    CraftEngineBlockBridge craftEngineBlockBridge() {
        return ownerRef.craftEngineBlockBridge;
    }

    CustomBlockBridge itemsAdderBlockBridge() {
        return ownerRef.itemsAdderBlockBridge;
    }

    CustomBlockBridge nexoBlockBridge() {
        return ownerRef.nexoBlockBridge;
    }

    MythicMobBridge mythicMobBridge() {
        return ownerRef.mythicMobBridge;
    }

    CustomBlockBridge oraxenBlockBridge() {
        return ownerRef.oraxenBlockBridge;
    }

    FurnitureBridge craftEngineFurnitureBridge() {
        return ownerRef.craftEngineFurnitureBridge;
    }

    FurnitureBridge itemsAdderFurnitureBridge() {
        return ownerRef.itemsAdderFurnitureBridge;
    }

    FurnitureBridge nexoFurnitureBridge() {
        return ownerRef.nexoFurnitureBridge;
    }

    FurnitureBridge oraxenFurnitureBridge() {
        return ownerRef.oraxenFurnitureBridge;
    }

    FurnitureEventRelay furnitureEventRelay() {
        return ownerRef.furnitureEventRelay;
    }

    DebugLogger debugLogger() {
        return ownerRef.debugLogger;
    }

    <T> void registerService(Class<T> type, T service) {
        if (service != null) {
            ownerRef.serviceRegistry.put(type, service);
        }
    }

}
