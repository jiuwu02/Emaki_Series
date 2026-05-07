package emaki.jiuwu.craft.cooking;

import java.util.Map;

import org.bukkit.command.PluginCommand;

import emaki.jiuwu.craft.corelib.action.ActionExecutor;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.api.integration.CraftEngineBlockBridge;
import emaki.jiuwu.craft.corelib.api.integration.CustomBlockBridge;
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
import emaki.jiuwu.craft.cooking.loader.FermentationBarrelRecipeLoader;
import emaki.jiuwu.craft.cooking.loader.GrinderRecipeLoader;
import emaki.jiuwu.craft.cooking.loader.JuicerRecipeLoader;
import emaki.jiuwu.craft.cooking.loader.OvenRecipeLoader;
import emaki.jiuwu.craft.cooking.loader.SteamerRecipeLoader;
import emaki.jiuwu.craft.cooking.loader.WokRecipeLoader;
import emaki.jiuwu.craft.cooking.service.ChoppingBoardRuntimeService;
import emaki.jiuwu.craft.cooking.service.CookingBlockMatcher;
import emaki.jiuwu.craft.cooking.service.CookingInspectService;
import emaki.jiuwu.craft.cooking.service.CookingRecipeService;
import emaki.jiuwu.craft.cooking.service.CookingRewardService;
import emaki.jiuwu.craft.cooking.service.CookingSettingsService;
import emaki.jiuwu.craft.cooking.service.FermentationBarrelRuntimeService;
import emaki.jiuwu.craft.cooking.service.GrinderRuntimeService;
import emaki.jiuwu.craft.cooking.service.JuicerRuntimeService;
import emaki.jiuwu.craft.cooking.service.OvenRuntimeService;
import emaki.jiuwu.craft.cooking.service.StationStateStore;
import emaki.jiuwu.craft.cooking.service.SteamerRuntimeService;
import emaki.jiuwu.craft.cooking.service.WokRuntimeService;
import emaki.jiuwu.craft.cooking.service.display.CookingDisplayService;

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
    private OvenRecipeLoader ovenRecipeLoader;
    private JuicerRecipeLoader juicerRecipeLoader;
    private FermentationBarrelRecipeLoader fermentationBarrelRecipeLoader;
    private MessageService messageService;
    private BootstrapService bootstrapService;
    private ActionExecutor coreActionExecutor;
    private ItemSourceService coreItemSourceService;
    private CraftEngineBlockBridge craftEngineBlockBridge;
    private CustomBlockBridge itemsAdderBlockBridge;
    private CustomBlockBridge nexoBlockBridge;
    private CookingSettingsService settingsService;
    private CookingBlockMatcher blockMatcher;
    private StationStateStore stationStateStore;
    private CookingRecipeService recipeService;
    private CookingRewardService rewardService;
    private CookingInspectService inspectService;
    private CookingDisplayService displayService;
    private ChoppingBoardRuntimeService choppingBoardRuntimeService;
    private WokRuntimeService wokRuntimeService;
    private GrinderRuntimeService grinderRuntimeService;
    private SteamerRuntimeService steamerRuntimeService;
    private OvenRuntimeService ovenRuntimeService;
    private JuicerRuntimeService juicerRuntimeService;
    private FermentationBarrelRuntimeService fermentationBarrelRuntimeService;

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
        if (ovenRuntimeService != null) {
            ovenRuntimeService.shutdown();
        }
        if (juicerRuntimeService != null) {
            juicerRuntimeService.shutdown();
        }
        if (fermentationBarrelRuntimeService != null) {
            fermentationBarrelRuntimeService.shutdown();
        }
        if (displayService != null) {
            displayService.shutdown();
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
        ovenRecipeLoader = components.ovenRecipeLoader();
        juicerRecipeLoader = components.juicerRecipeLoader();
        fermentationBarrelRecipeLoader = components.fermentationBarrelRecipeLoader();
        messageService = components.messageService();
        bootstrapService = components.bootstrapService();
        coreActionExecutor = components.coreActionExecutor();
        coreItemSourceService = components.coreItemSourceService();
        craftEngineBlockBridge = components.craftEngineBlockBridge();
        itemsAdderBlockBridge = components.itemsAdderBlockBridge();
        nexoBlockBridge = components.nexoBlockBridge();
        settingsService = components.settingsService();
        blockMatcher = components.blockMatcher();
        stationStateStore = components.stationStateStore();
        recipeService = components.recipeService();
        rewardService = components.rewardService();
        inspectService = components.inspectService();
        displayService = components.displayService();
        choppingBoardRuntimeService = components.choppingBoardRuntimeService();
        wokRuntimeService = components.wokRuntimeService();
        grinderRuntimeService = components.grinderRuntimeService();
        steamerRuntimeService = components.steamerRuntimeService();
        ovenRuntimeService = components.ovenRuntimeService();
        juicerRuntimeService = components.juicerRuntimeService();
        fermentationBarrelRuntimeService = components.fermentationBarrelRuntimeService();
        stationListener = new CookingStationListener(choppingBoardRuntimeService, wokRuntimeService, grinderRuntimeService, steamerRuntimeService, ovenRuntimeService, juicerRuntimeService, fermentationBarrelRuntimeService);
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
        if (ovenRuntimeService != null) {
            getServer().getPluginManager().registerEvents(ovenRuntimeService, this);
        }
        if (juicerRuntimeService != null) {
            getServer().getPluginManager().registerEvents(juicerRuntimeService, this);
        }
        if (fermentationBarrelRuntimeService != null) {
            getServer().getPluginManager().registerEvents(fermentationBarrelRuntimeService, this);
        }
        registerCraftEngineEventHandlers();
        registerItemsAdderEventHandlers();
        registerNexoEventHandlers();
    }

    private void registerCraftEngineEventHandlers() {
        if (stationListener == null || !getServer().getPluginManager().isPluginEnabled("CraftEngine")) {
            return;
        }
        try {
            getServer().getPluginManager().registerEvents(new CraftEngineCookingStationListener(stationListener), this);
            messageService.info("console.block_source_bridge_ready", Map.of("provider", "CraftEngine"));
        } catch (LinkageError exception) {
            messageService.warning("console.block_source_bridge_unavailable", Map.of("provider", "CraftEngine", "error", String.valueOf(exception.getMessage())));
        }
    }

    private void registerItemsAdderEventHandlers() {
        if (stationListener == null || !getServer().getPluginManager().isPluginEnabled("ItemsAdder")) {
            return;
        }
        try {
            getServer().getPluginManager().registerEvents(new ItemsAdderCookingStationListener(stationListener), this);
            messageService.info("console.block_source_bridge_ready", Map.of("provider", "ItemsAdder"));
        } catch (LinkageError exception) {
            messageService.warning("console.block_source_bridge_unavailable", Map.of("provider", "ItemsAdder", "error", String.valueOf(exception.getMessage())));
        }
    }

    private void registerNexoEventHandlers() {
        if (stationListener == null || !getServer().getPluginManager().isPluginEnabled("Nexo")) {
            return;
        }
        try {
            getServer().getPluginManager().registerEvents(new NexoCookingStationListener(stationListener), this);
            messageService.info("console.block_source_bridge_ready", Map.of("provider", "Nexo"));
        } catch (LinkageError exception) {
            messageService.warning("console.block_source_bridge_unavailable", Map.of("provider", "Nexo", "error", String.valueOf(exception.getMessage())));
        }
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

    public OvenRecipeLoader ovenRecipeLoader() {
        return ovenRecipeLoader;
    }

    public JuicerRecipeLoader juicerRecipeLoader() {
        return juicerRecipeLoader;
    }

    public FermentationBarrelRecipeLoader fermentationBarrelRecipeLoader() {
        return fermentationBarrelRecipeLoader;
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

    public CustomBlockBridge itemsAdderBlockBridge() {
        return itemsAdderBlockBridge;
    }

    public CustomBlockBridge nexoBlockBridge() {
        return nexoBlockBridge;
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

    public CookingDisplayService displayService() {
        return displayService;
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

    public OvenRuntimeService ovenRuntimeService() {
        return ovenRuntimeService;
    }

    public JuicerRuntimeService juicerRuntimeService() {
        return juicerRuntimeService;
    }

    public FermentationBarrelRuntimeService fermentationBarrelRuntimeService() {
        return fermentationBarrelRuntimeService;
    }
}
