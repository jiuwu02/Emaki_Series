package emaki.jiuwu.craft.item;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.metrics.BStatsRegistration;
import emaki.jiuwu.craft.corelib.debug.DebugCommand;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.api.integration.EmakiAttributeBridge;
import emaki.jiuwu.craft.corelib.integration.PdcAttributeGateway;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.plugin.AbstractConfigurableEmakiPlugin;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.AdventureSupport;
import emaki.jiuwu.craft.corelib.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.web.WebConsoleRegistry;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.item.api.EmakiItemApi;
import emaki.jiuwu.craft.item.config.AppConfig;
import emaki.jiuwu.craft.item.listener.ItemTriggerListener;
import emaki.jiuwu.craft.item.listener.ItemUpdateListener;
import emaki.jiuwu.craft.item.loader.EmakiItemLoader;
import emaki.jiuwu.craft.item.loader.EmakiItemSetLoader;
import emaki.jiuwu.craft.item.papi.ItemPlaceholderExpansion;
import emaki.jiuwu.craft.item.service.EmakiItemActionService;
import emaki.jiuwu.craft.item.service.EmakiItemConditionChecker;
import emaki.jiuwu.craft.item.service.EmakiItemFactory;
import emaki.jiuwu.craft.item.service.EmakiItemIdentifier;
import emaki.jiuwu.craft.item.service.EmakiItemPdcWriter;
import emaki.jiuwu.craft.item.service.EmakiItemSetService;
import emaki.jiuwu.craft.item.service.EmakiItemUpdateService;
import emaki.jiuwu.craft.item.service.ItemComponentInspector;
import emaki.jiuwu.craft.item.service.ItemComponentPlaceholderResolver;

public final class EmakiItemPlugin extends AbstractConfigurableEmakiPlugin<AppConfig> implements LogMessagesProvider {

    private static final String ROOT_COMMAND = "emakiitem";
    private static final Set<String> DEBUG_MODULES = Set.of("create", "update", "identify");
    private static final String STARTUP_ASCII = """
 ______  __    __  ______  __  __   __  __  ______  ______  __    __  ______
/\\  ___\\/\\ "-./  \\/\\  __ \\/\\ \\/ /  /\\ \\/\\ \\/\\__  _\\/\\  ___\\/\\ "-./  \\/\\  ___\\
\\ \\  __\\\\ \\ \\-./\\ \\ \\  __ \\ \\  _"-.\\ \\ \\ \\ \\/_/\\ \\/\\ \\  __\\\\ \\ \\-./\\ \\ \\___  \\
 \\ \\_____\\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_\\ \\_\\\\ \\_\\ \\_\\ \\ \\_\\ \\ \\_____\\ \\_\\ \\ \\_\\/\\_____\\
  \\/_____/\\/_/  \\/_/\\/_/\\/_/\\/_/\\/_/ \\/_/\\/_/  \\/_/  \\/_____/\\/_/  \\/_/\\/_____/
""";
    private static final int BSTATS_PLUGIN_ID = 31770;

    private BStatsRegistration metrics;

    private final ItemLifecycleCoordinator lifecycleCoordinator = new ItemLifecycleCoordinator();
    private final ItemCommandRouter commandRouter = new ItemCommandRouter(this);
    private ItemPlaceholderExpansion placeholderExpansion;
    private DebugCommand debugCommand;

    private YamlConfigLoader<AppConfig> appConfigLoader;
    private LanguageLoader languageLoader;
    private MessageService messageService;
    private BootstrapService bootstrapService;
    private EmakiItemLoader itemLoader;
    private EmakiItemSetLoader setLoader;
    private EmakiItemIdentifier identifier;
    private EmakiItemPdcWriter pdcWriter;
    private EmakiItemFactory itemFactory;
    private EmakiItemUpdateService updateService;
    private EmakiItemSetService setService;
    private EmakiItemActionService actionService;
    private EmakiItemConditionChecker conditionChecker;
    private EmakiItemApi itemApi;
    private ItemComponentInspector componentInspector;
    private ItemComponentPlaceholderResolver componentPlaceholderResolver;
    private ItemSourceService itemSourceService;
    private PdcAttributeGateway pdcAttributeGateway;

    public EmakiItemPlugin() {
        super(AppConfig::defaults);
    }

    @Override
    public void onEnable() {
        ConsoleOutputs.sendGradientAscii(this, STARTUP_ASCII);
        applyRuntimeComponents(lifecycleCoordinator.initialize(this));
        messageService.info("console.plugin_starting");
        bootstrapService.bootstrap();
        reloadPluginState();
        lifecycleCoordinator.registerServices(this);
        registerCommandHandler();
        registerEventHandlers();
        registerWebConsole();
        ensurePlaceholderExpansion();
        metrics = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class).registerBStats(this, BSTATS_PLUGIN_ID);
        messageService.info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
            placeholderExpansion = null;
        }
        if (metrics != null) {
            metrics.close();
            metrics = null;
        }
        WebConsoleRegistry.unregisterModule(this);
        lifecycleCoordinator.shutdown(this);
        AdventureSupport.close(this);
    }

    public void reloadPluginState() {
        lifecycleCoordinator.reload(this);
    }

    public CompletableFuture<Void> reloadPluginStateAsync() {
        return lifecycleCoordinator.reloadAsync(this, null);
    }

    private void applyRuntimeComponents(ItemRuntimeComponents components) {
        appConfigLoader = components.appConfigLoader();
        languageLoader = components.languageLoader();
        messageService = components.messageService();
        bootstrapService = components.bootstrapService();
        itemLoader = components.itemLoader();
        setLoader = components.setLoader();
        identifier = components.identifier();
        pdcWriter = components.pdcWriter();
        itemFactory = components.itemFactory();
        updateService = components.updateService();
        setService = components.setService();
        actionService = components.actionService();
        conditionChecker = components.conditionChecker();
        itemApi = components.itemApi();
        componentInspector = components.componentInspector();
        componentPlaceholderResolver = components.componentPlaceholderResolver();
        itemSourceService = components.itemSourceService();
        pdcAttributeGateway = components.pdcAttributeGateway();
        setDebugLogger(new DebugLogger(getLogger(), languageLoader));
        debugCommand = new DebugCommand(debugLogger(), DEBUG_MODULES);
        registerServices(components);
    }

    private void registerCommandHandler() {
        PluginCommand pluginCommand = getCommand(ROOT_COMMAND);
        if (pluginCommand != null) {
            pluginCommand.setExecutor(commandRouter);
            pluginCommand.setTabCompleter(commandRouter);
        }
    }

    private void registerEventHandlers() {
        getServer().getPluginManager().registerEvents(new ItemTriggerListener(this), this);
        getServer().getPluginManager().registerEvents(new ItemUpdateListener(this), this);
    }

    private void registerWebConsole() {
        WebConsoleRegistry.registerFromYaml(this);
    }

    private void ensurePlaceholderExpansion() {
        if (placeholderExpansion != null || !Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        placeholderExpansion = new ItemPlaceholderExpansion(this);
        placeholderExpansion.register();
        messageService.info("console.papi_registered");
    }

    @Override
    public YamlConfigLoader<AppConfig> appConfigLoader() {
        return appConfigLoader;
    }

    public LanguageLoader languageLoader() {
        return languageLoader;
    }

    public MessageService messageService() {
        return messageService;
    }

    public BootstrapService bootstrapService() {
        return bootstrapService;
    }

    public EmakiItemLoader itemLoader() {
        return itemLoader;
    }

    public EmakiItemSetLoader setLoader() {
        return setLoader;
    }

    public EmakiItemIdentifier identifier() {
        return identifier;
    }

    public EmakiItemPdcWriter pdcWriter() {
        return pdcWriter;
    }

    public EmakiItemFactory itemFactory() {
        return itemFactory;
    }

    public EmakiItemUpdateService updateService() {
        return updateService;
    }

    public EmakiItemSetService setService() {
        return setService;
    }

    public EmakiItemActionService actionService() {
        return actionService;
    }

    public EmakiItemConditionChecker conditionChecker() {
        return conditionChecker;
    }

    public EmakiItemApi itemApi() {
        return itemApi;
    }

    public ItemComponentInspector componentInspector() {
        return componentInspector;
    }

    public ItemComponentPlaceholderResolver componentPlaceholderResolver() {
        return componentPlaceholderResolver;
    }

    public ItemSourceService itemSourceService() {
        return itemSourceService;
    }

    public PdcAttributeGateway pdcAttributeGateway() {
        return pdcAttributeGateway;
    }

    public void scheduleAttributeEquipmentSync(Player player) {
        if (player == null) {
            return;
        }
        RegisteredServiceProvider<EmakiAttributeBridge> registration = Bukkit.getServicesManager().getRegistration(EmakiAttributeBridge.class);
        EmakiAttributeBridge bridge = registration == null ? null : registration.getProvider();
        if (bridge != null && bridge.available()) {
            bridge.scheduleEquipmentSync(player);
        }
    }

    public DebugCommand debugCommand() {
        return debugCommand;
    }
}
