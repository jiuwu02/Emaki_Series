package emaki.jiuwu.craft.cooking;

import org.bukkit.command.PluginCommand;

import emaki.jiuwu.craft.corelib.action.ActionExecutor;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.integration.CraftEngineBlockBridge;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.plugin.AbstractConfigurableEmakiPlugin;
import emaki.jiuwu.craft.corelib.service.EmakiServiceRegistry;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.AdventureSupport;
import emaki.jiuwu.craft.corelib.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.cooking.config.AppConfig;
import emaki.jiuwu.craft.cooking.loader.ChoppingBoardRecipeLoader;
import emaki.jiuwu.craft.cooking.loader.GrinderRecipeLoader;
import emaki.jiuwu.craft.cooking.loader.SteamerRecipeLoader;
import emaki.jiuwu.craft.cooking.loader.WokRecipeLoader;
import emaki.jiuwu.craft.cooking.service.ChoppingBoardRuntimeService;
import emaki.jiuwu.craft.cooking.service.CookingBlockMatcher;
import emaki.jiuwu.craft.cooking.service.CookingInspectService;
import emaki.jiuwu.craft.cooking.service.CookingRecipeService;
import emaki.jiuwu.craft.cooking.service.CookingRewardService;
import emaki.jiuwu.craft.cooking.service.CookingSettingsService;
import emaki.jiuwu.craft.cooking.service.GrinderRuntimeService;
import emaki.jiuwu.craft.cooking.service.StationStateStore;
import emaki.jiuwu.craft.cooking.service.SteamerRuntimeService;
import emaki.jiuwu.craft.cooking.service.WokRuntimeService;

public final class EmakiCookingPlugin extends AbstractConfigurableEmakiPlugin<AppConfig> implements LogMessagesProvider, EmakiServiceRegistry {

    private static final String ROOT_COMMAND = "ecooking";

    private static final String STARTUP_ASCII = """
 ______  __    __  ______  __  __   __  ______  ______  ______  __  __   __  __   __  ______    
/\\  ___\\/\\ "-./  \\/\\  __ \\/\\ \\/ /  /\\ \\/\\  ___\\/\\  __ \\/\\  __ \\/\\ \\/ /  /\\ \\/\\ "-.\\ \\/\\  ___\\   
\\ \\  __\\\\ \\ \\-./\\ \\ \\  __ \\ \\  _"-.\\ \\ \\ \\ \\___\\ \\ \\/\\ \\ \\ \\/\\ \\ \\  _"-.\\ \\ \\ \\ \\-.  \\ \\ \\__ \\  
 \\ \\_____\\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_\\ \\_\\\\ \\_\\ \\_____\\ \\_____\\ \\_____\\ \\_\\ \\_\\\\ \\_\\ \\_\\\\"\\_\\ \\_____\\ 
  \\/_____/\\/_/  \\/_/\\/_/\\/_/\\/_/\\/_/ \\/_/\\/_____/\\/_____/\\/_____/\\/_/\\/_/ \\/_/\\/_/ \\/_/\\/_____/ 
""";

    private final CookingLifecycleCoordinator lifecycleCoordinator = new CookingLifecycleCoordinator();
    private final CookingCommandRouter commandRouter = new CookingCommandRouter(this);
    private CookingStationListener stationListener;

    private YamlConfigLoader<AppConfig> appConfigLoader;
    private LanguageLoader languageLoader;
    private ChoppingBoardRecipeLoader choppingBoardRecipeLoader;
    private WokRecipeLoader wokRecipeLoader;
    private GrinderRecipeLoader grinderRecipeLoader;
    private SteamerRecipeLoader steamerRecipeLoader;
    private MessageService messageService;
    private BootstrapService bootstrapService;
    private ActionExecutor coreActionExecutor;
    private ItemSourceService coreItemSourceService;
    private CraftEngineBlockBridge craftEngineBlockBridge;
    private CookingSettingsService settingsService;
    private CookingBlockMatcher blockMatcher;
    private StationStateStore stationStateStore;
    private CookingRecipeService recipeService;
    private CookingRewardService rewardService;
    private CookingInspectService inspectService;
    private ChoppingBoardRuntimeService choppingBoardRuntimeService;
    private WokRuntimeService wokRuntimeService;
    private GrinderRuntimeService grinderRuntimeService;
    private SteamerRuntimeService steamerRuntimeService;

    public EmakiCookingPlugin() {
        super(AppConfig::defaults);
    }

    @Override
    public void onEnable() {
        ConsoleOutputs.sendGradientAscii(this, STARTUP_ASCII);
        applyRuntimeComponents(lifecycleCoordinator.initialize(this));
        messageService.info("console.plugin_starting");
        bootstrapService.bootstrap();
        reloadPluginState();
        registerCommandHandler();
        registerEventHandlers();
        messageService.info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        if (grinderRuntimeService != null) {
            grinderRuntimeService.shutdown();
        }
        if (steamerRuntimeService != null) {
            steamerRuntimeService.shutdown();
        }
        if (messageService != null) {
            messageService.info("console.plugin_stopped");
        }
        AdventureSupport.close(this);
    }

    public void reloadPluginState() {
        lifecycleCoordinator.reload(this);
    }

    private void applyRuntimeComponents(CookingRuntimeComponents components) {
        appConfigLoader = components.appConfigLoader();
        languageLoader = components.languageLoader();
        choppingBoardRecipeLoader = components.choppingBoardRecipeLoader();
        wokRecipeLoader = components.wokRecipeLoader();
        grinderRecipeLoader = components.grinderRecipeLoader();
        steamerRecipeLoader = components.steamerRecipeLoader();
        messageService = components.messageService();
        bootstrapService = components.bootstrapService();
        coreActionExecutor = components.coreActionExecutor();
        coreItemSourceService = components.coreItemSourceService();
        craftEngineBlockBridge = components.craftEngineBlockBridge();
        settingsService = components.settingsService();
        blockMatcher = components.blockMatcher();
        stationStateStore = components.stationStateStore();
        recipeService = components.recipeService();
        rewardService = components.rewardService();
        inspectService = components.inspectService();
        choppingBoardRuntimeService = components.choppingBoardRuntimeService();
        wokRuntimeService = components.wokRuntimeService();
        grinderRuntimeService = components.grinderRuntimeService();
        steamerRuntimeService = components.steamerRuntimeService();
        stationListener = new CookingStationListener(this, choppingBoardRuntimeService, wokRuntimeService, grinderRuntimeService, steamerRuntimeService);
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
        if (stationListener == null) {
            return;
        }
        getServer().getPluginManager().registerEvents(stationListener, this);
        if (steamerRuntimeService != null) {
            getServer().getPluginManager().registerEvents(steamerRuntimeService, this);
        }
        stationListener.registerReflectiveEvents();
    }

    @Override
    public YamlConfigLoader<AppConfig> appConfigLoader() {
        return appConfigLoader;
    }

    public LanguageLoader languageLoader() {
        return languageLoader;
    }

    public ChoppingBoardRecipeLoader choppingBoardRecipeLoader() {
        return choppingBoardRecipeLoader;
    }

    public WokRecipeLoader wokRecipeLoader() {
        return wokRecipeLoader;
    }

    public GrinderRecipeLoader grinderRecipeLoader() {
        return grinderRecipeLoader;
    }

    public SteamerRecipeLoader steamerRecipeLoader() {
        return steamerRecipeLoader;
    }

    @Override
    public MessageService messageService() {
        return messageService;
    }

    public BootstrapService bootstrapService() {
        return bootstrapService;
    }

    public ActionExecutor coreActionExecutor() {
        return coreActionExecutor;
    }

    public ItemSourceService coreItemSourceService() {
        return coreItemSourceService;
    }

    public CraftEngineBlockBridge craftEngineBlockBridge() {
        return craftEngineBlockBridge;
    }

    public CookingSettingsService settingsService() {
        return settingsService;
    }

    public CookingBlockMatcher blockMatcher() {
        return blockMatcher;
    }

    public StationStateStore stationStateStore() {
        return stationStateStore;
    }

    public CookingRecipeService recipeService() {
        return recipeService;
    }

    public CookingRewardService rewardService() {
        return rewardService;
    }

    public CookingInspectService inspectService() {
        return inspectService;
    }

    public ChoppingBoardRuntimeService choppingBoardRuntimeService() {
        return choppingBoardRuntimeService;
    }

    public WokRuntimeService wokRuntimeService() {
        return wokRuntimeService;
    }

    public GrinderRuntimeService grinderRuntimeService() {
        return grinderRuntimeService;
    }

    public SteamerRuntimeService steamerRuntimeService() {
        return steamerRuntimeService;
    }
}
