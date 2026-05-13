package emaki.jiuwu.craft.forge;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.scheduler.BukkitTask;

import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.debug.DebugCommand;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.integration.PdcAttributeGateway;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.plugin.AbstractConfigurableEmakiPlugin;
import emaki.jiuwu.craft.corelib.service.EmakiServiceRegistry;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.AdventureSupport;
import emaki.jiuwu.craft.corelib.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.web.WebConsoleRegistry;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.forge.config.AppConfig;
import emaki.jiuwu.craft.forge.loader.PlayerDataStore;
import emaki.jiuwu.craft.forge.loader.RecipeLoader;
import emaki.jiuwu.craft.forge.papi.ForgePlaceholderExpansion;
import emaki.jiuwu.craft.forge.service.ForgeGuiService;
import emaki.jiuwu.craft.forge.service.ForgeItemRefreshService;
import emaki.jiuwu.craft.forge.service.ForgeService;
import emaki.jiuwu.craft.forge.service.ItemIdentifierService;
import emaki.jiuwu.craft.forge.service.RecipeBookGuiService;

public class EmakiForgePlugin extends AbstractConfigurableEmakiPlugin<AppConfig> implements LogMessagesProvider, EmakiServiceRegistry {

    private static final String ROOT_COMMAND = "emakiforge";

    private static final String STARTUP_ASCII = """
 ______  __    __  ______  __  __   __  ______  ______  ______  ______  ______
/\\  ___\\/\\ "-./  \\/\\  __ \\/\\ \\/ /  /\\ \\/\\  ___\\/\\  __ \\/\\  == \\/\\  ___\\/\\  ___\\
\\ \\  __\\\\ \\ \\-./\\ \\ \\  __ \\ \\  _"-.\\ \\ \\ \\  __\\\\ \\ \\/\\ \\ \\  __<\\ \\ \\__ \\ \\  __\\
 \\ \\_____\\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_\\ \\_\\\\ \\_\\ \\_\\   \\ \\_____\\ \\_\\ \\_\\ \\_____\\ \\_____\\
  \\/_____/\\/_/  \\/_/\\/_/\\/_/\\/_/\\/_/ \\/_/\\/_/    \\/_____/\\/_/ /_/\\/_____/\\/_____/
""";

    private final ForgeLifecycleCoordinator lifecycleCoordinator = new ForgeLifecycleCoordinator();
    private final ForgeCommandRouter commandRouter = new ForgeCommandRouter(this);
    private final ForgePlayerDataListener playerDataListener = new ForgePlayerDataListener(this);
    private final ForgeItemRefreshListener itemRefreshListener = new ForgeItemRefreshListener(this);

    private YamlConfigLoader<AppConfig> appConfigLoader;
    private LanguageLoader languageLoader;
    private RecipeLoader recipeLoader;
    private GuiTemplateLoader guiTemplateLoader;
    private PlayerDataStore playerDataStore;
    private MessageService messageService;
    private BootstrapService bootstrapService;
    private GuiService guiService;
    private ItemIdentifierService itemIdentifierService;
    private PdcAttributeGateway pdcAttributeGateway;
    private ForgeItemRefreshService itemRefreshService;
    private ForgeService forgeService;
    private ForgeGuiService forgeGuiService;
    private RecipeBookGuiService recipeBookGuiService;
    private ForgePlaceholderExpansion placeholderExpansion;
    private BukkitTask autoSaveTask;
    private DebugCommand debugCommand;

    private static final Set<String> DEBUG_MODULES = Set.of("recipe", "forge", "gui");

    public EmakiForgePlugin() {
        super(AppConfig::defaults);
    }

    @Override
    public void onEnable() {
        ConsoleOutputs.sendGradientAscii(this, STARTUP_ASCII);
        applyRuntimeComponents(lifecycleCoordinator.initialize(this));
        messageService.info("console.plugin_starting");
        bootstrapService.bootstrap();
        reloadPluginState(false);
        registerCommandHandler();
        registerEventHandlers();
        registerWebConsole();
        ensurePlaceholderExpansion();
        messageService.info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
            placeholderExpansion = null;
        }
        WebConsoleRegistry.unregisterModule(this);
        lifecycleCoordinator.shutdown(this, autoSaveTask);
        AdventureSupport.close(this);
        autoSaveTask = null;
    }

    public void reloadPluginState(boolean closeOpenInventories) {
        autoSaveTask = lifecycleCoordinator.reload(this, autoSaveTask, closeOpenInventories);
    }

    public CompletableFuture<Void> reloadPluginStateAsync(boolean closeOpenInventories) {
        return lifecycleCoordinator.reloadAsync(this, autoSaveTask, closeOpenInventories, null)
                .thenAccept(task -> autoSaveTask = task);
    }

    private void applyRuntimeComponents(ForgeRuntimeComponents components) {
        appConfigLoader = components.appConfigLoader();
        languageLoader = components.languageLoader();
        recipeLoader = components.recipeLoader();
        guiTemplateLoader = components.guiTemplateLoader();
        playerDataStore = components.playerDataStore();
        messageService = components.messageService();
        bootstrapService = components.bootstrapService();
        guiService = components.guiService();
        itemIdentifierService = components.itemIdentifierService();
        pdcAttributeGateway = components.pdcAttributeGateway();
        itemRefreshService = components.itemRefreshService();
        forgeService = components.forgeService();
        forgeGuiService = components.forgeGuiService();
        recipeBookGuiService = components.recipeBookGuiService();
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
        getServer().getPluginManager().registerEvents(guiService, this);
        getServer().getPluginManager().registerEvents(playerDataListener, this);
        getServer().getPluginManager().registerEvents(itemRefreshListener, this);
    }

    private void registerWebConsole() {
        WebConsoleRegistry.registerModule(getName(), "Forge 锻造", "品质池、保底、历史与条件", "forge");
        WebConsoleRegistry.registerConfigFile(getName(), "锻造系统配置", "config.yml", "锻造系统主配置，包含品质、配方、GUI 和玩家数据策略。 ");
        WebConsoleRegistry.registerGuiFile(getName(), "锻造 GUI 模板", "gui/**/*.yml", "锻造、配方书与编辑器 GUI 模板文件。");
        WebConsoleRegistry.registerCommonConfigComments(getName());

        // quality
        WebConsoleRegistry.registerNodeComment(getName(), "quality", "品质配置", "品质池、保底和物品显示规则。", "object");
        WebConsoleRegistry.registerNodeComment(getName(), "quality.tiers", "品质池", "品质列表，格式为 \"名称-权重-倍率\"。", "list");
        WebConsoleRegistry.registerNodeComment(getName(), "quality.default_tier", "回退品质", "随机未命中任何品质时使用的回退品质名称。", "text");
        WebConsoleRegistry.registerNodeComment(getName(), "quality.guarantee", "保底配置", "品质保底触发条件与最低品质设置。", "object");
        WebConsoleRegistry.registerNodeComment(getName(), "quality.guarantee.enabled", "启用保底", "是否启用品质保底机制。", "boolean");
        WebConsoleRegistry.registerNodeComment(getName(), "quality.guarantee.threshold", "保底阈值", "连续未出高品质达到此次数后触发保底。", "number");
        WebConsoleRegistry.registerNodeComment(getName(), "quality.guarantee.minimum", "保底品质", "保底触发时给予的最低品质名称。", "text");
        WebConsoleRegistry.registerNodeComment(getName(), "quality.item_meta", "物品显示", "品质写入物品名称和 Lore 的配置。", "object");
        WebConsoleRegistry.registerNodeComment(getName(), "quality.item_meta.enabled", "启用写入", "是否将品质信息写入物品名称和 Lore。", "boolean");
        WebConsoleRegistry.registerNodeComment(getName(), "quality.item_meta.tiers", "品质显示", "各品质的名称动作和广播配置。", "object");

        // number_format
        WebConsoleRegistry.registerNodeComment(getName(), "number_format", "数值格式", "锻造结果数值的格式化配置。", "object");
        WebConsoleRegistry.registerNodeComment(getName(), "number_format.default", "默认格式", "默认数值显示格式（如 #.##）。", "text");
        WebConsoleRegistry.registerNodeComment(getName(), "number_format.integer", "整数格式", "整数数值显示格式。", "text");
        WebConsoleRegistry.registerNodeComment(getName(), "number_format.percentage", "百分比格式", "百分比数值显示格式。", "text");

        // permission
        WebConsoleRegistry.registerNodeComment(getName(), "permission.op_bypass", "OP跳过", "OP 是否跳过锻造条件检查。", "boolean");

        // condition
        WebConsoleRegistry.registerNodeComment(getName(), "condition", "条件配置", "锻造条件表达式解析与判定配置。", "object");
        WebConsoleRegistry.registerNodeComment(getName(), "condition.invalid_as_failure", "解析失败", "条件表达式解析失败时是否视为不通过。", "boolean");

        // history
        WebConsoleRegistry.registerNodeComment(getName(), "history", "锻造历史", "玩家锻造历史记录与自动保存配置。", "object");
        WebConsoleRegistry.registerNodeComment(getName(), "history.enabled", "启用历史", "是否启用锻造历史记录功能。", "boolean");
        WebConsoleRegistry.registerNodeComment(getName(), "history.auto_save", "自动保存", "是否定时自动保存锻造历史。", "boolean");
        WebConsoleRegistry.registerNodeComment(getName(), "history.save_interval", "保存间隔", "自动保存锻造历史的间隔 tick 数。", "number");
    }

    private void ensurePlaceholderExpansion() {
        if (placeholderExpansion != null) {
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        placeholderExpansion = new ForgePlaceholderExpansion(this, playerDataStore);
        placeholderExpansion.register();
        messageService.info("console.papi_registered");
    }

    public YamlConfigLoader<AppConfig> appConfigLoader() {
        return appConfigLoader;
    }

    public LanguageLoader languageLoader() {
        return languageLoader;
    }

    public RecipeLoader recipeLoader() {
        return recipeLoader;
    }

    public GuiTemplateLoader guiTemplateLoader() {
        return guiTemplateLoader;
    }

    public PlayerDataStore playerDataStore() {
        return playerDataStore;
    }

    public MessageService messageService() {
        return messageService;
    }

    public BootstrapService bootstrapService() {
        return bootstrapService;
    }

    public GuiService guiService() {
        return guiService;
    }

    public ItemIdentifierService itemIdentifierService() {
        return itemIdentifierService;
    }

    public PdcAttributeGateway pdcAttributeGateway() {
        return pdcAttributeGateway;
    }

    public ForgeItemRefreshService itemRefreshService() {
        return itemRefreshService;
    }

    public ForgeService forgeService() {
        return forgeService;
    }

    public ForgeGuiService forgeGuiService() {
        return forgeGuiService;
    }

    public RecipeBookGuiService recipeBookGuiService() {
        return recipeBookGuiService;
    }

    public DebugCommand debugCommand() {
        return debugCommand;
    }
}
