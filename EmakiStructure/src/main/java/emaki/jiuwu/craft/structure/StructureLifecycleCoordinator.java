package emaki.jiuwu.craft.structure;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapHooks;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.runtime.AbstractLifecycleCoordinator;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.structure.command.StructureCommandRouter;
import emaki.jiuwu.craft.structure.config.AppConfig;
import emaki.jiuwu.craft.structure.config.AppConfigParser;
import emaki.jiuwu.craft.structure.config.ConfigIssue;
import emaki.jiuwu.craft.structure.generate.BlockEntityWriter;
import emaki.jiuwu.craft.structure.generate.GenerationFinalizer;
import emaki.jiuwu.craft.structure.generate.GenerationScheduler;
import emaki.jiuwu.craft.structure.generate.GenerationService;
import emaki.jiuwu.craft.structure.integration.CustomBlockIdAdapter;
import emaki.jiuwu.craft.structure.integration.FurnitureIdAdapter;
import emaki.jiuwu.craft.structure.multiblock.InstanceRegistry;
import emaki.jiuwu.craft.structure.multiblock.MultiblockRepository;
import emaki.jiuwu.craft.structure.multiblock.MultiblockService;
import emaki.jiuwu.craft.structure.multiblock.TriggerCooldown;
import emaki.jiuwu.craft.structure.selection.ParticleSelectionHighlighter;
import emaki.jiuwu.craft.structure.selection.SelectionManager;
import emaki.jiuwu.craft.structure.spawn.SpawnExecutor;
import emaki.jiuwu.craft.structure.spawn.SpawnQueue;
import emaki.jiuwu.craft.structure.spawn.SpawnSidecarRepository;
import emaki.jiuwu.craft.structure.template.ImportLimits;
import emaki.jiuwu.craft.structure.template.StructureTemplateCache;
import emaki.jiuwu.craft.structure.template.TemplateLoadReport;
import emaki.jiuwu.craft.structure.template.TemplateRepository;

import java.util.List;
import java.util.Map;

final class StructureLifecycleCoordinator
        extends AbstractLifecycleCoordinator<EmakiStructurePlugin, StructureRuntimeComponents> {

    private static final String DEFAULT_PREFIX =
            "<gray>[<gradient:#86EFAC:#34D399>EmakiStructure</gradient>]</gray> ";
    private static final List<String> VERSIONED_FILES = List.of("config.yml");
    private static final List<String> STATIC_FILES = List.of();
    private static final List<String> DEFAULT_DATA_FILES = List.of();
    private static final List<String> EXTRA_DIRECTORIES =
            List.of("structures", "multiblocks", "spawns", "furniture", "journal");

    @Override
    public StructureRuntimeComponents initialize(EmakiStructurePlugin plugin) {
        var coreLibPlugin = EmakiCoreLibPlugin.lookup();
        var appConfigLoader = new YamlConfigLoader<>(plugin,
                "config.yml", AppConfig::defaults, AppConfigParser::parse);
        appConfigLoader.load();
        reportConfigIssues(plugin, appConfigLoader.current());
        appConfigLoader.overrideCurrent(appConfigLoader.current().withoutIssues());
        var languageLoader = new LanguageLoader(plugin, "lang", "lang", "zh_CN", "zh_CN");
        var messageService = new MessageService(plugin, languageLoader, DEFAULT_PREFIX, true);
        languageLoader.load();
        var bootstrapService = new BootstrapService(plugin, messageService,
                VERSIONED_FILES, STATIC_FILES, DEFAULT_DATA_FILES, EXTRA_DIRECTORIES,
                new BootstrapHooks() {
                    @Override
                    public boolean shouldInstallDefaultData() {
                        return true;
                    }
                });
        var executionDispatcher = coreLibPlugin.executionDispatcher();

        AppConfig current = appConfigLoader.current();
        var importLimits = toImportLimits(current);
        var templateCache = new StructureTemplateCache(current.cache().maxEntries(),
                current.cache().maxBytes());
        var templateRepository = new TemplateRepository(plugin.dataPath("structures"),
                importLimits, templateCache);
        templateRepository.reload();
        reportTemplateProblems(plugin, templateRepository);
        reportCacheWarnings(plugin, templateRepository);
        messageService.info("console.templates_loaded",
                Map.of("count", templateRepository.templateIds().size()));

        var spawnSidecars = new SpawnSidecarRepository(plugin, plugin.getDataFolder().toPath(),
                () -> appConfigLoader.current().spawn());
        var spawnQueue = new SpawnQueue(plugin, executionDispatcher,
                () -> appConfigLoader.current().spawn().batchPerTick());
        var spawnExecutor = new SpawnExecutor(plugin, spawnQueue,
                () -> appConfigLoader.current().generate().entityPolicy(),
                () -> appConfigLoader.current().spawn().respectEntityPolicy());
        spawnQueue.start();

        var highlighter = new ParticleSelectionHighlighter(plugin, executionDispatcher);
        var selectionManager = new SelectionManager(plugin, executionDispatcher,
                () -> appConfigLoader.current().selection().sessionTimeout(), highlighter);
        selectionManager.startSweep();

        var generationScheduler = new GenerationScheduler(plugin, executionDispatcher,
                () -> appConfigLoader.current().generate().maxBlocksPerTick(),
                () -> appConfigLoader.current().generate().adaptiveThrottling(),
                () -> appConfigLoader.current().generate().queueTimeout());
        generationScheduler.start();

        var blockEntityWriter = new BlockEntityWriter();
        var generationFinalizer = new GenerationFinalizer(plugin, executionDispatcher,
                blockEntityWriter, spawnSidecars, spawnExecutor, appConfigLoader::current);
        var generationService = new GenerationService(plugin, executionDispatcher,
                generationScheduler, generationFinalizer, appConfigLoader::current);
        var commandRouter = new StructureCommandRouter(plugin, selectionManager,
                generationService, templateRepository, appConfigLoader::current);

        var customBlockAdapter = new CustomBlockIdAdapter();
        var furnitureAdapter = new FurnitureIdAdapter(plugin);
        for (String source : furnitureAdapter.absentSources()) {
            messageService.warning("console.furniture_source_absent", Map.of("source", source));
        }
        for (String namespace : customBlockAdapter.unavailableKnownNamespaces()) {
            messageService.warning("console.custom_block_source_absent",
                    Map.of("namespace", namespace));
        }
        var multiblockRepository = new MultiblockRepository(plugin.dataPath("multiblocks"),
                appConfigLoader::current);
        multiblockRepository.reload();
        reportMultiblockProblems(plugin, multiblockRepository);
        var instanceRegistry = new InstanceRegistry(plugin, executionDispatcher,
                () -> appConfigLoader.current().detection());
        var triggerCooldown = new TriggerCooldown();
        var multiblockService = new MultiblockService(plugin, multiblockRepository,
                instanceRegistry, customBlockAdapter, furnitureAdapter, triggerCooldown,
                appConfigLoader::current);
        multiblockService.startSweep();

        return new StructureRuntimeComponents(messageService, languageLoader, executionDispatcher,
                appConfigLoader, bootstrapService, templateRepository, templateCache,
                spawnSidecars, spawnQueue, spawnExecutor, selectionManager,
                generationScheduler, blockEntityWriter, generationFinalizer,
                generationService, commandRouter,
                customBlockAdapter, furnitureAdapter, multiblockRepository, multiblockService);
    }

    int reload(EmakiStructurePlugin plugin) {
        StructureRuntimeComponents components = plugin.components();
        AppConfig previous = components.appConfigLoader().current();
        try {
            YamlFiles.load(components.appConfigLoader().file());
        } catch (RuntimeException exception) {
            plugin.messageService().warning("console.config_reload_failed", Map.of(
                    "file", components.appConfigLoader().file().getName(),
                    "error", String.valueOf(exception.getMessage())));
            return components.templateRepository().templateIds().size();
        }
        components.appConfigLoader().load();
        AppConfig loaded = components.appConfigLoader().current();
        AppConfig repaired = loaded.issues().isEmpty() ? loaded : loaded.repair(previous);
        reportConfigIssues(plugin, repaired);
        components.appConfigLoader().overrideCurrent(repaired.withoutIssues());
        components.generationScheduler().cancelAll("error.generate.cancelled");
        components.selectionManager().clearAllSessions();
        components.multiblockService().clearIndex();
        components.spawnSidecars().reload();
        components.templateCache().invalidateAll();
        components.templateRepository().reload();
        components.multiblockRepository().reload();
        reportTemplateProblems(plugin, components.templateRepository());
        reportMultiblockProblems(plugin, components.multiblockRepository());
        reportCacheWarnings(plugin, components.templateRepository());
        components.multiblockService().startSweep();
        int count = components.templateRepository().templateIds().size();
        plugin.messageService().info("console.reload_complete", Map.of("count", count));
        return count;
    }

    private static void reportCacheWarnings(EmakiStructurePlugin plugin, TemplateRepository repository) {
        for (String templateId : repository.drainCacheWarnings()) {
            plugin.messageService().warning("console.cache_rejected",
                    Map.of("template", templateId));
        }
    }

    private static void reportMultiblockProblems(EmakiStructurePlugin plugin,
            MultiblockRepository repository) {
        for (MultiblockRepository.Problem problem : repository.drainProblems()) {
            if ("error.multiblock.duplicate_id".equals(problem.reasonKey())
                    || "error.multiblock.version_unsupported".equals(problem.reasonKey())) {
                plugin.messageService().severe(problem.reasonKey(), problem.placeholders());
            } else {
                plugin.messageService().warning(problem.reasonKey(), problem.placeholders());
            }
        }
    }

    private static void reportConfigIssues(EmakiStructurePlugin plugin, AppConfig config) {
        for (ConfigIssue issue : config.issues()) {
            plugin.messageService().warning("console.config_issue", issue.placeholders());
        }
    }

    private static void reportTemplateProblems(EmakiStructurePlugin plugin, TemplateRepository repository) {
        TemplateLoadReport report = repository.report();
        if (!report.hasProblems()) {
            return;
        }
        for (TemplateLoadReport.Problem problem : report.problems()) {
            plugin.messageService().warning("error.template.rejected", Map.of(
                    "file", problem.source(),
                    "reason", plugin.messageService().message(problem.reasonKey(), problem.placeholders())));
        }
        report.clear();
    }

    private static ImportLimits toImportLimits(AppConfig config) {
        ImportLimits defaults = ImportLimits.defaults();
        ImportLimits.LegacyPolicy legacyPolicy = config.template().legacyPolicy() == AppConfig.LegacyPolicy.SKIP
                ? ImportLimits.LegacyPolicy.SKIP
                : ImportLimits.LegacyPolicy.REJECT;
        return new ImportLimits(config.template().importMaxFileBytes(),
                defaults.maxOutputBytes(),
                defaults.maxDepth(),
                config.generate().sizeLimitX(),
                config.generate().sizeLimitY(),
                config.generate().sizeLimitZ(),
                defaults.maxTotalBlocks(),
                config.template().paletteLimit(),
                config.template().blockEntityMaxBytes(),
                legacyPolicy);
    }
}
