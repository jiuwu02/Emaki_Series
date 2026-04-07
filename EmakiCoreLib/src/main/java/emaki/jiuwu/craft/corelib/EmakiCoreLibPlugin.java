package emaki.jiuwu.craft.corelib;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.action.ActionExecutor;
import emaki.jiuwu.craft.corelib.action.ActionLineParser;
import emaki.jiuwu.craft.corelib.action.ActionRegistry;
import emaki.jiuwu.craft.corelib.action.ActionTemplateRegistry;
import emaki.jiuwu.craft.corelib.action.builtin.BuiltinActions;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyService;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemLayerCodecRegistry;
import emaki.jiuwu.craft.corelib.assembly.EmakiNamespaceDefinition;
import emaki.jiuwu.craft.corelib.assembly.EmakiNamespaceRegistry;
import emaki.jiuwu.craft.corelib.assembly.ItemPresentationCompiler;
import emaki.jiuwu.craft.corelib.async.AsyncFileService;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.item.ItemSourceIntegrationCoordinator;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.monitor.PerformanceMonitor;
import emaki.jiuwu.craft.corelib.pdc.PdcService;
import emaki.jiuwu.craft.corelib.placeholder.ActionContextPlaceholderResolver;
import emaki.jiuwu.craft.corelib.placeholder.ActionInlineTokenResolver;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderApiResolver;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRegistry;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.yaml.AsyncYamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;

public final class EmakiCoreLibPlugin extends JavaPlugin implements LogMessagesProvider {

    private static final String STARTUP_ASCII = """
 ______  __    __  ______  __  __   __  ______  ______  ______  ______  __      __  ______
/\\  ___\\/\\ "-./  \\/\\  __ \\/\\ \\/ /  /\\ \\/\\  ___\\/\\  __ \\/\\  == \\/\\  ___\\/\\ \\    /\\ \\/\\  == \\
\\ \\  __\\\\ \\ \\-./\\ \\ \\  __ \\ \\  _"-.\\ \\ \\ \\ \\___\\ \\ \\/\\ \\ \\  __<\\ \\  __\\\\ \\ \\___\\ \\ \\ \\  __<
 \\ \\_____\\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_\\ \\_\\\\ \\_\\ \\_____\\ \\_____\\ \\_\\ \\_\\ \\_____\\ \\_____\\ \\_\\ \\_____\\
  \\/_____/\\/_/  \\/_/\\/_/\\/_/\\/_/\\/_/ \\/_/\\/_____/\\/_____/\\/_/ /_/\\/_____/\\/_____/\\/_/\\/_____/
""";

    private static EmakiCoreLibPlugin instance;
    private LanguageLoader languageLoader;
    private MessageService messageService;
    private CoreLibConfig configModel = CoreLibConfig.defaults();
    private PerformanceMonitor performanceMonitor;
    private AsyncTaskScheduler asyncTaskScheduler;
    private AsyncFileService asyncFileService;
    private AsyncYamlFiles asyncYamlFiles;
    private ActionRegistry actionRegistry;
    private ActionTemplateRegistry actionTemplateRegistry;
    private PlaceholderRegistry placeholderRegistry;
    private EconomyManager economyManager;
    private ActionExecutor actionExecutor;
    private final PdcService pdcService = new PdcService("emaki_corelib");
    private final ItemSourceService itemSourceService = new ItemSourceService();
    private ItemSourceIntegrationCoordinator itemSourceIntegrationCoordinator;
    private final EmakiNamespaceRegistry namespaceRegistry = new EmakiNamespaceRegistry();
    private final EmakiItemLayerCodecRegistry itemLayerCodecRegistry = new EmakiItemLayerCodecRegistry();
    private final ItemPresentationCompiler itemPresentationCompiler = new ItemPresentationCompiler();
    private final EmakiItemAssemblyService itemAssemblyService
            = new EmakiItemAssemblyService(namespaceRegistry, itemLayerCodecRegistry, itemSourceService);

    public static EmakiCoreLibPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        initializeServices();
        ConsoleOutputs.sendGradientAscii(this, STARTUP_ASCII);
        messageService.info("console.plugin_starting");
        migrateLegacyBundledFiles();
        ensureBundledFile("config.yml");
        itemSourceIntegrationCoordinator.initialize();
        reloadActionSystem();
        messageService.info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        if (messageService != null) {
            messageService.info("console.plugin_stopped");
        }
        if (asyncTaskScheduler != null) {
            asyncTaskScheduler.shutdown(5_000L);
        }
        if (instance == this) {
            instance = null;
        }
    }

    @Override
    public MessageService messageService() {
        return messageService;
    }

    public LanguageLoader languageLoader() {
        return languageLoader;
    }

    public void reloadActionSystem() {
        configModel = loadConfigModel();
        actionRegistry = new ActionRegistry();
        actionTemplateRegistry = new ActionTemplateRegistry();
        placeholderRegistry = new PlaceholderRegistry();
        economyManager = new EconomyManager(this);
        placeholderRegistry.register(new ActionContextPlaceholderResolver());
        placeholderRegistry.register(new ActionInlineTokenResolver());
        placeholderRegistry.register(new PlaceholderApiResolver());
        for (var entry : configModel.actionTemplates().entrySet()) {
            actionTemplateRegistry.register(entry.getKey(), entry.getValue());
        }
        BuiltinActions.registerAll(actionRegistry, economyManager, itemSourceService, itemPresentationCompiler);
        actionExecutor = new ActionExecutor(this, actionRegistry, new ActionLineParser(), placeholderRegistry, actionTemplateRegistry);
    }

    public Path dataPath(String first, String... more) {
        return getDataFolder().toPath().resolve(Path.of(first, more));
    }

    private void initializeServices() {
        languageLoader = new LanguageLoader(this);
        messageService = new MessageService(this, languageLoader);
        itemSourceIntegrationCoordinator = new ItemSourceIntegrationCoordinator(this, messageService, itemSourceService);
        performanceMonitor = new PerformanceMonitor();
        asyncTaskScheduler = AsyncTaskScheduler.forPlugin(this, "emaki-corelib-async", performanceMonitor);
        asyncFileService = new AsyncFileService(asyncTaskScheduler, 3, performanceMonitor);
        asyncYamlFiles = new AsyncYamlFiles(asyncFileService);
        languageLoader.load();
        namespaceRegistry.register(new EmakiNamespaceDefinition("forge", 100, "Forge"));
        namespaceRegistry.register(new EmakiNamespaceDefinition("strengthen", 200, "Strengthen"));
        itemAssemblyService.configureAsync(asyncTaskScheduler, performanceMonitor);
    }

    private void ensureBundledFile(String relativePath) {
        File target = new File(getDataFolder(), relativePath);
        try {
            boolean copied = YamlFiles.copyResourceIfMissing(this, relativePath, target);
            if (!copied && !target.exists()) {
                messageService.warning("loader.bundled_resource_missing", java.util.Map.of(
                        "type", "资源",
                        "path", target.getPath(),
                        "resource", relativePath
                ));
            }
        } catch (Exception exception) {
            messageService.warning("loader.bundled_resource_write_failed", java.util.Map.of(
                    "path", target.getPath(),
                    "error", String.valueOf(exception.getMessage())
            ));
        }
    }

    private CoreLibConfig loadConfigModel() {
        try {
            File file = new File(getDataFolder(), "config.yml");
            return CoreLibConfig.fromConfig(YamlFiles.load(file));
        } catch (Exception exception) {
            messageService.warning("console.action_config_load_failed", java.util.Map.of(
                    "error", String.valueOf(exception.getMessage())
            ));
            return CoreLibConfig.defaults();
        }
    }

    private void migrateLegacyBundledFiles() {
        Path legacyRoot = getDataFolder().toPath().resolve("defaults");
        if (!Files.exists(legacyRoot) || !Files.isDirectory(legacyRoot)) {
            return;
        }
        try (var paths = Files.walk(legacyRoot)) {
            paths.forEach(source -> {
                try {
                    Path relative = legacyRoot.relativize(source);
                    if (relative.toString().isBlank()) {
                        return;
                    }
                    Path target = getDataFolder().toPath().resolve(relative);
                    if (Files.isDirectory(source)) {
                        YamlFiles.ensureDirectory(target);
                        return;
                    }
                    if (Files.exists(target)) {
                        return;
                    }
                    YamlFiles.ensureDirectory(target.getParent());
                    Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
                } catch (IOException exception) {
                    messageService.warning("loader.legacy_resource_migrate_failed", java.util.Map.of(
                            "path", source.toString(),
                            "error", String.valueOf(exception.getMessage())
                    ));
                }
            });
        } catch (IOException exception) {
            messageService.warning("loader.legacy_resource_scan_failed", java.util.Map.of(
                    "path", legacyRoot.toString(),
                    "error", String.valueOf(exception.getMessage())
            ));
        }
    }

    public CoreLibConfig configModel() {
        return configModel;
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

    public AsyncTaskScheduler asyncTaskScheduler() {
        return asyncTaskScheduler;
    }

    public PerformanceMonitor performanceMonitor() {
        return performanceMonitor;
    }

    public AsyncFileService asyncFileService() {
        return asyncFileService;
    }

    public AsyncYamlFiles asyncYamlFiles() {
        return asyncYamlFiles;
    }

    public PdcService pdcService() {
        return pdcService;
    }

    public ItemSourceService itemSourceService() {
        return itemSourceService;
    }

    public EmakiNamespaceRegistry namespaceRegistry() {
        return namespaceRegistry;
    }

    public EmakiItemLayerCodecRegistry itemLayerCodecRegistry() {
        return itemLayerCodecRegistry;
    }

    public ItemPresentationCompiler itemPresentationCompiler() {
        return itemPresentationCompiler;
    }

    public EmakiItemAssemblyService itemAssemblyService() {
        return itemAssemblyService;
    }
}
