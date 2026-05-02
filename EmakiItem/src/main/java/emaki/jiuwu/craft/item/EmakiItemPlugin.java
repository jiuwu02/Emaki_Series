package emaki.jiuwu.craft.item;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;

import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.integration.PdcAttributeGateway;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.plugin.AbstractConfigurableEmakiPlugin;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.AdventureSupport;
import emaki.jiuwu.craft.corelib.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.item.api.EmakiItemApi;
import emaki.jiuwu.craft.item.config.AppConfig;
import emaki.jiuwu.craft.item.listener.ItemTriggerListener;
import emaki.jiuwu.craft.item.loader.EmakiItemLoader;
import emaki.jiuwu.craft.item.papi.ItemPlaceholderExpansion;
import emaki.jiuwu.craft.item.service.EmakiItemActionService;
import emaki.jiuwu.craft.item.service.EmakiItemConditionChecker;
import emaki.jiuwu.craft.item.service.EmakiItemFactory;
import emaki.jiuwu.craft.item.service.EmakiItemIdentifier;
import emaki.jiuwu.craft.item.service.EmakiItemPdcWriter;

public final class EmakiItemPlugin extends AbstractConfigurableEmakiPlugin<AppConfig> implements LogMessagesProvider {

    private static final String ROOT_COMMAND = "emakiitem";
    private static final String STARTUP_ASCII = """
 ______  __    __  ______  __  __   __  __  ______  ______  __    __  ______
/\\  ___\\/\\ "-./  \\/\\  __ \\/\\ \\/ /  /\\ \\/\\ \\/\\__  _\\/\\  ___\\/\\ "-./  \\/\\  ___\\
\\ \\  __\\\\ \\ \\-./\\ \\ \\  __ \\ \\  _"-.\\ \\ \\ \\ \\/_/\\ \\/\\ \\  __\\\\ \\ \\-./\\ \\ \\___  \\
 \\ \\_____\\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_\\ \\_\\\\ \\_\\ \\_\\  \\ \\_\\ \\ \\_____\\ \\_\\ \\ \\_\\/\\_____\\
  \\/_____/\\/_/  \\/_/\\/_/\\/_/\\/_/\\/_/ \\/_/\\/_/   \\/_/  \\/_____/\\/_/  \\/_/\\/_____/
""";

    private final ItemLifecycleCoordinator lifecycleCoordinator = new ItemLifecycleCoordinator();
    private final ItemCommandRouter commandRouter = new ItemCommandRouter(this);
    private ItemPlaceholderExpansion placeholderExpansion;

    private YamlConfigLoader<AppConfig> appConfigLoader;
    private LanguageLoader languageLoader;
    private MessageService messageService;
    private BootstrapService bootstrapService;
    private EmakiItemLoader itemLoader;
    private EmakiItemIdentifier identifier;
    private EmakiItemPdcWriter pdcWriter;
    private EmakiItemFactory itemFactory;
    private EmakiItemActionService actionService;
    private EmakiItemConditionChecker conditionChecker;
    private EmakiItemApi itemApi;
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
        ensurePlaceholderExpansion();
        messageService.info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
            placeholderExpansion = null;
        }
        lifecycleCoordinator.shutdown(this);
        AdventureSupport.close(this);
    }

    public void reloadPluginState() {
        lifecycleCoordinator.reload(this);
    }

    private void applyRuntimeComponents(ItemRuntimeComponents components) {
        appConfigLoader = components.appConfigLoader();
        languageLoader = components.languageLoader();
        messageService = components.messageService();
        bootstrapService = components.bootstrapService();
        itemLoader = components.itemLoader();
        identifier = components.identifier();
        pdcWriter = components.pdcWriter();
        itemFactory = components.itemFactory();
        actionService = components.actionService();
        conditionChecker = components.conditionChecker();
        itemApi = components.itemApi();
        itemSourceService = components.itemSourceService();
        pdcAttributeGateway = components.pdcAttributeGateway();
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
    }

    private void ensurePlaceholderExpansion() {
        if (placeholderExpansion != null || !Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        placeholderExpansion = new ItemPlaceholderExpansion(this);
        placeholderExpansion.register();
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

    public EmakiItemIdentifier identifier() {
        return identifier;
    }

    public EmakiItemPdcWriter pdcWriter() {
        return pdcWriter;
    }

    public EmakiItemFactory itemFactory() {
        return itemFactory;
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

    public ItemSourceService itemSourceService() {
        return itemSourceService;
    }

    public PdcAttributeGateway pdcAttributeGateway() {
        return pdcAttributeGateway;
    }
}
