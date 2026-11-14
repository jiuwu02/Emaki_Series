package emaki.jiuwu.craft.forge;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.api.assembly.EmakiNamespaceDefinition;
import emaki.jiuwu.craft.corelib.api.async.AsyncFailures;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.bootstrap.ConfigKeyMigration;
import emaki.jiuwu.craft.corelib.condition.ConditionBlock;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.forge.integration.ForgeAttributeBridge;
import emaki.jiuwu.craft.forge.integration.ForgeAttributeBridgeHolder;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.runtime.AbstractLifecycleCoordinator;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.AsyncYamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.chat.ChatInputService;
import emaki.jiuwu.craft.corelib.editor.EditorService;
import emaki.jiuwu.craft.forge.config.AppConfig;
import emaki.jiuwu.craft.forge.editor.ForgeEditorModule;
import emaki.jiuwu.craft.forge.loader.PlayerDataStore;
import emaki.jiuwu.craft.forge.loader.RecipeLoader;
import emaki.jiuwu.craft.forge.loader.ForgeGuiTemplateLoader;
import emaki.jiuwu.craft.forge.model.Recipe;
import emaki.jiuwu.craft.forge.service.ForgeGuiService;
import emaki.jiuwu.craft.forge.service.ForgeItemRefreshService;
import emaki.jiuwu.craft.forge.service.ForgeLookupIndex;
import emaki.jiuwu.craft.forge.service.ForgeService;
import emaki.jiuwu.craft.forge.service.ItemIdentifierService;
import emaki.jiuwu.craft.forge.service.RecipeBookGuiService;
import emaki.jiuwu.craft.forge.model.QualitySettings;
import emaki.jiuwu.craft.forge.service.ForgeGuiSession;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;

final class ForgeLifecycleCoordinator extends AbstractLifecycleCoordinator<EmakiForgePlugin, ForgeRuntimeComponents> {

    private static final String DEFAULT_PREFIX = "<gray>[ <gradient:#F59E0B:#EF4444>EmakiForge</gradient> ]</gray>";
    private static final String PDC_ATTRIBUTE_SOURCE_ID = "forge";
    static final List<String> VERSIONED_FILES = List.of("config.yml", "lang/zh_CN.yml", "lang/en_US.yml");
    static final List<String> STATIC_FILES = List.of("gui/forge_gui.yml", "gui/recipe_book.yml",
            "gui/editor_page_gui.yml", "gui/editor_browser_gui.yml");
    static final List<String> DEFAULT_DATA_FILES = List.of("recipes/example_recipe.yml",
            "recipes/example_weapon_recipe.yml",
            "recipes/example_armor_recipe.yml",
            "recipes/example_guarantee_recipe.yml",
            "recipes/example_failure_recipe.yml",
            "recipes/example_optional_materials_recipe.yml");
    static final List<String> EXTRA_DIRECTORIES = List.of("data");
    static final List<ConfigKeyMigration.Rename> CONFIG_RENAMES = List.of(
            new ConfigKeyMigration.Rename("permission.op_bypass", "op_bypass"));
    static final long SHUTDOWN_SETTLEMENT_DELAY_TICKS = 100L;
    private static final long SHUTDOWN_RETIREMENT_TIMEOUT_SECONDS = 10L;

    private final AtomicLong autoSaveGeneration = new AtomicLong();
    private final AtomicLong requestedGeneration = new AtomicLong();
    private final AtomicBoolean playerStorePrepared = new AtomicBoolean();
    private final AtomicBoolean reloadsAccepted = new AtomicBoolean(true);
    private final Object reloadLock = new Object();
    private CompletableFuture<Void> reloadTail = CompletableFuture.completedFuture(null);

    record CandidatePreparation(
            long generation,
            long startedNanos,
            YamlConfigLoader<AppConfig> appConfigLoader,
            LanguageLoader languageLoader,
            MessageService messageService,
            BootstrapService bootstrapService,
            RecipeLoader recipeLoader,
            ForgeGuiTemplateLoader guiTemplateLoader,
            List<File> recipeFiles,
            List<File> guiFiles) {
    }

    record CandidateDocuments(
            List<RecipeLoader.CandidateDocument> recipeDocuments,
            List<RecipeLoader.CandidateDocument> guiDocuments) {
    }

    record RuntimeValidatedCandidate(
            CandidatePreparation preparation,
            RecipeLoader.RecipeLoadReport report,
            Map<String, Recipe> recipes,
            ForgeService forgeService) {
    }

    @Override
    public ForgeRuntimeComponents initialize(EmakiForgePlugin plugin) {
        reloadsAccepted.set(true);
        EmakiCoreLibPlugin coreLibPlugin = EmakiCoreLibApi.requireService(EmakiCoreLibPlugin.class);
        registerAssemblyLayer(coreLibPlugin);
        YamlConfigLoader<AppConfig> appConfigLoader = new YamlConfigLoader<>(
                plugin,
                "config.yml",
                AppConfig::defaults,
                this::parseAppConfig
        );
        appConfigLoader.load();
        LanguageLoader languageLoader = moduleLanguageLoader(plugin);
        RecipeLoader recipeLoader = new RecipeLoader(plugin, coreLibPlugin::actionEngine);
        GuiTemplateLoader guiTemplateLoader = new GuiTemplateLoader(plugin);
        AsyncYamlFiles playerDataFiles = coreLibPlugin.asyncYamlFiles(plugin);
        PlayerDataStore playerDataStore = new PlayerDataStore(plugin, () -> playerDataFiles);
        MessageService messageService = new MessageService(plugin, languageLoader, DEFAULT_PREFIX, true);
        languageLoader.load();
        BootstrapService bootstrapService = createBootstrapService(plugin, messageService, appConfigLoader);
        ExecutionDispatcher executionDispatcher = EmakiCoreLibApi.requireService(ExecutionDispatcher.class);
        var threadOwnership = EmakiCoreLibApi.requireService(ThreadOwnership.class);
        GuiService guiService = new GuiService(plugin, executionDispatcher, coreLibPlugin.asyncTaskScheduler(),
                coreLibPlugin.performanceMonitor(), coreLibPlugin.guiBackend());
        ItemIdentifierService itemIdentifierService = new ItemIdentifierService(plugin, coreLibPlugin.itemSourceService());
        ForgeAttributeBridge pdcAttributeGateway = new ForgeAttributeBridgeHolder(plugin.getLogger());
        pdcAttributeGateway.syncRegistration(PDC_ATTRIBUTE_SOURCE_ID);
        ForgeService forgeService = new ForgeService(
                plugin,
                coreLibPlugin.asyncTaskScheduler(),
                coreLibPlugin.performanceMonitor(),
                coreLibPlugin.itemAssemblyService(),
                plugin.actionLines(),
                executionDispatcher,
                threadOwnership,
                coreLibPlugin.placeholderRegistry()
        );
        ForgeItemRefreshService itemRefreshService = new ForgeItemRefreshService(
                plugin,
                coreLibPlugin.itemAssemblyService(),
                executionDispatcher,
                coreLibPlugin.placeholderRegistry()
        );
        ForgeGuiService forgeGuiService = new ForgeGuiService(plugin, guiService, executionDispatcher, threadOwnership);
        RecipeBookGuiService recipeBookGuiService = new RecipeBookGuiService(plugin, guiService);
        ChatInputService chatInputService =
                new emaki.jiuwu.craft.corelib.chat.ChatInputService(plugin, executionDispatcher);
        GuiTemplateLoader editorTemplateLoader = new GuiTemplateLoader(plugin);
        ForgeEditorModule editorModule =
                new emaki.jiuwu.craft.forge.editor.ForgeEditorModule(plugin, editorTemplateLoader);
        EditorService editorService =
                new emaki.jiuwu.craft.corelib.editor.EditorService(plugin, guiService, editorTemplateLoader,
                        messageService, chatInputService, editorModule);
        editorModule.bind(editorService);
        return new ForgeRuntimeComponents(
                appConfigLoader,
                executionDispatcher,
                threadOwnership,
                languageLoader,
                recipeLoader,
                guiTemplateLoader,
                playerDataStore,
                messageService,
                bootstrapService,
                guiService,
                itemIdentifierService,
                pdcAttributeGateway,
                itemRefreshService,
                forgeService,
                forgeGuiService,
                recipeBookGuiService,
                chatInputService,
                editorModule,
                editorService
        );
    }

    public long requestedGeneration() {
        return requestedGeneration.get();
    }

    public CompletableFuture<ForgeReloadResult> reloadAsync(EmakiForgePlugin plugin,
                                                            boolean closeOpenInventories,
                                                            Consumer<String> progressListener) {
        synchronized (reloadLock) {
            if (!reloadsAccepted.get() || plugin.isShutdownStarted()) {
                ForgeReloadResult result = new ForgeReloadResult(
                        requestedGeneration.get(),
                        plugin.runtimeGeneration(),
                        plugin.runtimeStatus() == ForgeRuntimeStatus.UNAVAILABLE
                                ? ForgeReloadResult.Outcome.FAILED_UNAVAILABLE
                                : ForgeReloadResult.Outcome.FAILED_PRESERVED,
                        plugin.recipeLoader() == null ? 0 : plugin.recipeLoader().all().size(),
                        plugin.guiTemplateLoader() == null ? 0 : plugin.guiTemplateLoader().all().size(),
                        ForgeItemRefreshService.RefreshSummary.empty(),
                        plugin.runtimeSnapshot().recipeReport(),
                        "插件已开始关闭, 重载被拒绝",
                        0L);
                plugin.runtimeMetrics().recordResult(result);
                return CompletableFuture.completedFuture(result);
            }
            long generation = requestedGeneration.incrementAndGet();
            plugin.runtimeMetrics().recordCandidate();
            CompletableFuture<ForgeReloadResult> result;
            result = reloadTail.handle((ignored, throwable) -> null)
                    .thenCompose(ignored -> executeReload(plugin, generation, closeOpenInventories, progressListener));
            reloadTail = result.handle((ignored, throwable) -> null);
            return result;
        }
    }

    private CompletableFuture<Void> retireReloads() {
        synchronized (reloadLock) {
            reloadsAccepted.set(false);
            requestedGeneration.incrementAndGet();
            return reloadTail.handle((ignored, throwable) -> null);
        }
    }

    private CompletableFuture<ForgeReloadResult> executeReload(EmakiForgePlugin plugin,
                                                               long generation,
                                                               boolean closeOpenInventories,
                                                               Consumer<String> progressListener) {
        long started = System.nanoTime();
        notifyProgress(progressListener, "正在构建 Forge 运行时代次 " + generation + "...");
        CompletableFuture<ForgeReloadResult> pipeline = submitGlobal(plugin, () -> {
            plugin.beginReload(generation);
            plugin.itemIdentifierService().refresh();
            return null;
        }).thenCompose(ignored -> buildCandidateAsync(plugin, generation))
                .thenCompose(candidate -> {
                    if (!plugin.isGenerationRequested(generation)) {
                        return staleReload(plugin, generation, started, "安装前已被取代");
                    }
                    if (candidate.blocking()) {
                        return rejectedReload(plugin, candidate, started, "候选包含阻断性问题");
                    }
                    return installCandidate(plugin, candidate, closeOpenInventories, started, progressListener);
                });
        return pipeline.handle((result, throwable) -> {
            if (throwable == null) {
                return CompletableFuture.completedFuture(result);
            }
            Throwable cause = AsyncFailures.unwrap(throwable);
            if (cause instanceof StaleReloadException) {
                return staleReload(plugin, generation, started, "重载期间已被取代");
            }
            return submitGlobal(plugin, () -> {
                plugin.failReload(generation, "重载失败: " + cause.getMessage());
                plugin.messageService().warning("console.reload_generation_failed",
                        Map.of("generation", generation, "error", String.valueOf(cause.getMessage())));
                ForgeReloadResult reloadResult = new ForgeReloadResult(
                        generation,
                        plugin.runtimeGeneration(),
                        plugin.runtimeStatus() == ForgeRuntimeStatus.UNAVAILABLE
                                ? ForgeReloadResult.Outcome.FAILED_UNAVAILABLE
                                : ForgeReloadResult.Outcome.FAILED_PRESERVED,
                        plugin.recipeLoader() == null ? 0 : plugin.recipeLoader().all().size(),
                        plugin.guiTemplateLoader() == null ? 0 : plugin.guiTemplateLoader().all().size(),
                        ForgeItemRefreshService.RefreshSummary.empty(),
                        RecipeLoader.RecipeLoadReport.empty(generation),
                        cause.getMessage(),
                        System.nanoTime() - started
                );
                plugin.runtimeMetrics().recordResult(reloadResult);
                return reloadResult;
            });
        }).thenCompose(stage -> stage);
    }

    private CompletableFuture<ForgeReloadCandidate> buildCandidateAsync(EmakiForgePlugin plugin, long generation) {
        EmakiCoreLibPlugin coreLib = plugin.coreLib();
        AsyncTaskScheduler scheduler = coreLib.asyncTaskScheduler();
        return scheduler.supplyAsync(
                        "forge-reload-parse-" + generation,
                        () -> prepareCandidateDocuments(plugin, coreLib, generation))
                .thenCompose(preparation -> {
                    if (plugin.isShutdownStarted() || !plugin.isGenerationRequested(generation)) {
                        return CompletableFuture.failedFuture(new StaleReloadException());
                    }
                    return submitGlobal(plugin, () -> runRuntimeValidation(plugin, preparation));
                })
                .thenCompose(validation -> scheduler.supplyAsync(
                        "forge-reload-index-" + generation,
                        () -> buildCandidate(plugin, validation)));
    }

    CandidatePreparation prepareCandidateDocuments(EmakiForgePlugin plugin,
                                                            EmakiCoreLibPlugin coreLib,
                                                            long generation) {
        CandidatePreparation preparation = prepareCandidate(plugin, coreLib, generation);
        if (plugin.isShutdownStarted() || !plugin.isGenerationRequested(generation)) {
            throw new StaleReloadException();
        }
        CandidateDocuments documents = new CandidateDocuments(
                readCandidateDocuments(preparation.recipeFiles()),
                readCandidateDocuments(preparation.guiFiles()));
        if (plugin.isShutdownStarted() || !plugin.isGenerationRequested(generation)) {
            throw new StaleReloadException();
        }
        preparation.recipeLoader().loadCandidateDocuments(documents.recipeDocuments());
        preparation.guiTemplateLoader().loadCandidateDocuments(documents.guiDocuments());
        if (plugin.isShutdownStarted() || !plugin.isGenerationRequested(generation)) {
            throw new StaleReloadException();
        }
        return preparation;
    }

    CandidatePreparation prepareCandidate(EmakiForgePlugin plugin,
                                                   EmakiCoreLibPlugin coreLib,
                                                   long generation) {
        if (plugin.isShutdownStarted() || !plugin.isGenerationRequested(generation)) {
            throw new StaleReloadException();
        }
        long startedNanos = System.nanoTime();
        YamlConfigLoader<AppConfig> appConfigLoader = new YamlConfigLoader<>(
                plugin,
                "config.yml",
                AppConfig::defaults,
                this::parseAppConfig
        );
        LanguageLoader languageLoader = moduleLanguageLoader(plugin);
        loadCandidateAppConfigReadOnly(appConfigLoader);
        validateCandidateLanguagesReadOnly(new File(plugin.getDataFolder(), "lang"));
        MessageService messageService = new MessageService(plugin, languageLoader, DEFAULT_PREFIX, true);
        BootstrapService bootstrapService = createBootstrapService(plugin, messageService, appConfigLoader);
        RecipeLoader recipeLoader = new RecipeLoader(plugin, coreLib::actionEngine,
                plugin.itemIdentifierService(), true);
        ForgeGuiTemplateLoader guiTemplateLoader = new ForgeGuiTemplateLoader(
                plugin, plugin.itemIdentifierService(), true);
        recipeLoader.prepareLoad(generation);
        File recipeDirectory = new File(plugin.getDataFolder(), "recipes");
        File guiDirectory = new File(plugin.getDataFolder(), "gui");
        List<File> recipeFiles = discoverYamlFiles(recipeDirectory);
        List<File> guiFiles = discoverYamlFiles(guiDirectory);
        guiTemplateLoader.prepareCandidateFiles(guiDirectory, guiFiles);
        return new CandidatePreparation(
                generation,
                startedNanos,
                appConfigLoader,
                languageLoader,
                messageService,
                bootstrapService,
                recipeLoader,
                guiTemplateLoader,
                recipeFiles,
                guiFiles);
    }

    private void loadCandidateAppConfigReadOnly(YamlConfigLoader<AppConfig> loader) { new ForgeLifecycleCoordinatorDelegate(this).loadCandidateAppConfigReadOnly(loader); }

    private void validateCandidateLanguagesReadOnly(File directory) { new ForgeLifecycleCoordinatorDelegate(this).validateCandidateLanguagesReadOnly(directory); }

    RuntimeValidatedCandidate runRuntimeValidation(EmakiForgePlugin plugin,
                                                            CandidatePreparation preparation) {
        long generation = preparation.generation();
        if (plugin.isShutdownStarted() || !plugin.isGenerationRequested(generation)) {
            throw new StaleReloadException();
        }
        preparation.recipeLoader().completeDeferredRuntimeValidation();
        preparation.guiTemplateLoader().completeDeferredRuntimeValidation();
        RecipeLoader.RecipeLoadReport report = preparation.recipeLoader().completeReport(
                preparation.guiTemplateLoader());
        if (plugin.isShutdownStarted() || !plugin.isGenerationRequested(generation)) {
            throw new StaleReloadException();
        }
        ForgeService forgeService = plugin.forgeService();
        if (forgeService == null) {
            throw new IllegalStateException("候选校验期间 Forge 服务不可用.");
        }
        return new RuntimeValidatedCandidate(
                preparation,
                report,
                preparation.recipeLoader().all(),
                forgeService);
    }

    private ForgeReloadCandidate buildCandidate(EmakiForgePlugin plugin,
                                                RuntimeValidatedCandidate validation) {
        CandidatePreparation preparation = validation.preparation();
        long generation = preparation.generation();
        if (plugin.isShutdownStarted() || !plugin.isGenerationRequested(generation)) {
            throw new StaleReloadException();
        }
        ForgeLookupIndex.Snapshot lookupSnapshot = validation.forgeService().buildLookupSnapshot(
                generation, validation.recipes(), validation.report());
        if (plugin.isShutdownStarted() || !plugin.isGenerationRequested(generation)) {
            throw new StaleReloadException();
        }
        return new ForgeReloadCandidate(
                generation,
                preparation.appConfigLoader(),
                preparation.languageLoader(),
                preparation.messageService(),
                preparation.bootstrapService(),
                preparation.recipeLoader(),
                preparation.guiTemplateLoader(),
                validation.report(),
                lookupSnapshot,
                System.nanoTime() - preparation.startedNanos());
    }

    private List<RecipeLoader.CandidateDocument> readCandidateDocuments(List<File> files) { return new ForgeLifecycleCoordinatorDelegate(this).readCandidateDocuments(files); }

    private List<File> discoverYamlFiles(File directory) { return new ForgeLifecycleCoordinatorDelegate(this).discoverYamlFiles(directory); }

    private BootstrapService createBootstrapService(EmakiForgePlugin plugin,
            MessageService messageService,
            YamlConfigLoader<AppConfig> appConfigLoader) {
        return new BootstrapService(
                plugin,
                messageService,
                VERSIONED_FILES,
                STATIC_FILES,
                DEFAULT_DATA_FILES,
                EXTRA_DIRECTORIES,
                bootstrapHooks(releaseDefaultData(appConfigLoader, AppConfig::releaseDefaultData),
                        (relativePath, runtime, bundled) -> {
                            if (!"config.yml".equals(relativePath)) {
                                return;
                            }
                            ConfigKeyMigration.applyRenames(runtime, bundled, CONFIG_RENAMES, plugin.getLogger());
                            ConfigKeyMigration.pruneEmptySection(runtime, "permission");
                        },
                        null)
        );
    }

    private void finalizeCandidateConfiguration(EmakiForgePlugin plugin, ForgeReloadCandidate candidate) {
        if (plugin == null || candidate == null
                || candidate.appConfigLoader() == null || candidate.languageLoader() == null) {
            throw new IllegalStateException("Forge 候选配置不完整.");
        }
        if (playerStorePrepared.compareAndSet(false, true)) {
            try {
                plugin.playerDataStore().load();
            } catch (RuntimeException | Error failure) {
                playerStorePrepared.set(false);
                throw failure;
            }
        }
        AppConfig validatedConfig = candidate.appConfigLoader().current();
        candidate.appConfigLoader().load();
        candidate.appConfigLoader().overrideCurrent(validatedConfig == null ? AppConfig.defaults() : validatedConfig);
        candidate.languageLoader().load();
        candidate.languageLoader().setLanguage(candidate.appConfigLoader().current().language());
    }

    private CompletableFuture<ForgeReloadResult> installCandidate(EmakiForgePlugin plugin,
                                                                  ForgeReloadCandidate candidate,
                                                                  boolean closeOpenInventories,
                                                                  long started,
                                                                  Consumer<String> progressListener) {
        long generation = candidate.generation();
        notifyProgress(progressListener, "正在静默 Forge 代次 " + plugin.runtimeGeneration() + "...");
        return submitGlobal(plugin, () -> {
            if (!plugin.isGenerationRequested(generation)) {
                throw new StaleReloadException();
            }
            return plugin.forgeService().quiesce();
        }).thenCompose(stage -> stage)
                .thenCompose(ignored -> closeOpenInventories || plugin.runtimeGeneration() > 0L
                        ? closeOpenInventoriesAsync(plugin)
                        : CompletableFuture.completedFuture(null))
                .thenCompose(ignored -> submitGlobal(plugin, () -> {
                    synchronized (reloadLock) {
                        if (!plugin.isGenerationRequested(generation)) {
                            throw new StaleReloadException();
                        }
                        finalizeCandidateConfiguration(plugin, candidate);
                        plugin.pdcAttributeGateway().syncRegistration(PDC_ATTRIBUTE_SOURCE_ID);
                        plugin.installCandidate(candidate);
                    }
                    plugin.messageService().info("console.pdc_source_registered", Map.of(
                            "source", PDC_ATTRIBUTE_SOURCE_ID));
                    plugin.messageService().info("console.recipes_loaded", Map.of(
                            "count", String.valueOf(candidate.recipeLoader().all().size())));
                    return null;
                }))
                .thenCompose(ignored -> plugin.itemRefreshService() == null
                        ? CompletableFuture.completedFuture(new ForgeItemRefreshService.RefreshSummary(
                        generation, 0, 0, 0, 0, false, 0L))
                        : plugin.itemRefreshService().refreshOnlinePlayers(generation))
                .thenCompose(refresh -> submitGlobal(plugin, () -> {
                    boolean generationMismatch = refresh.stale()
                            || !plugin.isGenerationActive(generation)
                            || !plugin.isGenerationRequested(generation);
                    if (!generationMismatch && !plugin.completeCandidateInstallation(generation)) {
                        generationMismatch = true;
                    }
                    ForgeReloadResult.Outcome outcome;
                    if (generationMismatch) {
                        outcome = ForgeReloadResult.Outcome.STALE;
                    } else if (refresh.failed() > 0) {
                        outcome = ForgeReloadResult.Outcome.FAILED;
                    } else if (candidate.recipeReport().hasWarnings()) {
                        outcome = ForgeReloadResult.Outcome.WARNING;
                    } else {
                        outcome = ForgeReloadResult.Outcome.SUCCESS;
                    }
                    notifyProgress(progressListener, "重载完成: 代次 " + generation
                            + ", 结果=" + outcome + ".");
                    ForgeReloadResult result = new ForgeReloadResult(
                            generation,
                            plugin.runtimeGeneration(),
                            outcome,
                            candidate.recipeLoader().all().size(),
                            candidate.guiTemplateLoader().all().size(),
                            refresh,
                            candidate.recipeReport(),
                            "players=" + refresh.players() + ", refreshed=" + refresh.refreshed()
                                    + ", skipped=" + refresh.skipped()
                                    + ", failed=" + refresh.failed()
                                    + ", generationMismatch=" + generationMismatch,
                            System.nanoTime() - started
                    );
                    plugin.runtimeMetrics().recordResult(result);
                    return result;
                }));
    }

    private CompletableFuture<ForgeReloadResult> rejectedReload(EmakiForgePlugin plugin,
                                                                ForgeReloadCandidate candidate,
                                                                long started,
                                                                String detail) { return new ForgeLifecycleCoordinatorDelegate(this).rejectedReload(plugin, candidate, started, detail); }

    private CompletableFuture<ForgeReloadResult> staleReload(EmakiForgePlugin plugin,
                                                             long generation,
                                                             long started,
                                                             String detail) { return new ForgeLifecycleCoordinatorDelegate(this).staleReload(plugin, generation, started, detail); }

    public TaskToken rescheduleAutoSave(EmakiForgePlugin plugin, TaskToken currentTask) {
        cancelAutoSave(currentTask);
        AppConfig config = plugin.appConfig();
        if (!config.historyEnabled() || !config.historyAutoSave()) {
            autoSaveGeneration.incrementAndGet();
            return null;
        }
        long generation = autoSaveGeneration.incrementAndGet();
        return plugin.executionDispatcher().runGlobalTimer(
                plugin,
                () -> {
                    if (autoSaveGeneration.get() == generation && plugin.isRuntimeReady()) {
                        plugin.playerDataStore().saveAllAsync();
                    }
                },
                config.historySaveInterval(),
                config.historySaveInterval()
        );
    }

    public TaskToken cancelAutoSave(TaskToken currentTask) {
        autoSaveGeneration.incrementAndGet();
        if (currentTask != null && !currentTask.cancelled()) {
            currentTask.cancel();
        }
        return null;
    }

    public CompletableFuture<Void> shutdownAsync(EmakiForgePlugin plugin, EmakiCoreLibPlugin coreLibPlugin) {
        if (coreLibPlugin != null) {
            try {
                coreLibPlugin.namespaceRegistry().unregister("forge");
            } catch (Throwable throwable) {
                plugin.getLogger().warning("[shutdown] 命名空间清理失败: "
                        + String.valueOf(throwable.getMessage()));
            }
        }
        sendShutdownMessage(plugin, "console.plugin_stopping");
        if (plugin.pdcAttributeGateway() != null) {
            try {
                plugin.pdcAttributeGateway().shutdown();
            } catch (Throwable throwable) {
                plugin.getLogger().warning("[shutdown] PDC 网关清理失败: "
                        + String.valueOf(throwable.getMessage()));
            }
        }
        if (plugin.playerDataStore() == null) {
            sendShutdownMessage(plugin, "console.plugin_stopped");
            return CompletableFuture.completedFuture(null);
        }
        sendShutdownMessage(plugin, "console.saving_player_data");
        CompletableFuture<PlayerDataStore.FlushResult> flushFuture;
        try {
            flushFuture = plugin.playerDataStore().flushAndSealAsync(5L, TimeUnit.SECONDS);
        } catch (Throwable throwable) {
            plugin.getLogger().warning("[shutdown] 玩家数据刷写无法启动: "
                    + throwable.getClass().getSimpleName() + ": " + String.valueOf(throwable.getMessage()));
            sendShutdownMessage(plugin, "console.plugin_stopped");
            return CompletableFuture.completedFuture(null);
        }
        return flushFuture.handle((flushResult, throwable) -> {
            if (throwable != null) {
                Throwable cause = AsyncFailures.unwrap(throwable);
                plugin.getLogger().warning("[shutdown] 玩家数据刷写失败: "
                        + cause.getClass().getSimpleName() + ": " + String.valueOf(cause.getMessage()));
            } else if (flushResult != null) {
                sendShutdownMessage(plugin, "console.player_data_saved", Map.of(
                        "count", flushResult.savedEntries()));
                if (!flushResult.clean()) {
                    plugin.getLogger().warning("[shutdown] 玩家数据排空不完整: pending="
                            + flushResult.drainResult().pendingOperations()
                            + ", ioFailures=" + flushResult.drainResult().failures().size()
                            + ", saveFailures=" + flushResult.failedEntries()
                            + ", remainingDirty=" + flushResult.remainingDirtyEntries());
                }
            }
            sendShutdownMessage(plugin, "console.plugin_stopped");
            return null;
        });
    }

    void sendShutdownMessage(EmakiForgePlugin plugin, String key) { new ForgeLifecycleCoordinatorDelegate(this).sendShutdownMessage(plugin, key); }

    void sendShutdownMessage(EmakiForgePlugin plugin, String key, Map<String, ?> replacements) { new ForgeLifecycleCoordinatorDelegate(this).sendShutdownMessage(plugin, key, replacements); }

    <T> CompletableFuture<T> submitGlobal(EmakiForgePlugin plugin,
            Supplier<T> operation) {
        return submitGlobal(plugin, plugin, operation);
    }

    <T> CompletableFuture<T> submitGlobal(EmakiForgePlugin plugin,
            Plugin taskOwner,
            Supplier<T> operation) {
        return plugin.executionDispatcher().submitGlobal(taskOwner, operation);
    }

    CompletableFuture<Void> quiesceAndCloseForShutdown(EmakiForgePlugin plugin,
            EmakiCoreLibPlugin coreLibPlugin) {
        if (plugin == null) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> quiesceFuture = plugin.forgeService() == null
                ? CompletableFuture.completedFuture(null)
                : plugin.forgeService().quiesce();
        CompletableFuture<Void> reloadFuture = retireReloads();
        CompletableFuture<Void> closureFuture = scheduleShutdownInventoryClosures(
                plugin, coreLibPlugin, quiesceFuture);
        return CompletableFuture.allOf(reloadFuture, quiesceFuture, closureFuture)
                .orTimeout(SHUTDOWN_RETIREMENT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .handle((ignored, throwable) -> {
                    if (throwable != null) {
                        Throwable cause = AsyncFailures.unwrap(throwable);
                        plugin.runtimeMetrics().recordGuiSettlementFailure();
                        plugin.getLogger().warning("[shutdown] Forge 退役未干净完成: "
                                + cause.getClass().getSimpleName() + ": " + String.valueOf(cause.getMessage()));
                    }
                    return throwable == null;
                })
                .thenAccept(cleanRetirement -> {
                    String reason = cleanRetirement
                            ? "关闭清理发现未结算的 GUI 会话"
                            : "关闭退役在 GUI 结算前超时";
                    try {
                        plugin.forgeGuiService().finalizeShutdownSessions(reason);
                    } finally {
                        closeRuntimeForShutdown(plugin);
                    }
                })
                .handle((ignored, throwable) -> {
                    if (throwable != null) {
                        Throwable cause = AsyncFailures.unwrap(throwable);
                        plugin.getLogger().warning("[shutdown] Forge 运行时清理失败: "
                                + cause.getClass().getSimpleName() + ": " + String.valueOf(cause.getMessage()));
                        closeRuntimeForShutdown(plugin);
                    }
                    return null;
                });
    }

    private void closeRuntimeForShutdown(EmakiForgePlugin plugin) { new ForgeLifecycleCoordinatorDelegate(this).closeRuntimeForShutdown(plugin); }

    private CompletableFuture<Void> closeOpenInventoriesAsync(EmakiForgePlugin plugin) {
        return scheduleInventoryClosures(plugin)
                .thenCompose(ignored -> submitGlobal(plugin, () -> {
                    plugin.forgeGuiService().clearSettledSessions();
                    if (!plugin.forgeGuiService().sessionsSnapshot().isEmpty()) {
                        throw new IllegalStateException(
                                "GUI 物品结算未解决时无法安装 Forge 运行时重载.");
                    }
                    plugin.recipeBookGuiService().clearAllBooks();
                    return null;
                }));
    }

    private CompletableFuture<Void> scheduleShutdownInventoryClosures(EmakiForgePlugin plugin,
            EmakiCoreLibPlugin coreLibPlugin,
            CompletableFuture<Void> quiesceFuture) {
        List<CompletableFuture<Void>> closures = new ArrayList<>();
        for (var session : plugin.forgeGuiService().sessionsSnapshot()) {
            session.markShutdownRetiring();
            Player player = session.player();
            if (player == null) {
                plugin.forgeGuiService().handleShutdownClosureFailure(session,
                        "关闭会话没有玩家所有者");
                continue;
            }
            CompletableFuture<Void> closure = new CompletableFuture<>();
            closures.add(closure);
            Runnable close = () -> {
                try {
                    if (!session.processing()) {
                        plugin.forgeGuiService().settleShutdownSessionOnOwner(session);
                        player.closeInventory();
                    }
                } catch (Throwable throwable) {
                    plugin.getLogger().warning("[shutdown] Forge GUI 关闭失败, 玩家 "
                            + player.getUniqueId() + ": " + String.valueOf(throwable.getMessage()));
                    plugin.forgeGuiService().handleShutdownClosureFailure(session,
                            "关闭背包时失败: " + String.valueOf(throwable.getMessage()));
                } finally {
                    closure.complete(null);
                }
            };
            Runnable retired = () -> {
                plugin.forgeGuiService().handleShutdownClosureFailure(session,
                        "关闭背包前玩家所有者已退役");
                closure.complete(null);
            };
            scheduleShutdownEntityTask(plugin, coreLibPlugin, player, close, retired, closure,
                    "Forge GUI 关闭");
            CompletableFuture<Void> settlementFallback = new CompletableFuture<>();
            closures.add(settlementFallback);
            scheduleShutdownSettlementFallback(plugin, coreLibPlugin, player, session,
                    quiesceFuture, settlementFallback);
        }
        for (Player player : plugin.recipeBookGuiService().openPlayersSnapshot()) {
            if (player == null || plugin.forgeGuiService().getSession(player) != null) {
                continue;
            }
            CompletableFuture<Void> closure = new CompletableFuture<>();
            closures.add(closure);
            Runnable close = () -> {
                try {
                    plugin.recipeBookGuiService().removeRecipeBook(player);
                    player.closeInventory();
                } catch (Throwable throwable) {
                    plugin.getLogger().warning("[shutdown] 配方图鉴关闭失败, 玩家 "
                            + player.getUniqueId() + ": " + String.valueOf(throwable.getMessage()));
                } finally {
                    closure.complete(null);
                }
            };
            Runnable retired = () -> {
                plugin.recipeBookGuiService().removeRecipeBookOwner(player);
                plugin.getLogger().warning("[shutdown] 配方图鉴所有者已在关闭背包前退役");
                closure.complete(null);
            };
            scheduleShutdownEntityTask(plugin, coreLibPlugin, player, close, retired, closure,
                    "配方图鉴关闭");
        }
        return CompletableFuture.allOf(closures.toArray(CompletableFuture[]::new));
    }

    private void scheduleShutdownSettlementFallback(EmakiForgePlugin plugin,
            EmakiCoreLibPlugin coreLibPlugin,
            Player player,
            ForgeGuiSession session,
            CompletableFuture<Void> quiesceFuture,
            CompletableFuture<Void> completion) { new ForgeLifecycleCoordinatorDelegate(this).scheduleShutdownSettlementFallback(plugin, coreLibPlugin, player, session, quiesceFuture, completion); }

    void scheduleShutdownEntityTask(EmakiForgePlugin plugin,
            EmakiCoreLibPlugin coreLibPlugin,
            Player player,
            Runnable operation,
            Runnable retired,
            CompletableFuture<Void> completion,
            String description) { new ForgeLifecycleCoordinatorDelegate(this).scheduleShutdownEntityTask(plugin, coreLibPlugin, player, operation, retired, completion, description); }

    private CompletableFuture<Void> scheduleInventoryClosures(EmakiForgePlugin plugin) { return new ForgeLifecycleCoordinatorDelegate(this).scheduleInventoryClosures(plugin); }

    AppConfig parseAppConfig(YamlSection configuration) { return new ForgeLifecycleCoordinatorDelegate(this).parseAppConfig(configuration); }
    boolean resolveOpBypass(YamlSection configuration, YamlSection permission) { return new ForgeLifecycleCoordinatorDelegate(this).resolveOpBypass(configuration, permission); }
    private void registerAssemblyLayer(EmakiCoreLibPlugin coreLibPlugin) { new ForgeLifecycleCoordinatorDelegate(this).registerAssemblyLayer(coreLibPlugin); }
    static final class StaleReloadException extends RuntimeException {
        StaleReloadException() {
            super("重载代次已过期");
        }
    }
}