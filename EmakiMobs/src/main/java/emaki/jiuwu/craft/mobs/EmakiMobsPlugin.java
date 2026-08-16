package emaki.jiuwu.craft.mobs;

import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.plugin.AbstractConfigurableEmakiPlugin;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.mobs.api.EmakiMobsApi;
import emaki.jiuwu.craft.mobs.apiimpl.ServiceBackedMobsBridge;
import emaki.jiuwu.craft.mobs.command.MobsCommandAdapter;
import emaki.jiuwu.craft.mobs.command.MobsCommandRouter;
import emaki.jiuwu.craft.mobs.config.AppConfig;
import emaki.jiuwu.craft.mobs.loader.MobSpec;
import emaki.jiuwu.craft.mobs.service.MobFactory;
import org.bukkit.event.HandlerList;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class EmakiMobsPlugin extends AbstractConfigurableEmakiPlugin<AppConfig> {

    private static final String ROOT_COMMAND = "emakimobs";

    private final MobsLifecycleCoordinator lifecycleCoordinator = new MobsLifecycleCoordinator();
    private final AtomicBoolean shutdownStarted = new AtomicBoolean(false);

    private MobsRuntimeComponents components;
    private boolean runtimeInitialized;
    private volatile boolean contentReady;
    private ServiceBackedMobsBridge bridge;

    public EmakiMobsPlugin() {
        super(AppConfig::defaults);
    }

    @Override
    public void onEnable() {
        shutdownStarted.set(false);
        components = lifecycleCoordinator.initialize(this);
        runtimeInitialized = true;
        setDebugLogger(new DebugLogger(this, components.languageLoader()));
        components.messageService().info("console.plugin_starting");
        components.bootstrapService().bootstrap();
        reloadContent();
        registerCommandHandler();
        registerListeners();
        installPublicApi();
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
        getServer().getPluginManager().registerEvents(components.structureSpawnHandler(), this);
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
