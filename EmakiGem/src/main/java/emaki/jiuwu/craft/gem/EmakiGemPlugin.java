package emaki.jiuwu.craft.gem;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.integration.ReflectivePdcAttributeGateway;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.plugin.AbstractConfigurableEmakiPlugin;
import emaki.jiuwu.craft.corelib.service.EmakiServiceRegistry;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.AdventureSupport;
import emaki.jiuwu.craft.corelib.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.gem.config.AppConfig;
import emaki.jiuwu.craft.gem.loader.GemItemLoader;
import emaki.jiuwu.craft.gem.loader.GemLoader;
import emaki.jiuwu.craft.gem.service.GemActionCoordinator;
import emaki.jiuwu.craft.gem.service.GemEconomyService;
import emaki.jiuwu.craft.gem.service.GemExtractService;
import emaki.jiuwu.craft.gem.service.GemGuiService;
import emaki.jiuwu.craft.gem.service.GemInlayService;
import emaki.jiuwu.craft.gem.service.GemItemFactory;
import emaki.jiuwu.craft.gem.service.GemItemMatcher;
import emaki.jiuwu.craft.gem.service.GemPdcAttributeWriter;
import emaki.jiuwu.craft.gem.service.GemSnapshotBuilder;
import emaki.jiuwu.craft.gem.service.GemStateService;
import emaki.jiuwu.craft.gem.service.GemUpgradeService;
import emaki.jiuwu.craft.gem.service.SocketOpenerService;

public final class EmakiGemPlugin extends AbstractConfigurableEmakiPlugin<AppConfig> implements LogMessagesProvider, EmakiServiceRegistry {

    private static final String ROOT_COMMAND = "emakigem";

    private static final String STARTUP_ASCII = """
 ______  __    __  ______  __  __   __  ______  ______  __    __
/\\  ___\\/\\ "-./  \\/\\  __ \\/\\ \\/ /  /\\ \\/\\  ___\\/\\  ___\\/\\ "-./  \\
\\ \\  __\\\\ \\ \\-./\\ \\ \\  __ \\ \\  _"-.\\ \\ \\ \\ \\__ \\ \\  __\\\\ \\ \\-./\\ \\
 \\ \\_____\\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_\\ \\_\\\\ \\_\\ \\_____\\ \\_____\\ \\_\\ \\ \\_\\
  \\/_____/\\/_/  \\/_/\\/_/\\/_/\\/_/\\/_/ \\/_/\\/_____/\\/_____/\\/_/  \\/_/
""";

    private final GemLifecycleCoordinator lifecycleCoordinator = new GemLifecycleCoordinator();
    private final GemCommandRouter commandRouter = new GemCommandRouter(this);

    private YamlConfigLoader<AppConfig> appConfigLoader;
    private LanguageLoader languageLoader;
    private GemLoader gemLoader;
    private GemItemLoader gemItemLoader;
    private GuiTemplateLoader guiTemplateLoader;
    private MessageService messageService;
    private BootstrapService bootstrapService;
    private GuiService guiService;
    private ItemSourceService coreItemSourceService;
    private ReflectivePdcAttributeGateway pdcAttributeGateway;
    private GemItemMatcher itemMatcher;
    private GemItemFactory itemFactory;
    private GemSnapshotBuilder snapshotBuilder;
    private GemPdcAttributeWriter pdcAttributeWriter;
    private GemStateService stateService;
    private GemEconomyService economyService;
    private GemActionCoordinator actionCoordinator;
    private SocketOpenerService socketOpenerService;
    private GemInlayService inlayService;
    private GemExtractService extractService;
    private GemUpgradeService upgradeService;
    private GemGuiService gemGuiService;

    public EmakiGemPlugin() {
        super(AppConfig::defaults);
    }

    @Override
    public void onEnable() {
        ConsoleOutputs.sendGradientAscii(this, STARTUP_ASCII);
        applyRuntimeComponents(lifecycleCoordinator.initialize(this));
        if (languageLoader != null) {
            languageLoader.load();
            languageLoader.setLanguage(appConfig().language());
        }
        messageService.info("console.plugin_starting");
        bootstrapService.bootstrap();
        reloadPluginState(false);
        registerCommandHandler();
        registerEventHandlers();
        messageService.info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        lifecycleCoordinator.shutdown(this);
        AdventureSupport.close(this);
    }

    public void reloadPluginState(boolean closeOpenInventories) {
        lifecycleCoordinator.reload(this, closeOpenInventories);
    }

    private void applyRuntimeComponents(GemRuntimeComponents components) {
        appConfigLoader = components.appConfigLoader();
        languageLoader = components.languageLoader();
        gemLoader = components.gemLoader();
        gemItemLoader = components.gemItemLoader();
        guiTemplateLoader = components.guiTemplateLoader();
        messageService = components.messageService();
        bootstrapService = components.bootstrapService();
        guiService = components.guiService();
        coreItemSourceService = components.coreItemSourceService();
        pdcAttributeGateway = components.pdcAttributeGateway();
        itemMatcher = components.itemMatcher();
        itemFactory = components.itemFactory();
        snapshotBuilder = components.snapshotBuilder();
        pdcAttributeWriter = components.pdcAttributeWriter();
        stateService = components.stateService();
        economyService = components.economyService();
        actionCoordinator = components.actionCoordinator();
        socketOpenerService = components.socketOpenerService();
        inlayService = components.inlayService();
        extractService = components.extractService();
        upgradeService = components.upgradeService();
        gemGuiService = components.gemGuiService();
        registerServices(components);
    }

    private void registerCommandHandler() {
        PluginCommand pluginCommand = getCommand(ROOT_COMMAND);
        if (pluginCommand == null) {
            return;
        }
        pluginCommand.setExecutor(commandRouter);
        pluginCommand.setTabCompleter(commandRouter);
    }

    private void registerEventHandlers() {
        if (guiService != null) {
            getServer().getPluginManager().registerEvents(guiService, this);
        }
    }

    public YamlConfigLoader<AppConfig> appConfigLoader() {
        return appConfigLoader;
    }

    public LanguageLoader languageLoader() {
        return languageLoader;
    }

    public GemLoader gemLoader() {
        return gemLoader;
    }

    public GemItemLoader gemItemLoader() {
        return gemItemLoader;
    }

    public GuiTemplateLoader guiTemplateLoader() {
        return guiTemplateLoader;
    }

    @Override
    public MessageService messageService() {
        return messageService;
    }

    public BootstrapService bootstrapService() {
        return bootstrapService;
    }

    public GuiService guiService() {
        return guiService;
    }

    public EmakiCoreLibPlugin coreLib() {
        return JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
    }

    public ItemSourceService coreItemSourceService() {
        return coreItemSourceService;
    }

    public ReflectivePdcAttributeGateway pdcAttributeGateway() {
        return pdcAttributeGateway;
    }

    public GemItemMatcher itemMatcher() {
        return itemMatcher;
    }

    public GemItemFactory itemFactory() {
        return itemFactory;
    }

    public GemSnapshotBuilder snapshotBuilder() {
        return snapshotBuilder;
    }

    public GemPdcAttributeWriter pdcAttributeWriter() {
        return pdcAttributeWriter;
    }

    public GemStateService stateService() {
        return stateService;
    }

    public GemEconomyService economyService() {
        return economyService;
    }

    public GemActionCoordinator actionCoordinator() {
        return actionCoordinator;
    }

    public SocketOpenerService socketOpenerService() {
        return socketOpenerService;
    }

    public GemInlayService inlayService() {
        return inlayService;
    }

    public GemExtractService extractService() {
        return extractService;
    }

    public GemUpgradeService upgradeService() {
        return upgradeService;
    }

    public GemGuiService gemGuiService() {
        return gemGuiService;
    }
}
