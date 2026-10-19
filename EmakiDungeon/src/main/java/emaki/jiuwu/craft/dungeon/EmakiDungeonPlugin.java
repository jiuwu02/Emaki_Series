package emaki.jiuwu.craft.dungeon;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.api.contract.ApiStatus;
import emaki.jiuwu.craft.corelib.debug.DebugCommand;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.plugin.AbstractConfigurableEmakiPlugin;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.dungeon.api.EmakiDungeonApi;
import emaki.jiuwu.craft.dungeon.apiimpl.ServiceBackedDungeonBridge;
import emaki.jiuwu.craft.dungeon.command.DungeonCommandAdapter;
import emaki.jiuwu.craft.dungeon.command.DungeonCommandRouter;
import emaki.jiuwu.craft.dungeon.config.AppConfig;

public class EmakiDungeonPlugin extends AbstractConfigurableEmakiPlugin<AppConfig> {

    private static final String MODULE = "dungeon";
    private static final String ROOT_COMMAND = "edg";
    private static final Set<String> DEBUG_MODULES = Set.of(MODULE);
    private static final String API_VERSION = "1.0.0";

    private final AtomicBoolean shutdownStarted = new AtomicBoolean(false);

    private YamlConfigLoader<AppConfig> appConfigLoader;
    private MessageService messageService;
    private LanguageLoader languageLoader;
    private ServiceBackedDungeonBridge bridge;
    private DebugCommand debugCommand;
    private boolean runtimeInitialized;

    public EmakiDungeonPlugin() {
        super(AppConfig::defaults);
    }

    @Override
    public void onEnable() {
        shutdownStarted.set(false);
        languageLoader = new LanguageLoader(this);
        messageService = new MessageService(this, languageLoader, "EmakiDungeon");
        appConfigLoader = new YamlConfigLoader<>(this, "config.yml", AppConfig::defaults, AppConfig::parse);
        appConfigLoader.load();
        setDebugLogger(new DebugLogger(this, languageLoader));
        debugLogger().setFallbackLoader(EmakiCoreLibPlugin.lookup().languageLoader());
        debugCommand = new DebugCommand(debugLogger(), DEBUG_MODULES, getName());
        registerCommandHandler();
        installPublicApi();
        runtimeInitialized = true;
        messageService.info("console.plugin_starting");
        messageService.info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        if (!shutdownStarted.compareAndSet(false, true)) {
            return;
        }
        if (bridge != null) {
            EmakiDungeonApi.uninstall(bridge);
            bridge = null;
        }
        debugCommand = null;
        runtimeInitialized = false;
        if (messageService != null) {
            messageService.info("console.plugin_stopped");
        }
    }

    @Override
    public YamlConfigLoader<AppConfig> appConfigLoader() {
        return appConfigLoader;
    }

    public MessageService messageService() {
        return messageService;
    }

    public LanguageLoader languageLoader() {
        return languageLoader;
    }

    public DebugCommand debugCommand() {
        return debugCommand;
    }

    public boolean runtimeInitialized() {
        return runtimeInitialized;
    }

    private void registerCommandHandler() {
        DungeonCommandRouter router = new DungeonCommandRouter(this);
        registerCommand(ROOT_COMMAND, "EmakiDungeon 命令", List.of("dungeon", "edungeon"),
                new DungeonCommandAdapter(router, "emakidungeon.play"));
    }

    private void installPublicApi() {
        String version = getPluginMeta().getVersion();
        bridge = new ServiceBackedDungeonBridge(ApiStatus.ready(getName(), version, API_VERSION));
        EmakiDungeonApi.install(bridge);
    }
}
