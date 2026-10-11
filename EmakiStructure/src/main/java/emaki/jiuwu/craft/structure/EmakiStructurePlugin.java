package emaki.jiuwu.craft.structure;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.api.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckLifecycleSupport;
import emaki.jiuwu.craft.corelib.debug.DebugCommand;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.metrics.BStatsRegistration;
import emaki.jiuwu.craft.corelib.plugin.AbstractConfigurableEmakiPlugin;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.structure.api.EmakiStructureApi;
import emaki.jiuwu.craft.structure.command.StructureCommandAdapter;
import emaki.jiuwu.craft.structure.command.StructureCommandRouter;
import emaki.jiuwu.craft.structure.config.AppConfig;
import emaki.jiuwu.craft.structure.config.StructureConfigPrecheckContributor;
import emaki.jiuwu.craft.structure.generate.GenerationService;
import emaki.jiuwu.craft.structure.apiimpl.ServiceBackedStructureBridge;
import emaki.jiuwu.craft.structure.integration.furniture.CraftEngineFurnitureListener;
import emaki.jiuwu.craft.structure.integration.furniture.ItemsAdderFurnitureListener;
import emaki.jiuwu.craft.structure.integration.furniture.NexoFurnitureListener;
import emaki.jiuwu.craft.structure.integration.furniture.OraxenFurnitureListener;
import emaki.jiuwu.craft.structure.multiblock.MultiblockService;
import emaki.jiuwu.craft.structure.multiblock.listener.BlockChangeDetectListener;
import emaki.jiuwu.craft.structure.multiblock.listener.MultiblockInteractListener;
import emaki.jiuwu.craft.structure.selection.SelectionManager;
import emaki.jiuwu.craft.structure.template.StructureTemplateCache;
import emaki.jiuwu.craft.structure.template.TemplateRepository;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class EmakiStructurePlugin extends AbstractConfigurableEmakiPlugin<AppConfig> {

    private static final String MODULE = "structure";
    private static final String ROOT_COMMAND = "emakistructure";
    private static final Set<String> DEBUG_MODULES = Set.of(MODULE);

    private static final String STARTUP_ASCII = """
 _____       __             ____            _ __      __
/ ___/____  / /___  ___    / __ \\____  ____ _(_) /_____/ /
\\__ \\/ __ \\/ / __ \\/ _ \\  / /_/ / __ \\/ __ `/ / __/ __  /
___/ / /_/ / / / /  __/ / ____/ /_/ / /_/ / / /_/ /_/ /
/____/\\____/_/ /_/\\___/ /_/    \\____/\\__, /_/\\__/\\__,_/
                                     /____/
""";
    private static final int STARTUP_ASCII_START_COLOR = 0x86EFAC;
    private static final int STARTUP_ASCII_END_COLOR = 0x34D399;
    private static final int BSTATS_PLUGIN_ID = 34642;

    private final StructureLifecycleCoordinator lifecycleCoordinator = new StructureLifecycleCoordinator();
    private final AtomicBoolean shutdownStarted = new AtomicBoolean(false);

    private StructureRuntimeComponents components;
    private boolean runtimeInitialized;
    private volatile boolean contentReady;
    private ServiceBackedStructureBridge bridge;
    private DebugCommand debugCommand;
    private BStatsRegistration metrics;

    public EmakiStructurePlugin() {
        super(AppConfig::defaults);
    }

    @Override
    public void onEnable() {
        shutdownStarted.set(false);
        components = lifecycleCoordinator.initialize(this);
        runtimeInitialized = true;
        setDebugLogger(new DebugLogger(this, components.languageLoader()));
        debugLogger().setFallbackLoader(EmakiCoreLibPlugin.lookup().languageLoader());
        debugCommand = new DebugCommand(debugLogger(), DEBUG_MODULES, getName());
        ConsoleOutputs.sendGradientAscii(this, STARTUP_ASCII, STARTUP_ASCII_START_COLOR, STARTUP_ASCII_END_COLOR);
        ConfigPrecheckLifecycleSupport.register(new StructureConfigPrecheckContributor(this));
        components.messageService().info("console.plugin_starting");
        components.bootstrapService().bootstrap();
        reloadContent();
        registerCommandHandler();
        registerListeners();
        installPublicApi();
        metrics = EmakiCoreLibPlugin.lookup().registerBStats(this, BSTATS_PLUGIN_ID);
        components.messageService().info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        if (!shutdownStarted.compareAndSet(false, true)) {
            return;
        }
        contentReady = false;
        ConfigPrecheckLifecycleSupport.unregister(StructureConfigPrecheckContributor.MODULE);
        if (bridge != null) {
            uninstallPublicApi();
        }
        if (!runtimeInitialized || components == null) {
            return;
        }
        components.generationScheduler().close();
        components.spawnQueue().close();
        components.selectionManager().close();
        components.multiblockService().clearAll();
        HandlerList.unregisterAll(this);
        debugCommand = null;
        runtimeInitialized = false;
        components.messageService().info("console.plugin_stopped");
    }

    @Override
    public YamlConfigLoader<AppConfig> appConfigLoader() {
        return components == null ? null : components.appConfigLoader();
    }

    public boolean contentReady() {
        return contentReady;
    }

    public boolean isShutdownStarted() {
        return shutdownStarted.get();
    }

    StructureRuntimeComponents components() {
        return components;
    }

    public MessageService messageService() {
        return components == null ? null : components.messageService();
    }

    public LanguageLoader languageLoader() {
        return components == null ? null : components.languageLoader();
    }

    public ExecutionDispatcher executionDispatcher() {
        return components == null ? null : components.executionDispatcher();
    }

    public DebugCommand debugCommand() {
        return debugCommand;
    }

    public TemplateRepository templateRepository() {
        return components == null ? null : components.templateRepository();
    }

    public StructureTemplateCache templateCache() {
        return components == null ? null : components.templateCache();
    }

    public SelectionManager selectionManager() {
        return components == null ? null : components.selectionManager();
    }

    public GenerationService generationService() {
        return components == null ? null : components.generationService();
    }

    public StructureCommandRouter commandRouter() {
        return components == null ? null : components.commandRouter();
    }

    public MultiblockService multiblockService() {
        return components == null ? null : components.multiblockService();
    }

    public int reloadContent() {
        contentReady = false;
        int count = lifecycleCoordinator.reload(this);
        contentReady = true;
        return count;
    }

    private void registerCommandHandler() {
        registerCommand(ROOT_COMMAND, "EmakiStructure 命令",
                List.of("ems", "estruct"),
                new StructureCommandAdapter(components.commandRouter(), "emakistructure.use"));
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new SelectionQuitListener(this), this);
        MultiblockService service = components.multiblockService();
        getServer().getPluginManager().registerEvents(new BlockChangeDetectListener(this, service), this);
        getServer().getPluginManager().registerEvents(new MultiblockInteractListener(this, service), this);
        var furnitureAdapter = components.furnitureAdapter();
        registerIfPresent(furnitureAdapter.sourcePresent("ItemsAdder"), "ItemsAdder",
                () -> new ItemsAdderFurnitureListener(this, service));
        registerIfPresent(furnitureAdapter.sourcePresent("Oraxen"), "Oraxen",
                () -> new OraxenFurnitureListener(this, service));
        registerIfPresent(furnitureAdapter.sourcePresent("Nexo"), "Nexo",
                () -> new NexoFurnitureListener(this, service));
        registerIfPresent(furnitureAdapter.sourcePresent("CraftEngine"), "CraftEngine",
                () -> new CraftEngineFurnitureListener(this, service));
    }

    private void registerIfPresent(boolean present, String sourceName,
            java.util.function.Supplier<Listener> factory) {
        if (!present) {
            return;
        }
        try {
            getServer().getPluginManager().registerEvents(factory.get(), this);
        } catch (LinkageError error) {
            getLogger().warning("家具源 " + sourceName + " 监听注册失败，已跳过: "
                    + error.getMessage());
        } catch (RuntimeException exception) {
            getLogger().warning("家具源 " + sourceName + " 监听注册异常: "
                    + exception.getMessage());
        }
    }

    private void installPublicApi() {
        bridge = new ServiceBackedStructureBridge(this);
        EmakiStructureApi.install(bridge);
    }

    private void uninstallPublicApi() {
        EmakiStructureApi.uninstall(bridge);
        bridge = null;
    }
}
