package emaki.jiuwu.craft.strengthen;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;

import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.debug.DebugCommand;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.integration.PdcAttributeGateway;
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
import emaki.jiuwu.craft.strengthen.api.EmakiStrengthenApi;
import emaki.jiuwu.craft.strengthen.config.AppConfig;
import emaki.jiuwu.craft.strengthen.loader.StrengthenRecipeLoader;
import emaki.jiuwu.craft.strengthen.papi.StrengthenPlaceholderExpansion;
import emaki.jiuwu.craft.strengthen.service.ChanceCalculator;
import emaki.jiuwu.craft.strengthen.service.StrengthenRecipeResolver;
import emaki.jiuwu.craft.strengthen.service.StrengthenActionCoordinator;
import emaki.jiuwu.craft.strengthen.service.StrengthenAttemptService;
import emaki.jiuwu.craft.strengthen.service.StrengthenEconomyService;
import emaki.jiuwu.craft.strengthen.service.StrengthenGuiService;
import emaki.jiuwu.craft.strengthen.service.StrengthenRefreshService;
import emaki.jiuwu.craft.strengthen.service.StrengthenSnapshotBuilder;

public final class EmakiStrengthenPlugin extends AbstractConfigurableEmakiPlugin<AppConfig> implements LogMessagesProvider, EmakiServiceRegistry {

    private static final String ROOT_COMMAND = "emakistrengthen";
    private static final String WEB_ICON = """
            <svg viewBox="0 0 38 38" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="M19 30V12M13 18l6-6 6 6M12 8l1.6 3.2 3.4.5-2.5 2.4.6 3.4-3.1-1.6-3.1 1.6.6-3.4-2.5-2.4 3.4-.5L12 8zM27 7l1.2 2.5 2.8.4-2 1.9.5 2.7-2.5-1.3-2.5 1.3.5-2.7-2-1.9 2.8-.4L27 7zM11 31h16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>
            """;
    private static final Set<String> DEBUG_MODULES = Set.of("attempt", "state", "gui");

    private static final String STARTUP_ASCII = """
 ______  __    __  ______  __  __   __  ______  ______  ______  ______  __   __  ______  ______  __  __  ______  __   __    
/\\  ___\\/\\ "-./  \\/\\  __ \\/\\ \\/ /  /\\ \\/\\  ___\\/\\__  _\\/\\  == \\/\\  ___\\/\\ "-.\\ \\/\\  ___\\/\\__  _\\/\\ \\_\\ \\/\\  ___\\/\\ "-.\\ \\
\\ \\  __\\\\ \\ \\-./\\ \\ \\  __ \\ \\  _"-.\\ \\ \\ \\___  \\/_/\\ \\/\\ \\  __<\\ \\  __\\\\ \\ \\-.  \\ \\ \\__ \\/_/\\ \\/\\ \\  __ \\ \\  __\\\\ \\ \\-.  \\
 \\ \\_____\\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_\\ \\_\\\\ \\_\\/\\_____\\ \\ \\_\\ \\ \\_\\ \\_\\ \\_____\\ \\_\\\\"\\_\\ \\_____\\ \\ \\_\\ \\ \\_\\ \\_\\ \\_____\\ \\_\\\\"\\_\\
  \\/_____/\\/_/  \\/_/\\/_/\\/_/\\/_/\\/_/ \\/_/\\/_____/  \\/_/  \\/_/ /_/\\/_____/\\/_/ \\/_/\\/_____/  \\/_/  \\/_/\\/_/\\/_____/\\/_/ \\/_/
""";

    private final StrengthenLifecycleCoordinator lifecycleCoordinator = new StrengthenLifecycleCoordinator();
    private final StrengthenCommandRouter commandRouter = new StrengthenCommandRouter(this);
    private final StrengthenItemRefreshListener itemRefreshListener = new StrengthenItemRefreshListener(this);
    private ItemSourceService coreItemSourceService;
    private DebugCommand debugCommand;
    private final GuiItemBuilder.ItemFactory coreItemFactory = (source, amount) -> {
        return coreItemSourceService == null ? null : coreItemSourceService.createItem(source, amount);
    };

    private YamlConfigLoader<AppConfig> appConfigLoader;
    private LanguageLoader languageLoader;
    private StrengthenRecipeLoader recipeLoader;
    private GuiTemplateLoader guiTemplateLoader;
    private MessageService messageService;
    private BootstrapService bootstrapService;
    private GuiService guiService;
    private PdcAttributeGateway pdcAttributeGateway;
    private StrengthenRecipeResolver recipeResolver;
    private ChanceCalculator chanceCalculator;
    private StrengthenEconomyService economyService;
    private StrengthenSnapshotBuilder snapshotBuilder;
    private StrengthenActionCoordinator actionCoordinator;
    private StrengthenAttemptService attemptService;
    private StrengthenRefreshService refreshService;
    private StrengthenGuiService strengthenGuiService;
    private StrengthenPlaceholderExpansion placeholderExpansion;

    public EmakiStrengthenPlugin() {
        super(AppConfig::defaults);
    }

    @Override
    public void onEnable() {
        ConsoleOutputs.sendGradientAscii(this, STARTUP_ASCII);
        applyRuntimeComponents(lifecycleCoordinator.initialize(this));
        messageService.info("console.plugin_starting");
        bootstrapService.bootstrap();
        reloadPluginState(false);
        registerApi();
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
        getServer().getServicesManager().unregisterAll(this);
        lifecycleCoordinator.shutdown(this);
        AdventureSupport.close(this);
    }

    public void reloadPluginState(boolean closeOpenInventories) {
        lifecycleCoordinator.reload(this, closeOpenInventories);
    }

    public CompletableFuture<Void> reloadPluginStateAsync(boolean closeOpenInventories) {
        return lifecycleCoordinator.reloadAsync(this, closeOpenInventories, null);
    }

    private void applyRuntimeComponents(StrengthenRuntimeComponents components) {
        appConfigLoader = components.appConfigLoader();
        languageLoader = components.languageLoader();
        recipeLoader = components.recipeLoader();
        guiTemplateLoader = components.guiTemplateLoader();
        messageService = components.messageService();
        bootstrapService = components.bootstrapService();
        guiService = components.guiService();
        coreItemSourceService = components.coreItemSourceService();
        pdcAttributeGateway = components.pdcAttributeGateway();
        recipeResolver = components.recipeResolver();
        chanceCalculator = components.chanceCalculator();
        economyService = components.economyService();
        snapshotBuilder = components.snapshotBuilder();
        actionCoordinator = components.actionCoordinator();
        attemptService = components.attemptService();
        refreshService = components.refreshService();
        strengthenGuiService = components.strengthenGuiService();
        setDebugLogger(new DebugLogger(getLogger(), languageLoader));
        debugCommand = new DebugCommand(debugLogger(), DEBUG_MODULES);
        registerServices(components);
    }

    private void registerApi() {
        getServer().getServicesManager().register(EmakiStrengthenApi.class, attemptService, this, ServicePriority.Normal);
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
        getServer().getPluginManager().registerEvents(itemRefreshListener, this);
    }

    private void registerWebConsole() {
        WebConsoleRegistry.registerModule(this, "Strengthen 强化", "星级、广播、成功率", "strengthen", WEB_ICON);
        WebConsoleRegistry.registerConfigFile(this, "强化系统配置", "config.yml", "强化系统主配置，包含成功率、材料、经济和显示策略。 ");
        WebConsoleRegistry.registerGuiFile(this, "强化 GUI 模板", "gui/**/*.yml", "强化界面 GUI 模板文件。", "emakistrengthen:gui");
        WebConsoleRegistry.registerWebExtension(this, "emakistrengthen:gui-surface", "web-extensions/emakistrengthen-gui-surface.js");
        WebConsoleRegistry.registerGuiEditorDescriptor(this, "emakistrengthen:gui", editorDescriptor("emakistrengthen:gui", "强化 GUI", "强化 GUI 模板"));
        registerGuiEditorFields("emakistrengthen:gui");
        WebConsoleRegistry.registerCommonConfigComments(this);

        // 顶层字段
        WebConsoleRegistry.registerNodeComment(this, "local_broadcast_radius", "广播半径", "本地广播的可见半径（格数）。", "number");

        // broadcast
        WebConsoleRegistry.registerNodeComment(this, "broadcast", "广播", "强化成功时的本地和全服广播设置。", "object");
        WebConsoleRegistry.registerNodeComment(this, "broadcast.local_stars", "本地广播星级", "触发本地广播的星级列表。", "list");
        WebConsoleRegistry.registerNodeComment(this, "broadcast.global_stars", "全服广播星级", "触发全服广播的星级列表。", "list");

        // success_rates
        WebConsoleRegistry.registerNodeComment(this, "success_rates", "成功率", "全局成功率配置，key 为星级，value 为成功率百分比。", "object");
    }

    private void registerGuiEditorFields(String editorId) {
        guiField(editorId, "id", "ID", "GUI 模板唯一标识。", "text");
        guiField(editorId, "gui_type", "GUI 类型", "Bukkit InventoryType。只有 CHEST 支持行数。", "enum");
        guiField(editorId, "title", "标题", "GUI 窗口标题，支持 MiniMessage。", "text");
        guiField(editorId, "rows", "箱子行数", "仅 CHEST 类型可用，范围 1-6。", "number");
        guiField(editorId, "type", "槽位类型", "强化业务槽位语义。", "text");
        guiField(editorId, "slots", "槽位", "槽位索引列表。", "list");
        guiField(editorId, "item", "物品", "槽位显示物品。", "text");
        guiField(editorId, "display_name", "显示名", "槽位物品显示名称。", "text");
        guiField(editorId, "lore", "Lore", "槽位物品描述。", "stringList");
        guiField(editorId, "target_item", "目标物品", "放入待强化物品的槽位。", "text");
        guiField(editorId, "material", "强化材料", "放入强化材料的槽位。", "text");
        guiField(editorId, "confirm", "确认强化", "执行强化操作按钮。", "text");
    }

    private void guiField(String editorId, String path, String label, String comment, String type) {
        WebConsoleRegistry.registerGuiEditorField(this, editorId, path, label, comment, type);
    }

    private Map<String, Object> editorDescriptor(String id, String title, String kindLabel) {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("id", id);
        descriptor.put("title", title);
        descriptor.put("kindLabel", kindLabel);
        return descriptor;
    }

    private void ensurePlaceholderExpansion() {
        if (placeholderExpansion != null) {
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        placeholderExpansion = new StrengthenPlaceholderExpansion(this, attemptService);
        placeholderExpansion.register();
        messageService.info("console.papi_registered");
    }

    public YamlConfigLoader<AppConfig> appConfigLoader() {
        return appConfigLoader;
    }

    public LanguageLoader languageLoader() {
        return languageLoader;
    }

    public StrengthenRecipeLoader recipeLoader() {
        return recipeLoader;
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

    public PdcAttributeGateway pdcAttributeGateway() {
        return pdcAttributeGateway;
    }

    public StrengthenRecipeResolver recipeResolver() {
        return recipeResolver;
    }

    public ChanceCalculator chanceCalculator() {
        return chanceCalculator;
    }

    public StrengthenEconomyService economyService() {
        return economyService;
    }

    public StrengthenSnapshotBuilder snapshotBuilder() {
        return snapshotBuilder;
    }

    public StrengthenActionCoordinator actionCoordinator() {
        return actionCoordinator;
    }

    public StrengthenAttemptService attemptService() {
        return attemptService;
    }

    public StrengthenRefreshService refreshService() {
        return refreshService;
    }

    public StrengthenGuiService strengthenGuiService() {
        return strengthenGuiService;
    }

    public GuiItemBuilder.ItemFactory coreItemFactory() {
        return coreItemFactory;
    }

    public ItemSourceService coreItemSourceService() {
        return coreItemSourceService;
    }

    public DebugCommand debugCommand() {
        return debugCommand;
    }
}
