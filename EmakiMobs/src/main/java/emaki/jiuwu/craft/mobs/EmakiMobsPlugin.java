package emaki.jiuwu.craft.mobs;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.api.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.metrics.BStatsRegistration;
import emaki.jiuwu.craft.corelib.plugin.AbstractConfigurableEmakiPlugin;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.mobs.api.EmakiMobsApi;
import emaki.jiuwu.craft.mobs.api.MobExtensions;
import emaki.jiuwu.craft.mobs.apiimpl.ServiceBackedMobsBridge;
import emaki.jiuwu.craft.mobs.command.MobsCommandAdapter;
import emaki.jiuwu.craft.mobs.command.MobsCommandRouter;
import emaki.jiuwu.craft.mobs.config.AppConfig;
import emaki.jiuwu.craft.mobs.loader.MobSpec;
import emaki.jiuwu.craft.mobs.service.MobFactory;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class EmakiMobsPlugin extends AbstractConfigurableEmakiPlugin<AppConfig> {

    private static final String ROOT_COMMAND = "emakimobs";

    private static final String STARTUP_ASCII = """
 ______ __ __ ______ __ __ __ __ __ ______ ______ ______
/\\ ___\\/\\ "-./ \\/\\ __ \\/\\ \\/ / /\\ \\/\\ "-./ \\/\\ __ \\/\\ == \\/\\ ___\\
\\ \\ __\\\\ \\ \\-./\\ \\ \\ __ \\ \\ _"-. \\ \\ \\ \\ \\-./\\ \\ \\ \\/\\ \\ \\ __<\\ \\___ \\
\\ \\_____\\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_\\ \\_\\\\ \\_\\ \\_\\ \\ \\_\\ \\_____\\ \\_____\\/\\_____\\
\\/_____/\\/_/ \\/_/\\/_/\\/_/\\/_/\\/_/ \\/_/\\/_/ \\/_/\\/_____/\\/_____/\\/_____/
""";
    private static final int STARTUP_ASCII_START_COLOR = 0x86EFAC;
    private static final int STARTUP_ASCII_END_COLOR = 0x34D399;
    private static final int BSTATS_PLUGIN_ID = 33427;

    private final MobsLifecycleCoordinator lifecycleCoordinator = new MobsLifecycleCoordinator();
    private final AtomicBoolean shutdownStarted = new AtomicBoolean(false);

    private MobsRuntimeComponents components;
    private boolean runtimeInitialized;
    private volatile boolean contentReady;
    private ServiceBackedMobsBridge bridge;
    private BStatsRegistration metrics;

    public EmakiMobsPlugin() {
        super(AppConfig::defaults);
    }

    @Override
    public void onEnable() {
        shutdownStarted.set(false);
        components = lifecycleCoordinator.initialize(this);
        runtimeInitialized = true;
        setDebugLogger(new DebugLogger(this, components.languageLoader()));
        debugLogger().setFallbackLoader(JavaPlugin.getPlugin(EmakiCoreLibPlugin.class).languageLoader());
        ConsoleOutputs.sendGradientAscii(this, STARTUP_ASCII, STARTUP_ASCII_START_COLOR, STARTUP_ASCII_END_COLOR);
        components.messageService().info("console.plugin_starting");
        components.bootstrapService().bootstrap();
        reloadContent();
        lifecycleCoordinator.registerCustomActions(this);
        registerCommandHandler();
        registerListeners();
        installPublicApi();
        metrics = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class).registerBStats(this, BSTATS_PLUGIN_ID);
        components.messageService().info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        if (!shutdownStarted.compareAndSet(false, true)) {
            return;
        }
        contentReady = false;
        if (bridge != null) {
            uninstallPublicApi();
        }
        if (!runtimeInitialized || components == null) {
            return;
        }
        lifecycleCoordinator.unregisterCustomActions();
        HandlerList.unregisterAll(this);
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

    MobsRuntimeComponents components() {
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

    public MobFactory mobFactory() {
        return components == null ? null : components.mobFactory();
    }

    public MobExtensions mobExtensions() {
        return components == null ? null : components.mobExtensions();
    }

    public AtomicReference<Map<String, MobSpec>> mobRegistry() {
        return components == null ? null : components.mobRegistry();
    }

    public int reloadContent() {
        contentReady = false;
        int count = lifecycleCoordinator.reload(this);
        contentReady = true;
        return count;
    }

    private void registerCommandHandler() {
        var commandRouter = new MobsCommandRouter(this);
        registerCommand(ROOT_COMMAND, "EmakiMobs command",
                List.of("emobs"),
                new MobsCommandAdapter(commandRouter, "emakimobs.use"));
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(components.mobDropHandler(), this);
        getServer().getPluginManager().registerEvents(components.naturalSpawnHandler(), this);
        getServer().getPluginManager().registerEvents(components.mobTriggerListener(), this);
        getServer().getPluginManager().registerEvents(components.typeOverrideApplicator(), this);
        getServer().getPluginManager().registerEvents(components.threatTableManager(), this);
        getServer().getPluginManager().registerEvents(components.bossBarManager(), this);
    }

    private void installPublicApi() {
        bridge = new ServiceBackedMobsBridge(this);
        EmakiMobsApi.install(bridge);
    }

    private void uninstallPublicApi() {
        EmakiMobsApi.uninstall(bridge);
        bridge = null;
    }
}
