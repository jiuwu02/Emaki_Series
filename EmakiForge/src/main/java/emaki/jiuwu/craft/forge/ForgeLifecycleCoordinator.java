package emaki.jiuwu.craft.forge;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.assembly.EmakiNamespaceDefinition;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapHooks;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.condition.ConditionBlock;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.TaskHandle;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.forge.integration.ForgeAttributeBridge;
import emaki.jiuwu.craft.forge.integration.ForgeAttributeBridgeHolder;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.runtime.AbstractLifecycleCoordinator;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.AsyncYamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import emaki.jiuwu.craft.forge.config.AppConfig;
import emaki.jiuwu.craft.forge.loader.PlayerDataStore;
import emaki.jiuwu.craft.forge.loader.RecipeLoader;
import emaki.jiuwu.craft.forge.loader.ForgeGuiTemplateLoader;
import emaki.jiuwu.craft.forge.model.Recipe;
import emaki.jiuwu.craft.forge.script.ScriptForgeModuleApi;
import emaki.jiuwu.craft.forge.service.ForgeGuiService;
import emaki.jiuwu.craft.forge.service.ForgeItemRefreshService;
import emaki.jiuwu.craft.forge.service.ForgeLookupIndex;
import emaki.jiuwu.craft.forge.service.ForgeService;
import emaki.jiuwu.craft.forge.service.ItemIdentifierService;
import emaki.jiuwu.craft.forge.service.RecipeBookGuiService;

final class ForgeLifecycleCoordinator extends AbstractLifecycleCoordinator<EmakiForgePlugin, ForgeRuntimeComponents> {

    private static final String DEFAULT_PREFIX = "<gray>[ <gradient:#F59E0B:#EF4444>EmakiForge</gradient> ]</gray>";
    private static final String PDC_ATTRIBUTE_SOURCE_ID = "forge";
    private static final List<String> VERSIONED_FILES = List.of("config.yml", "lang/zh_CN.yml", "lang/en_US.yml");
    private static final List<String> STATIC_FILES = List.of("gui/forge_gui.yml", "gui/recipe_book.yml");
    private static final List<String> DEFAULT_DATA_FILES = List.of("recipes/example_recipe.yml");
    private static final List<String> EXTRA_DIRECTORIES = List.of("data");
    private static final long SHUTDOWN_SETTLEMENT_DELAY_TICKS = 100L;
    private static final long SHUTDOWN_RETIREMENT_TIMEOUT_SECONDS = 10L;

    private final AtomicLong autoSaveGeneration = new AtomicLong();
    private final AtomicLong requestedGeneration = new AtomicLong();
    private final AtomicBoolean playerStorePrepared = new AtomicBoolean();
    private final AtomicBoolean reloadsAccepted = new AtomicBoolean(true);
    private final Object reloadLock = new Object();
    private CompletableFuture<Void> reloadTail = CompletableFuture.completedFuture(null);

    private record CandidatePreparation(
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

    private record CandidateDocuments(
            List<RecipeLoader.CandidateDocument> recipeDocuments,
            List<RecipeLoader.CandidateDocument> guiDocuments) {
    }

    private record RuntimeValidatedCandidate(
            CandidatePreparation preparation,
            RecipeLoader.RecipeLoadReport report,
            Map<String, Recipe> recipes,
            ForgeService forgeService) {
    }

    @Override
    public ForgeRuntimeComponents initialize(EmakiForgePlugin plugin) {
        reloadsAccepted.set(true);
        EmakiCoreLibPlugin coreLibPlugin = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
        registerAssemblyLayer(coreLibPlugin);
        registerScriptModule(coreLibPlugin);
        releaseBundledScripts(coreLibPlugin, plugin);
        YamlConfigLoader<AppConfig> appConfigLoader = new YamlConfigLoader<>(
                plugin,
                "config.yml",
                AppConfig::defaults,
                this::parseAppConfig
        );
        appConfigLoader.load();
        LanguageLoader languageLoader = new LanguageLoader(plugin, "lang", "lang", "zh_CN", "zh_CN");
        RecipeLoader recipeLoader = new RecipeLoader(plugin, coreLibPlugin::actionRegistry,
                coreLibPlugin::actionTemplateRegistry);
        GuiTemplateLoader guiTemplateLoader = new GuiTemplateLoader(plugin);
        AsyncYamlFiles playerDataFiles = coreLibPlugin.asyncYamlFiles(plugin);
        PlayerDataStore playerDataStore = new PlayerDataStore(plugin, () -> playerDataFiles);
        MessageService messageService = new MessageService(plugin, languageLoader, DEFAULT_PREFIX, true);
        languageLoader.load();
        BootstrapService bootstrapService = createBootstrapService(plugin, messageService);
        ExecutionDispatcher executionDispatcher = coreLibPlugin.executionDispatcher();
        var threadOwnership = coreLibPlugin.threadOwnership();
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
                coreLibPlugin::actionExecutor,
                executionDispatcher,
                threadOwnership
        );
        ForgeItemRefreshService itemRefreshService = new ForgeItemRefreshService(
                plugin,
                coreLibPlugin.itemAssemblyService(),
                executionDispatcher
        );
        ForgeGuiService forgeGuiService = new ForgeGuiService(plugin, guiService, executionDispatcher, threadOwnership);
        RecipeBookGuiService recipeBookGuiService = new RecipeBookGuiService(plugin, guiService);
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
                recipeBookGuiService
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
                        "reload rejected because plugin shutdown has started",
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
        notifyProgress(progressListener, "Building forge runtime generation " + generation + "...");
        CompletableFuture<ForgeReloadResult> pipeline = submitGlobal(plugin, () -> {
            plugin.beginReload(generation);
            plugin.itemIdentifierService().refresh();
            return null;
        }).thenCompose(ignored -> buildCandidateAsync(plugin, generation))
                .thenCompose(candidate -> {
                    if (!plugin.isGenerationRequested(generation)) {
                        return staleReload(plugin, generation, started, "superseded before install");
                    }
                    if (candidate.blocking()) {
                        return rejectedReload(plugin, candidate, started, "candidate contains blocking issues");
                    }
                    return installCandidate(plugin, candidate, closeOpenInventories, started, progressListener);
                });
        return pipeline.handle((result, throwable) -> {
            if (throwable == null) {
                return CompletableFuture.completedFuture(result);
            }
            Throwable cause = unwrap(throwable);
            if (cause instanceof StaleReloadException) {
                return staleReload(plugin, generation, started, "superseded during reload");
            }
            return submitGlobal(plugin, () -> {
                plugin.failReload(generation, "reload failed: " + cause.getMessage());
                plugin.getLogger().warning("[Reload] Generation " + generation + " failed: " + cause.getMessage());
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

    private CandidatePreparation prepareCandidateDocuments(EmakiForgePlugin plugin,
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

    private CandidatePreparation prepareCandidate(EmakiForgePlugin plugin,
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
        LanguageLoader languageLoader = new LanguageLoader(plugin, "lang", "lang", "zh_CN", "zh_CN");
        loadCandidateAppConfigReadOnly(appConfigLoader);
        validateCandidateLanguagesReadOnly(new File(plugin.getDataFolder(), "lang"));
        MessageService messageService = new MessageService(plugin, languageLoader, DEFAULT_PREFIX, true);
        BootstrapService bootstrapService = createBootstrapService(plugin, messageService);
        RecipeLoader recipeLoader = new RecipeLoader(plugin, coreLib::actionRegistry,
                coreLib::actionTemplateRegistry, plugin.itemIdentifierService(), true);
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

    private void loadCandidateAppConfigReadOnly(YamlConfigLoader<AppConfig> loader) {
        if (loader == null) {
            throw new IllegalStateException("Forge app config loader is unavailable.");
        }
        File file = loader.file();
        if (file == null || !file.isFile()) {
            return;
        }
        YamlSection configuration = YamlFiles.load(file);
        loader.overrideCurrent(configuration == null || configuration.isEmpty()
                ? AppConfig.defaults()
                : parseAppConfig(configuration));
    }

    private void validateCandidateLanguagesReadOnly(File directory) {
        if (directory == null || !directory.isDirectory()) {
            return;
        }
        File[] files = directory.listFiles((ignored, name) -> {
            String normalized = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
            return normalized.endsWith(".yml") || normalized.endsWith(".yaml");
        });
        if (files == null) {
            throw new IllegalStateException("Unable to list forge language directory " + directory.getPath());
        }
        for (File file : files) {
            if (file != null && file.isFile()) {
                YamlFiles.load(file);
            }
        }
    }

    private RuntimeValidatedCandidate runRuntimeValidation(EmakiForgePlugin plugin,
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
            throw new IllegalStateException("Forge service is unavailable during candidate validation.");
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

    private List<RecipeLoader.CandidateDocument> readCandidateDocuments(List<File> files) {
        List<RecipeLoader.CandidateDocument> documents = new ArrayList<>();
        if (files == null) {
            return List.of();
        }
        for (File file : files) {
            if (file == null) {
                continue;
            }
            try {
                documents.add(new RecipeLoader.CandidateDocument(
                        file,
                        Files.readString(file.toPath(), StandardCharsets.UTF_8),
                        null));
            } catch (Exception exception) {
                documents.add(new RecipeLoader.CandidateDocument(file, null, exception));
            }
        }
        return List.copyOf(documents);
    }

    private List<File> discoverYamlFiles(File directory) {
        List<File> files = new ArrayList<>();
        collectYamlFiles(directory, files);
        files.sort((left, right) -> left.getPath().compareToIgnoreCase(right.getPath()));
        return List.copyOf(files);
    }

    private void collectYamlFiles(File directory, List<File> sink) {
        if (directory == null || sink == null || !directory.exists()) {
            return;
        }
        File[] entries = directory.listFiles();
        if (entries == null) {
            return;
        }
        for (File entry : entries) {
            if (entry == null) {
                continue;
            }
            if (entry.isDirectory()) {
                collectYamlFiles(entry, sink);
                continue;
            }
            String name = entry.getName().toLowerCase(java.util.Locale.ROOT);
            if (name.endsWith(".yml") || name.endsWith(".yaml")) {
                sink.add(entry);
            }
        }
    }

    private BootstrapService createBootstrapService(EmakiForgePlugin plugin, MessageService messageService) {
        return new BootstrapService(
                plugin,
                messageService,
                VERSIONED_FILES,
                STATIC_FILES,
                DEFAULT_DATA_FILES,
                EXTRA_DIRECTORIES,
                new BootstrapHooks() {
                    @Override
                    public boolean shouldInstallDefaultData() {
                        return shouldReleaseDefaultData(plugin);
                    }
                }
        );
    }

    private void finalizeCandidateConfiguration(EmakiForgePlugin plugin, ForgeReloadCandidate candidate) {
        if (plugin == null || candidate == null
                || candidate.appConfigLoader() == null || candidate.languageLoader() == null) {
            throw new IllegalStateException("Forge candidate configuration is incomplete.");
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
        notifyProgress(progressListener, "Quiescing forge generation " + plugin.runtimeGeneration() + "...");
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
                    notifyProgress(progressListener, "Reload complete: generation " + generation
                            + ", outcome=" + outcome + ".");
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
                                                                String detail) {
        return submitGlobal(plugin, () -> {
            plugin.failReload(candidate, detail);
            plugin.getLogger().warning("[Reload] Rejected generation " + candidate.generation() + ": "
                    + candidate.recipeReport().summary());
            ForgeReloadResult result = new ForgeReloadResult(
                    candidate.generation(),
                    plugin.runtimeGeneration(),
                    plugin.runtimeStatus() == ForgeRuntimeStatus.UNAVAILABLE
                            ? ForgeReloadResult.Outcome.FAILED_UNAVAILABLE
                            : ForgeReloadResult.Outcome.FAILED_PRESERVED,
                    candidate.recipeLoader().all().size(),
                    candidate.guiTemplateLoader().all().size(),
                    ForgeItemRefreshService.RefreshSummary.empty(),
                    candidate.recipeReport(),
                    detail,
                    System.nanoTime() - started
            );
            plugin.runtimeMetrics().recordResult(result);
            return result;
        });
    }

    private CompletableFuture<ForgeReloadResult> staleReload(EmakiForgePlugin plugin,
                                                             long generation,
                                                             long started,
                                                             String detail) {
        return submitGlobal(plugin, () -> {
            plugin.failReload(generation, detail);
            ForgeReloadResult result = new ForgeReloadResult(
                    generation,
                    plugin.runtimeGeneration(),
                    ForgeReloadResult.Outcome.STALE,
                    plugin.recipeLoader() == null ? 0 : plugin.recipeLoader().all().size(),
                    plugin.guiTemplateLoader() == null ? 0 : plugin.guiTemplateLoader().all().size(),
                    ForgeItemRefreshService.RefreshSummary.empty(),
                    RecipeLoader.RecipeLoadReport.empty(generation),
                    detail,
                    System.nanoTime() - started
            );
            plugin.runtimeMetrics().recordResult(result);
            return result;
        });
    }

    public TaskHandle rescheduleAutoSave(EmakiForgePlugin plugin, TaskHandle currentTask) {
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

    public TaskHandle cancelAutoSave(TaskHandle currentTask) {
        autoSaveGeneration.incrementAndGet();
        if (currentTask != null && !currentTask.isCancelled()) {
            currentTask.cancel();
        }
        return null;
    }

    public CompletableFuture<Void> shutdownAsync(EmakiForgePlugin plugin, EmakiCoreLibPlugin coreLibPlugin) {
        if (coreLibPlugin != null) {
            try {
                coreLibPlugin.namespaceRegistry().unregister("forge");
            } catch (Throwable throwable) {
                plugin.getLogger().warning("[Shutdown] Namespace cleanup failed: "
                        + String.valueOf(throwable.getMessage()));
            }
            try {
                var javaScriptRegistrationTracker = coreLibPlugin.javaScriptRegistrationTracker();
                if (javaScriptRegistrationTracker != null) {
                    javaScriptRegistrationTracker.unregisterOwner(plugin);
                }
            } catch (Throwable throwable) {
                plugin.getLogger().warning("[Shutdown] JavaScript registration cleanup failed: "
                        + String.valueOf(throwable.getMessage()));
            }
            try {
                coreLibPlugin.scriptModuleRegistry().unregister("forge");
            } catch (Throwable throwable) {
                plugin.getLogger().warning("[Shutdown] Script module cleanup failed: "
                        + String.valueOf(throwable.getMessage()));
            }
        }
        sendShutdownMessage(plugin, "console.plugin_stopping");
        if (plugin.pdcAttributeGateway() != null) {
            try {
                plugin.pdcAttributeGateway().shutdown();
            } catch (Throwable throwable) {
                plugin.getLogger().warning("[Shutdown] PDC gateway cleanup failed: "
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
            plugin.getLogger().warning("[Shutdown] Player data flush could not start: "
                    + throwable.getClass().getSimpleName() + ": " + String.valueOf(throwable.getMessage()));
            sendShutdownMessage(plugin, "console.plugin_stopped");
            return CompletableFuture.completedFuture(null);
        }
        return flushFuture.handle((flushResult, throwable) -> {
            if (throwable != null) {
                Throwable cause = unwrap(throwable);
                plugin.getLogger().warning("[Shutdown] Player data flush failed: "
                        + cause.getClass().getSimpleName() + ": " + String.valueOf(cause.getMessage()));
            } else if (flushResult != null) {
                sendShutdownMessage(plugin, "console.player_data_saved", Map.of(
                        "count", flushResult.savedEntries()));
                if (!flushResult.clean()) {
                    plugin.getLogger().warning("[Shutdown] Player data drain incomplete: pending="
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

    private void sendShutdownMessage(EmakiForgePlugin plugin, String key) {
        sendShutdownMessage(plugin, key, Map.of());
    }

    private void sendShutdownMessage(EmakiForgePlugin plugin, String key, Map<String, ?> replacements) {
        if (plugin.messageService() == null) {
            return;
        }
        try {
            if (replacements == null || replacements.isEmpty()) {
                plugin.messageService().info(key);
            } else {
                plugin.messageService().info(key, replacements);
            }
        } catch (Throwable throwable) {
            plugin.getLogger().warning("[Shutdown] Message dispatch failed for '" + key + "': "
                    + String.valueOf(throwable.getMessage()));
        }
    }

    private <T> CompletableFuture<T> submitGlobal(EmakiForgePlugin plugin,
            java.util.function.Supplier<T> operation) {
        return submitGlobal(plugin, plugin, operation);
    }

    private <T> CompletableFuture<T> submitGlobal(EmakiForgePlugin plugin,
            Plugin taskOwner,
            java.util.function.Supplier<T> operation) {
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
                        Throwable cause = unwrap(throwable);
                        plugin.runtimeMetrics().recordGuiSettlementFailure();
                        plugin.getLogger().warning("[Shutdown] Forge retirement did not complete cleanly: "
                                + cause.getClass().getSimpleName() + ": " + String.valueOf(cause.getMessage()));
                    }
                    return throwable == null;
                })
                .thenAccept(cleanRetirement -> {
                    String reason = cleanRetirement
                            ? "shutdown cleanup found an unsettled GUI session"
                            : "shutdown retirement timed out before GUI settlement";
                    try {
                        plugin.forgeGuiService().finalizeShutdownSessions(reason);
                    } finally {
                        closeRuntimeForShutdown(plugin);
                    }
                })
                .handle((ignored, throwable) -> {
                    if (throwable != null) {
                        Throwable cause = unwrap(throwable);
                        plugin.getLogger().warning("[Shutdown] Forge runtime cleanup failed: "
                                + cause.getClass().getSimpleName() + ": " + String.valueOf(cause.getMessage()));
                        closeRuntimeForShutdown(plugin);
                    }
                    return null;
                });
    }

    private void closeRuntimeForShutdown(EmakiForgePlugin plugin) {
        plugin.forgeGuiService().clearSettledSessions();
        int unresolvedSessions = plugin.forgeGuiService().sessionsSnapshot().size();
        if (unresolvedSessions > 0) {
            plugin.runtimeMetrics().recordGuiSettlementFailure();
            plugin.getLogger().severe("[Shutdown] " + unresolvedSessions
                    + " Forge GUI session(s) still own unsettled items; sessions were not silently discarded.");
        }
        plugin.recipeBookGuiService().clearAllBooks();
        if (plugin.forgeService() != null) {
            plugin.forgeService().close();
        }
    }

    private CompletableFuture<Void> closeOpenInventoriesAsync(EmakiForgePlugin plugin) {
        return scheduleInventoryClosures(plugin)
                .thenCompose(ignored -> submitGlobal(plugin, () -> {
                    plugin.forgeGuiService().clearSettledSessions();
                    if (!plugin.forgeGuiService().sessionsSnapshot().isEmpty()) {
                        throw new IllegalStateException(
                                "Forge runtime reload cannot install while GUI item settlement remains unresolved.");
                    }
                    plugin.recipeBookGuiService().clearAllBooks();
                    return null;
                }));
    }

    private CompletableFuture<Void> scheduleShutdownInventoryClosures(EmakiForgePlugin plugin,
            EmakiCoreLibPlugin coreLibPlugin,
            CompletableFuture<Void> quiesceFuture) {
        List<CompletableFuture<Void>> closures = new java.util.ArrayList<>();
        for (var session : plugin.forgeGuiService().sessionsSnapshot()) {
            session.markShutdownRetiring();
            Player player = session.player();
            if (player == null) {
                plugin.forgeGuiService().handleShutdownClosureFailure(session,
                        "shutdown session has no player owner");
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
                    plugin.getLogger().warning("[Shutdown] Forge GUI close failed for "
                            + player.getUniqueId() + ": " + String.valueOf(throwable.getMessage()));
                    plugin.forgeGuiService().handleShutdownClosureFailure(session,
                            "shutdown inventory close failed: " + String.valueOf(throwable.getMessage()));
                } finally {
                    closure.complete(null);
                }
            };
            Runnable retired = () -> {
                plugin.forgeGuiService().handleShutdownClosureFailure(session,
                        "shutdown player owner retired before inventory close");
                closure.complete(null);
            };
            scheduleShutdownEntityTask(plugin, coreLibPlugin, player, close, retired, closure,
                    "Forge GUI closure");
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
                    plugin.getLogger().warning("[Shutdown] Recipe book close failed for "
                            + player.getUniqueId() + ": " + String.valueOf(throwable.getMessage()));
                } finally {
                    closure.complete(null);
                }
            };
            Runnable retired = () -> {
                plugin.recipeBookGuiService().removeRecipeBookOwner(player);
                plugin.getLogger().warning("[Shutdown] Recipe book owner retired before inventory close.");
                closure.complete(null);
            };
            scheduleShutdownEntityTask(plugin, coreLibPlugin, player, close, retired, closure,
                    "recipe book closure");
        }
        return CompletableFuture.allOf(closures.toArray(CompletableFuture[]::new));
    }

    private void scheduleShutdownSettlementFallback(EmakiForgePlugin plugin,
            EmakiCoreLibPlugin coreLibPlugin,
            Player player,
            emaki.jiuwu.craft.forge.service.ForgeGuiSession session,
            CompletableFuture<Void> quiesceFuture,
            CompletableFuture<Void> completion) {
        Runnable settle = () -> {
            try {
                plugin.forgeGuiService().settleShutdownSessionOnOwner(session);
                player.closeInventory();
            } catch (Throwable throwable) {
                plugin.getLogger().warning("[Shutdown] Forge GUI settlement fallback failed for "
                        + player.getUniqueId() + ": " + String.valueOf(throwable.getMessage()));
                plugin.forgeGuiService().handleShutdownClosureFailure(session,
                        "shutdown settlement fallback failed: " + String.valueOf(throwable.getMessage()));
            } finally {
                completion.complete(null);
            }
        };
        Runnable retired = () -> {
            plugin.forgeGuiService().handleShutdownClosureFailure(session,
                    "shutdown player owner retired before settlement fallback");
            completion.complete(null);
        };
        try {
            if (plugin.executionDispatcher() == null || coreLibPlugin == null) {
                retired.run();
                return;
            }
            TaskHandle scheduled = plugin.executionDispatcher().runEntityLater(
                    coreLibPlugin,
                    player,
                    settle,
                    retired,
                    SHUTDOWN_SETTLEMENT_DELAY_TICKS);
            if (scheduled == null) {
                retired.run();
                return;
            }
            if (quiesceFuture != null) {
                quiesceFuture.whenComplete((ignored, throwable) -> {
                    if (completion.isDone()) {
                        return;
                    }
                    try {
                        if (!scheduled.isCancelled()) {
                            scheduled.cancel();
                        }
                    } catch (Throwable cancellationFailure) {
                        plugin.getLogger().warning("[Shutdown] Forge GUI settlement fallback cancellation failed: "
                                + String.valueOf(cancellationFailure.getMessage()));
                    }
                    scheduleShutdownEntityTask(plugin, coreLibPlugin, player, settle, retired, completion,
                            "Forge GUI post-quiesce settlement");
                });
            }
        } catch (Throwable throwable) {
            plugin.getLogger().warning("[Shutdown] Forge GUI settlement fallback scheduling failed: "
                    + String.valueOf(throwable.getMessage()));
            try {
                retired.run();
            } catch (Throwable retiredFailure) {
                completion.completeExceptionally(retiredFailure);
            }
        }
    }

    private void scheduleShutdownEntityTask(EmakiForgePlugin plugin,
            EmakiCoreLibPlugin coreLibPlugin,
            Player player,
            Runnable operation,
            Runnable retired,
            CompletableFuture<Void> completion,
            String description) {
        try {
            if (plugin.threadOwnership() != null && plugin.threadOwnership().isEntityOwned(player)) {
                operation.run();
                return;
            }
            if (plugin.executionDispatcher() == null || coreLibPlugin == null) {
                retired.run();
                return;
            }
            var scheduled = plugin.executionDispatcher().runEntity(coreLibPlugin, player, operation, retired);
            if (scheduled == null && !completion.isDone()) {
                retired.run();
            }
        } catch (Throwable throwable) {
            plugin.getLogger().warning("[Shutdown] " + description + " scheduling failed: "
                    + String.valueOf(throwable.getMessage()));
            try {
                retired.run();
            } catch (Throwable retiredFailure) {
                completion.completeExceptionally(retiredFailure);
            }
        }
    }

    private CompletableFuture<Void> scheduleInventoryClosures(EmakiForgePlugin plugin) {
        List<Player> players = List.copyOf(Bukkit.getOnlinePlayers());
        List<CompletableFuture<Void>> closures = new java.util.ArrayList<>(players.size());
        for (Player player : players) {
            CompletableFuture<Void> closure = new CompletableFuture<>();
            closures.add(closure);
            Runnable close = () -> {
                try {
                    var forgeSession = plugin.forgeGuiService().getSession(player);
                    if (forgeSession != null || plugin.recipeBookGuiService().isRecipeBookInventory(player)) {
                        player.closeInventory();
                    }
                    closure.complete(null);
                } catch (Throwable throwable) {
                    closure.completeExceptionally(throwable);
                }
            };
            try {
                if (plugin.threadOwnership().isEntityOwned(player)) {
                    close.run();
                } else {
                    var scheduled = plugin.executionDispatcher().runEntity(plugin, player, close,
                            () -> closure.complete(null));
                    if (scheduled == null) {
                        closure.completeExceptionally(new java.util.concurrent.RejectedExecutionException(
                                "Forge GUI closure scheduling was rejected."));
                    }
                }
            } catch (Throwable throwable) {
                closure.completeExceptionally(throwable);
            }
        }
        return CompletableFuture.allOf(closures.toArray(CompletableFuture[]::new));
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private AppConfig parseAppConfig(YamlSection configuration) {
        if (configuration == null || configuration.getKeys(false).isEmpty()) {
            return AppConfig.defaults();
        }
        YamlSection permission = configuration.getSection("permission");
        YamlSection condition = configuration.getSection("condition");
        YamlSection history = configuration.getSection("history");
        YamlSection numberFormat = configuration.getSection("number_format");
        return new AppConfig(
                configuration.getString("language", "zh_CN"),
                configuration.getString("version", AppConfig.defaults().configVersion()),
                configuration.getBoolean("release_default_data", true),
                emaki.jiuwu.craft.forge.model.QualitySettings.fromConfig(configuration.get("quality")),
                numberFormat == null ? "0.##" : numberFormat.getString("default", "0.##"),
                numberFormat == null ? "0" : numberFormat.getString("integer", "0"),
                numberFormat == null ? "0.##%" : numberFormat.getString("percentage", "0.##%"),
                permission != null && permission.getBoolean("op_bypass", false),
                ConditionBlock.fromConfig(condition, true, false).invalidAsFailure(),
                history == null || history.getBoolean("enabled", true),
                history == null || history.getBoolean("auto_save", true),
                history == null ? 6000 : Numbers.tryParseInt(history.get("save_interval"), 6000)
        );
    }

    private boolean shouldReleaseDefaultData(EmakiForgePlugin plugin) {
        YamlSection configuration = YamlFiles.load(plugin.dataPath("config.yml").toFile());
        return configuration.getBoolean("release_default_data", true);
    }

    private void registerAssemblyLayer(EmakiCoreLibPlugin coreLibPlugin) {
        coreLibPlugin.namespaceRegistry().register(new EmakiNamespaceDefinition("forge", 100, "Forge"));
    }

    private void registerScriptModule(EmakiCoreLibPlugin coreLibPlugin) {
        coreLibPlugin.scriptModuleRegistry().register("forge",
                context -> new ScriptForgeModuleApi(JavaPlugin.getPlugin(EmakiForgePlugin.class), context));
    }

    private void releaseBundledScripts(EmakiCoreLibPlugin coreLibPlugin, EmakiForgePlugin plugin) {
        coreLibPlugin.releaseBundledScripts(plugin, "examples", false, List.of("forge_success.js"));
    }

    private static final class StaleReloadException extends RuntimeException {
        private StaleReloadException() {
            super("reload generation is stale");
        }
    }
}
