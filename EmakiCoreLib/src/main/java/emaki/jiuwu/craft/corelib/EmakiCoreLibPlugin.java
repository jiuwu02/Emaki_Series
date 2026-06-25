package emaki.jiuwu.craft.corelib;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
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
import emaki.jiuwu.craft.corelib.library.RuntimeLibraryLoader;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyService;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemLayerCodecRegistry;
import emaki.jiuwu.craft.corelib.assembly.EmakiNamespaceRegistry;
import emaki.jiuwu.craft.corelib.async.AsyncFileService;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.bridge.mythic.MythicJavaScriptBridge;
import emaki.jiuwu.craft.corelib.command.CoreLibCommandRouter;
import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.integration.CraftEngineBlockBridge;
import emaki.jiuwu.craft.corelib.integration.CraftEngineBlockBridgeProvider;
import emaki.jiuwu.craft.corelib.api.integration.CustomBlockBridge;
import emaki.jiuwu.craft.corelib.integration.ItemsAdderBlockBridgeProvider;
import emaki.jiuwu.craft.corelib.integration.NexoBlockBridgeProvider;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceIntegrationCoordinator;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
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
import emaki.jiuwu.craft.corelib.service.EmakiServiceRegistry;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.text.AdventureSupport;
import emaki.jiuwu.craft.corelib.web.WebConsoleService;
import emaki.jiuwu.craft.corelib.yaml.AsyncYamlFiles;
import emaki.jiuwu.craft.corelib.yaml.VersionedYamlFile;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;

public final class EmakiCoreLibPlugin extends JavaPlugin implements LogMessagesProvider, EmakiServiceRegistry {

    private static final String STARTUP_ASCII = """
 ______  __    __  ______  __  __   __  ______  ______  ______  ______  __      __  ______
/\\  ___\\/\\ "-./  \\/\\  __ \\/\\ \\/ /  /\\ \\/\\  ___\\/\\  __ \\/\\  == \\/\\  ___\\/\\ \\    /\\ \\/\\  == \\
\\ \\  __\\\\ \\ \\-./\\ \\ \\  __ \\ \\  _"-.\\ \\ \\ \\ \\___\\ \\ \\/\\ \\ \\  __<\\ \\  __\\\\ \\ \\___\\ \\ \\ \\  __<
 \\ \\_____\\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_\\ \\_\\\\ \\_\\ \\_____\\ \\_____\\ \\_\\ \\_\\ \\_____\\ \\_____\\ \\_\\ \\_____\\
  \\/_____/\\/_/  \\/_/\\/_/\\/_/\\/_/\\/_/ \\/_/\\/_____/\\/_____/\\/_/ /_/\\/_____/\\/_____/\\/_/\\/_____/
""";
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
    private ItemSourceIntegrationCoordinator itemSourceIntegrationCoordinator;
    private final EmakiNamespaceRegistry namespaceRegistry = new EmakiNamespaceRegistry();
    private final EmakiItemLayerCodecRegistry itemLayerCodecRegistry = new EmakiItemLayerCodecRegistry();
    private final CraftEngineBlockBridge craftEngineBlockBridge = new CraftEngineBlockBridgeProvider(this);
    private final CustomBlockBridge itemsAdderBlockBridge = new ItemsAdderBlockBridgeProvider(this);
    private final CustomBlockBridge nexoBlockBridge = new NexoBlockBridgeProvider(this);
    private EmakiItemAssemblyService itemAssemblyService;
    private final emaki.jiuwu.craft.corelib.assembly.LayerMigrationRegistry layerMigrationRegistry
            = new emaki.jiuwu.craft.corelib.assembly.LayerMigrationRegistry();
    private final emaki.jiuwu.craft.corelib.event.EmakiEventBus eventBus
            = new emaki.jiuwu.craft.corelib.event.EmakiEventBus();
    private final Map<Class<?>, Object> serviceRegistry = new ConcurrentHashMap<>();
    private DebugLogger debugLogger;
    private WebConsoleService webConsoleService;
    private CoreLibCommandRouter commandRouter;
    private final EmakiCoreLibApi.Bridge coreLibApiBridge = new EmakiCoreLibApi.Bridge() {
        @Override
        public String apiVersion() {
            return getDescription().getVersion();
        }

        @Override
        public String pluginName() {
            return getName();
        }

        @Override
        public boolean isReady() {
            return isEnabled() && messageService() != null;
        }

        @Override
        public String itemDisplayName(String itemSource) {
            ItemSource source = ItemSourceUtil.parse(itemSource);
            String displayName = itemSourceService.displayName(source);
            return emaki.jiuwu.craft.corelib.text.Texts.isBlank(displayName)
                    ? emaki.jiuwu.craft.corelib.text.Texts.toStringSafe(itemSource)
                    : displayName;
        }

        @Override
        public String itemDisplayName(ItemStack itemStack) {
            if (itemStack == null || itemStack.getType().isAir()) {
                return "";
            }
            ItemSource source = itemSourceService.identifyItem(itemStack);
            String displayName = itemSourceService.displayName(source);
            return emaki.jiuwu.craft.corelib.text.Texts.isBlank(displayName)
                    ? ItemTextBridge.effectiveNameText(itemStack)
                    : displayName;
        }
    };
    private JavaScriptActionExtensionLoader javaScriptActionExtensionLoader;
    private MythicJavaScriptBridge mythicJavaScriptBridge;

    @Override
    public void onLoad() {
        new RuntimeLibraryLoader(this).load();
    }

    @Override
    public void onEnable() {
        ensureBundledFile("config.yml");
        configModel = loadConfigModel();
        initializeServices();
        ConsoleOutputs.sendGradientAscii(this, STARTUP_ASCII);
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
        if (messageService != null) {
            try {
                messageService.info("console.plugin_stopped");
            } catch (RuntimeException exception) {
                getLogger().info("EmakiCoreLib stopped.");
            }
        }
        if (webConsoleService != null) {
            webConsoleService.stop();
            webConsoleService = null;
        }
        if (metrics != null) {
            metrics.close();
            metrics = null;
        }
        if (bStatsService != null) {
            bStatsService.shutdownAll();
            bStatsService = null;
        }
        if (javaScriptActionExtensionLoader != null) {
            javaScriptActionExtensionLoader.close();
            javaScriptActionExtensionLoader = null;
        }
        if (loopActionService != null) {
            loopActionService.cancelAll();
        }
        if (mythicJavaScriptBridge != null) {
            HandlerList.unregisterAll(mythicJavaScriptBridge);
            mythicJavaScriptBridge = null;
        }
        if (javaScriptService != null) {
            javaScriptService.close();
        }
        EmakiCoreLibApi.uninstall(coreLibApiBridge);
        if (guiBackendRegistry != null) {
            guiBackendRegistry.shutdownAll();
            guiBackendRegistry = null;
            guiBackend = null;
        }
        if (asyncTaskScheduler != null) {
            asyncTaskScheduler.shutdown(5_000L);
        }
        ExpressionEngine.clearGlobalCache();
        ExpressionEngine.clearThreadLocalCache();
        emaki.jiuwu.craft.corelib.assembly.OperationTemplateRenderer.clearRegexCache();
        AdventureSupport.close(this);
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
        ActionRegistry candidateActionRegistry = new ActionRegistry();
        ActionTemplateRegistry candidateTemplateRegistry = new ActionTemplateRegistry();
        PlaceholderRegistry candidatePlaceholderRegistry = new PlaceholderRegistry(this::debugLogger);
        EconomyManager candidateEconomyManager = new EconomyManager(this);
        LoopActionService effectiveLoopService = loopActionService == null ? new LoopActionService(this) : loopActionService;
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
        reloadWebConsole();
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
                    configModel.scriptConfig(),
                    dataPath(configModel.scriptConfig().paths().root()),
                    () -> actionExecutor,
                    scriptModuleRegistry
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

    private void reloadWebConsole() {
        if (webConsoleService == null) {
            webConsoleService = new WebConsoleService(this, configModel.webConsoleConfig());
        }
        webConsoleService.stop();
        webConsoleService.restart(configModel.webConsoleConfig());
        refreshServiceRegistry();
    }

    private void registerPublicApiService() {
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

    private void registerMythicJavaScriptBridge() {
        if (mythicJavaScriptBridge != null || !getServer().getPluginManager().isPluginEnabled("MythicMobs")) {
            return;
        }
        mythicJavaScriptBridge = new MythicJavaScriptBridge(this);
        getServer().getPluginManager().registerEvents(mythicJavaScriptBridge, this);
        messageService.info("console.mythic_js_bridge_ready");
    }

    private void logStartupAudit() {
        if (economyManager == null) {
            return;
        }
        for (String providerId : economyManager.availableProviderIds()) {
            messageService.info("console.economy_bridge_ready", Map.of("provider", providerId));
        }
        for (String blockProvider : new String[]{"CraftEngine", "ItemsAdder", "Nexo"}) {
            if (getServer().getPluginManager().isPluginEnabled(blockProvider)) {
                messageService.info("console.block_source_bridge_ready", Map.of("provider", blockProvider));
            }
        }
    }

    public Path dataPath(String first, String... more) {
        return getDataFolder().toPath().resolve(Path.of(first, more));
    }

    public void releaseBundledScripts(JavaPlugin sourcePlugin, String directory, boolean skipWhenAnyFileExists, java.util.List<String> names) {
        CoreLibConfig effectiveConfig = configModel == null ? CoreLibConfig.defaults() : configModel;
        var scriptConfig = effectiveConfig.scriptConfig() == null
                ? emaki.jiuwu.craft.corelib.script.ScriptConfig.defaults()
                : effectiveConfig.scriptConfig();
        new ScriptRepository(
                dataPath(scriptConfig.paths().root()),
                scriptConfig.security()
        ).releaseScriptGroup(sourcePlugin, directory, skipWhenAnyFileExists, names);
    }

    private void registerCommandHandler() {
        commandRouter = new CoreLibCommandRouter(this);
        PluginCommand pluginCommand = getCommand("emakicorelib");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(commandRouter);
            pluginCommand.setTabCompleter(commandRouter);
        }
    }

    private void initializeServices() {
        CoreLibConfig config = configModel == null ? CoreLibConfig.defaults() : configModel;
        languageLoader = new LanguageLoader(this, "lang", "lang", config.language(), "zh_CN");
        messageService = new MessageService(this, languageLoader);
        bStatsService = new BStatsService(this, messageService);
        debugLogger = new DebugLogger(this, languageLoader);
        itemSourceIntegrationCoordinator = new ItemSourceIntegrationCoordinator(this, messageService, itemSourceService);
        configPrecheckService = new ConfigPrecheckService();
        loopActionService = new LoopActionService(this);
        getServer().getPluginManager().registerEvents(loopActionService, this);
        performanceMonitor = new PerformanceMonitor();
        asyncTaskScheduler = AsyncTaskScheduler.forPlugin(this, "emaki-corelib-async", performanceMonitor);
        asyncFileService = new AsyncFileService(asyncTaskScheduler, 3, performanceMonitor);
        asyncYamlFiles = new AsyncYamlFiles(asyncFileService);
        languageLoader.load();
        guiBackendRegistry = new emaki.jiuwu.craft.corelib.gui.GuiBackendRegistry(messageService);
        guiBackendRegistry.setConfiguredName(config.guiConfig().backend());
        guiBackend = new emaki.jiuwu.craft.corelib.gui.RegistryBackedGuiBackend(guiBackendRegistry);
        itemAssemblyService = new EmakiItemAssemblyService(namespaceRegistry, itemLayerCodecRegistry, itemSourceService);
        itemAssemblyService.configureAsync(asyncTaskScheduler, performanceMonitor);
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
            VersionedYamlFile versionedFile = YamlFiles.syncVersionedResource(this, file, "config.yml", "version");
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

    /**
     * The CoreLib-wide GUI backend registry. The built-in {@code bukkit} backend
     * is always available; the optional {@code packet} backend is installed by
     * {@link #installPacketBackend()} when PacketEvents is present.
     */
    public emaki.jiuwu.craft.corelib.gui.GuiBackendRegistry guiBackendRegistry() {
        return guiBackendRegistry;
    }

    /**
     * Installs the built-in packet GUI backend when the optional PacketEvents
     * plugin is present and enabled.
     *
     * <p>The packet backend lives inside CoreLib but depends on PacketEvents,
     * which CoreLib declares only as a {@code softdepend}. To keep CoreLib's zero
     * hard-dependency guarantee, the PacketEvents-touching classes are reached
     * only through {@code PacketBackendInstaller} and only after the PacketEvents
     * plugin is confirmed loaded, with {@link LinkageError} guarded so a missing
     * or incompatible PacketEvents never breaks startup. When unavailable the
     * registry keeps only the Bukkit backend.</p>
     */
    private void installPacketBackend() {
        if (guiBackendRegistry == null) {
            return;
        }
        var packetEvents = getServer().getPluginManager().getPlugin("PacketEvents");
        if (packetEvents == null || !packetEvents.isEnabled()) {
            return;
        }
        try {
            emaki.jiuwu.craft.corelib.gui.packet.PacketBackendInstaller.install(this, guiBackendRegistry);
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

    public DebugLogger debugLogger() {
        return debugLogger;
    }

    public WebConsoleService webConsoleService() {
        return webConsoleService;
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
        registerService(WebConsoleService.class, webConsoleService);
        registerService(PdcService.class, pdcService);
        registerService(ItemSourceService.class, itemSourceService);
        registerService(EmakiNamespaceRegistry.class, namespaceRegistry);
        registerService(EmakiItemLayerCodecRegistry.class, itemLayerCodecRegistry);
        registerService(CraftEngineBlockBridge.class, craftEngineBlockBridge);
        registerService(ItemsAdderBlockBridgeProvider.class, (ItemsAdderBlockBridgeProvider) itemsAdderBlockBridge);
        registerService(NexoBlockBridgeProvider.class, (NexoBlockBridgeProvider) nexoBlockBridge);
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
