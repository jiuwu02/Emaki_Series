package emaki.jiuwu.craft.cooking;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.bukkit.command.PluginCommand;

import emaki.jiuwu.craft.corelib.action.ActionExecutor;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.debug.DebugCommand;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
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
import emaki.jiuwu.craft.corelib.web.WebConsoleRegistry;
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
import emaki.jiuwu.craft.cooking.service.CookingEffectService;
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
import emaki.jiuwu.craft.cooking.papi.CookingPlaceholderExpansion;
import emaki.jiuwu.craft.cooking.service.display.CookingDisplayService;

public final class EmakiCookingPlugin extends AbstractConfigurableEmakiPlugin<AppConfig> implements LogMessagesProvider, EmakiServiceRegistry {

    private static final String ROOT_COMMAND = "ecooking";
    private static final String WEB_ICON = """
            <svg viewBox="0 0 38 38" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="M10 21h18l-2 8H12l-2-8zM8 21h22M13 16c-1.5-2 .8-3.5-.3-5M19 16c-1.5-2 .8-3.5-.3-5M25 16c-1.5-2 .8-3.5-.3-5M14 29l-2 3M24 29l2 3M15 21c1.2 2 6.8 2 8 0" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>
            """;

    private static final String STARTUP_ASCII = """
 ______  __    __  ______  __  __   __  ______  ______  ______  __  __   __  __   __  ______    
/\\  ___\\/\\ "-./  \\/\\  __ \\/\\ \\/ /  /\\ \\/\\  ___\\/\\  __ \\/\\  __ \\/\\ \\/ /  /\\ \\/\\ "-.\\ \\/\\  ___\\   
\\ \\  __\\\\ \\ \\-./\\ \\ \\  __ \\ \\  _"-.\\ \\ \\ \\ \\___\\ \\ \\/\\ \\ \\ \\/\\ \\ \\  _"-.\\ \\ \\ \\ \\-.  \\ \\ \\__ \\  
 \\ \\_____\\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_\\ \\_\\\\ \\_\\ \\_____\\ \\_____\\ \\_____\\ \\_\\ \\_\\\\ \\_\\ \\_\\\\"\\_\\ \\_____\\ 
  \\/_____/\\/_/  \\/_/\\/_/\\/_/\\/_/\\/_/ \\/_/\\/_____/\\/_____/\\/_____/\\/_/\\/_/ \\/_/\\/_/ \\/_/\\/_____/ 
""";

    private static final Set<String> DEBUG_MODULES = Set.of("recipe", "stir", "display", "station");

    private final CookingLifecycleCoordinator lifecycleCoordinator = new CookingLifecycleCoordinator();
    private final CookingCommandRouter commandRouter = new CookingCommandRouter(this);
    private CookingStationListener stationListener;
    private DebugCommand debugCommand;

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
    private CookingEffectService effectService;
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
        registerWebConsole();
        registerPlaceholderExpansion();
        messageService.info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        WebConsoleRegistry.unregisterModule(this);
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

    public CompletableFuture<Void> reloadPluginStateAsync() {
        return lifecycleCoordinator.reloadAsync(this, null);
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
        effectService = new CookingEffectService(this, coreActionExecutor, settingsService);
        choppingBoardRuntimeService = components.choppingBoardRuntimeService();
        wokRuntimeService = components.wokRuntimeService();
        grinderRuntimeService = components.grinderRuntimeService();
        steamerRuntimeService = components.steamerRuntimeService();
        ovenRuntimeService = components.ovenRuntimeService();
        juicerRuntimeService = components.juicerRuntimeService();
        fermentationBarrelRuntimeService = components.fermentationBarrelRuntimeService();
        stationListener = new CookingStationListener(choppingBoardRuntimeService, wokRuntimeService, grinderRuntimeService, steamerRuntimeService, ovenRuntimeService, juicerRuntimeService, fermentationBarrelRuntimeService);
        setDebugLogger(new DebugLogger(getLogger(), languageLoader));
        debugCommand = new DebugCommand(debugLogger(), DEBUG_MODULES);
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

    private void registerWebConsole() {
        WebConsoleRegistry.registerModule(this, "Cooking 烹饪", "工位、展示实体、输入规则", "cooking", WEB_ICON);
        WebConsoleRegistry.registerConfigFile(this, "烹饪系统配置", "config.yml", "烹饪系统主配置，包含工位、展示实体和输入规则。");
        WebConsoleRegistry.registerGuiFile(this, "烹饪 GUI 模板", "gui/**/*.yml", "烹饪工位 GUI 模板文件。");
        WebConsoleRegistry.registerCommonConfigComments(this);

        // input_rules
        WebConsoleRegistry.registerNodeComment(this, "input_rules", "输入规则", "工位物品输入的限制规则。", "object");
        WebConsoleRegistry.registerNodeComment(this, "input_rules.only_recipe_items", "严格模式", "是否只允许配方中定义的物品进入工位。", "boolean");

        // display_entities
        WebConsoleRegistry.registerNodeComment(this, "display_entities", "展示实体", "工位上方食材展示实体的全局配置。", "object");
        WebConsoleRegistry.registerNodeComment(this, "display_entities.backend", "渲染后端", "展示实体后端实现。", "enum:auto,packet_events,bukkit");
        WebConsoleRegistry.registerNodeComment(this, "display_entities.view_distance_blocks", "可视距离", "展示实体的最大可视距离（格）。", "number");
        WebConsoleRegistry.registerNodeComment(this, "display_entities.refresh_interval_ticks", "刷新间隔", "展示实体状态刷新间隔 tick 数。", "number");
        WebConsoleRegistry.registerNodeComment(this, "display_entities.wok.layout_radius", "炒锅半径", "炒锅食材展示实体的布局半径。", "number");

        // display_adjustments
        WebConsoleRegistry.registerNodeComment(this, "display_adjustments", "展示调整", "各工位展示实体的位置和缩放微调配置。", "object");

        // stations
        WebConsoleRegistry.registerNodeComment(this, "stations", "工位配置", "砧板、炒锅、研磨机、蒸锅等工位运行配置。", "object");
        WebConsoleRegistry.registerNodeComment(this, "stations.chopping_board", "砧板", "砧板工位的方块匹配与交互配置。", "object");
        WebConsoleRegistry.registerNodeComment(this, "stations.wok", "炒锅", "炒锅工位的方块匹配与翻炒配置。", "object");
        WebConsoleRegistry.registerNodeComment(this, "stations.grinder", "研磨机", "研磨机工位的方块匹配与研磨配置。", "object");
        WebConsoleRegistry.registerNodeComment(this, "stations.steamer", "蒸锅", "蒸锅工位的方块匹配与蒸制配置。", "object");
        WebConsoleRegistry.registerNodeComment(this, "stations.oven", "烤炉", "烤炉工位的方块匹配与烘烤配置。", "object");
        WebConsoleRegistry.registerNodeComment(this, "stations.juicer", "榨汁机", "榨汁机工位的方块匹配与榨汁配置。", "object");
        WebConsoleRegistry.registerNodeComment(this, "stations.fermentation_barrel", "发酵桶", "发酵桶工位的方块匹配与发酵配置。", "object");
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

    private void registerPlaceholderExpansion() {
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new CookingPlaceholderExpansion(this).register();
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

    public CookingEffectService effectService() {
        return effectService;
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

    public DebugCommand debugCommand() {
        return debugCommand;
    }
}
