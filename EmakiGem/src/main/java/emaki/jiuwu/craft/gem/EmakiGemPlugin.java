package emaki.jiuwu.craft.gem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.debug.DebugCommand;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
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
import emaki.jiuwu.craft.gem.config.AppConfig;
import emaki.jiuwu.craft.gem.loader.GemItemLoader;
import emaki.jiuwu.craft.gem.loader.GemLoader;
import emaki.jiuwu.craft.gem.loader.GemResonanceLoader;
import emaki.jiuwu.craft.gem.papi.GemPlaceholderExpansion;
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
import emaki.jiuwu.craft.gem.service.GemResonanceService;
import emaki.jiuwu.craft.gem.service.GemUpgradeService;
import emaki.jiuwu.craft.gem.service.SocketOpenerService;

public final class EmakiGemPlugin extends AbstractConfigurableEmakiPlugin<AppConfig> implements LogMessagesProvider, EmakiServiceRegistry {

    private static final String ROOT_COMMAND = "emakigem";
    private static final String WEB_ICON = """
            <svg viewBox="0 0 38 38" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="M9 14l10-8 10 8-10 18L9 14zM13 10h12M9 14h20M14 14l5 18 5-18M19 6v8" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>
            """;

    private static final Set<String> DEBUG_MODULES = Set.of("inlay", "socket", "state", "gui");

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
    private PdcAttributeGateway pdcAttributeGateway;
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
    private GemResonanceLoader resonanceLoader;
    private GemResonanceService resonanceService;
    private GemPlaceholderExpansion placeholderExpansion;
    private DebugCommand debugCommand;

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
        if (guiService != null) {
            getServer().getPluginManager().registerEvents(guiService, this);
        }
    }

    private void registerWebConsole() {
        WebConsoleRegistry.registerModule(getName(), "Gem 宝石", "开孔、镶嵌、升级与 GUI", "gem", WEB_ICON);
        WebConsoleRegistry.registerConfigFile(getName(), "宝石系统配置", "config.yml", "宝石系统主配置，包含开孔器、镶嵌、升级和 GUI 入口设置。");
        WebConsoleRegistry.registerGuiFile(getName(), "宝石 GUI 模板", "gui/**/*.yml", "宝石镶嵌、开孔、升级 GUI 模板文件。");
        WebConsoleRegistry.registerWebExtension(this, "emakigem:item-surface", "web-extensions/emakigem-item-surface.js");
        WebConsoleRegistry.registerEditorDescriptor(getName(), "emakigem:socket-item", socketItemEditorDescriptor());
        WebConsoleRegistry.registerEditorDescriptor(getName(), "emakigem:gem", gemEditorDescriptor());
        WebConsoleRegistry.registerItemFile(getName(), "宝石物品定义", "items/**/*.yml", "宝石插件物品定义文件。", "emakigem:socket-item");
        WebConsoleRegistry.registerItemFile(getName(), "宝石定义", "gems/**/*.yml", "宝石定义文件，包含宝石物品来源、效果、插槽兼容和升级配置。", "emakigem:gem");
        WebConsoleRegistry.registerCommonConfigComments(getName());

        // socket_openers
        WebConsoleRegistry.registerNodeComment(getName(), "socket_openers", "开孔器", "攻击、防御、通用等开孔器的物品与规则配置。", "object");

        // inlay_success
        WebConsoleRegistry.registerNodeComment(getName(), "inlay_success", "镶嵌成功率", "宝石镶嵌成功率、公式与失败处理策略。", "object");
        WebConsoleRegistry.registerNodeComment(getName(), "inlay_success.enabled", "启用成功率", "是否启用镶嵌成功率机制（关闭则必定成功）。", "boolean");
        WebConsoleRegistry.registerNodeComment(getName(), "inlay_success.default_chance", "默认成功率", "未单独配置时的默认镶嵌成功率百分比。", "number");
        WebConsoleRegistry.registerNodeComment(getName(), "inlay_success.rate_formula", "成功率公式", "镶嵌成功率的计算公式表达式。", "text");
        WebConsoleRegistry.registerNodeComment(getName(), "inlay_success.failure_action", "失败处理", "镶嵌失败时的处理方式。", "enum:return_gem,destroy_gem,destroy_both");

        // upgrade
        WebConsoleRegistry.registerNodeComment(getName(), "upgrade", "升级配置", "宝石升级成功率与失败惩罚配置。", "object");
        WebConsoleRegistry.registerNodeComment(getName(), "upgrade.global_success_rates", "升级成功率", "各等级宝石升级的全局成功率配置。", "object");
        WebConsoleRegistry.registerNodeComment(getName(), "upgrade.global_failure_penalty", "失败惩罚", "宝石升级失败时的全局惩罚方式。", "enum:none,downgrade,destroy");

        // number_format
        WebConsoleRegistry.registerNodeComment(getName(), "number_format", "数值格式", "宝石属性数值的格式化配置。", "object");
        WebConsoleRegistry.registerNodeComment(getName(), "number_format.default", "默认格式", "默认数值显示格式（如 #.##）。", "text");

        // permission
        WebConsoleRegistry.registerNodeComment(getName(), "permission.op_bypass", "OP跳过", "OP 是否跳过宝石操作的条件检查。", "boolean");

        // gui
        WebConsoleRegistry.registerNodeComment(getName(), "gui", "GUI", "宝石 GUI 默认模式和关闭保存策略。", "object");
        WebConsoleRegistry.registerNodeComment(getName(), "gui.default_mode", "默认模式", "打开宝石 GUI 时的默认显示模式。", "enum:inlay,open,upgrade");
        WebConsoleRegistry.registerNodeComment(getName(), "gui.save_on_close", "关闭保存", "关闭 GUI 时是否自动保存当前操作。", "boolean");
    }

    private Map<String, Object> gemEditorDescriptor() {
        Map<String, Object> descriptor = editorDescriptor("emakigem:gem", "宝石定义", "宝石定义");
        descriptor.put("baseName", "<gray>预览装备</gray>");
        descriptor.put("baseLore", List.of("<gray>原始装备 Lore</gray>"));
        descriptor.put("sections", List.of(
                section("基础字段", "宝石识别、显示、类型和物品来源。", List.of(
                        field("id", "ID", "text", "宝石唯一标识。", false),
                        field("display_name", "显示名", "text", "支持 MiniMessage。", false),
                        field("gem_type", "宝石类型", "enum", "用于插槽兼容匹配。", false, List.of("attack", "defense", "utility", "universal")),
                        field("level", "基础等级", "number", "未升级时的初始等级。", false),
                        field("item_sources", "物品来源", "stringList", "每行一个 ItemSource，例如 minecraft-redstone。", true),
                        field("custom_model_data", "Custom Model Data", "number", "可选资源包模型数据。", false),
                        field("socket_compatibility", "插槽兼容", "stringList", "每行一个可镶嵌插槽类型。", true)
                )),
                section("效果与展示动作", "effects 是宝石实际写入属性、技能和 Name/Lore 动作的主结构。", List.of(
                        field("effects", "effects", "json", "完整 effects 列表，支持 variables、ea_attribute、es_skill、name_action、lore_action。", true),
                        field("name_actions", "顶层 Name Actions", "actions", "兼容旧结构；推荐使用 effects[type=name_action]。", true),
                        field("lore_actions", "顶层 Lore Actions", "actions", "兼容旧结构；推荐使用 effects[type=lore_action]。", true)
                )),
                section("费用与返还", "镶嵌、拆卸和返还规则。", List.of(
                        field("inlay_cost", "镶嵌费用", "json", "货币与材料消耗。", true),
                        field("extract_cost", "拆卸费用", "json", "货币与材料消耗。", true),
                        field("extract_return", "拆卸返还", "json", "original、destroy 或 downgrade。", true)
                )),
                section("升级配置", "各等级成功率、材料、经济、效果与动作。", List.of(
                        field("upgrade", "upgrade", "json", "完整升级配置，包含 levels 下每级覆盖。", true),
                        field("actions", "成功动作", "json", "镶嵌/拆卸成功后执行的 Action 分组。", true)
                ))
        ));
        return descriptor;
    }

    private Map<String, Object> socketItemEditorDescriptor() {
        Map<String, Object> descriptor = editorDescriptor("emakigem:socket-item", "宝石插槽物品", "宝石物品定义");
        descriptor.put("baseName", "<gray>预览装备</gray>");
        descriptor.put("baseLore", List.of("<gray>原始装备 Lore</gray>"));
        descriptor.put("sections", List.of(
                section("匹配与限制", "定义哪些装备会被识别为可镶嵌物品。", List.of(
                        field("id", "ID", "text", "物品定义唯一标识。", false),
                        field("match.item_sources", "匹配物品来源", "stringList", "每行一个 ItemSource。", true),
                        field("allowed_gem_types", "允许宝石类型", "stringList", "每行一个宝石类型。", true),
                        field("max_same_type", "同类型上限", "number", "0 表示不限制。", false),
                        field("max_same_id", "同 ID 上限", "number", "同一宝石 ID 可镶嵌数量。", false)
                )),
                section("插槽结构", "定义插槽索引、类型、显示名和默认开放状态。", List.of(
                        field("slots", "slots", "json", "插槽数组，每项包含 index、type、display_name。", true),
                        field("default_open_slots", "默认开放插槽", "json", "默认开放的 slot index 列表。", true)
                )),
                section("GUI 与展示动作", "打开模板和插槽激活后的 Name/Lore 修改。", List.of(
                        field("gui", "GUI", "json", "gem/open/upgrade 模板关联配置。", true),
                        field("name_actions", "Name Actions", "actions", "插槽激活后对装备名的修改。", true),
                        field("lore_actions", "Lore Actions", "actions", "插槽激活后对装备 Lore 的修改。", true)
                ))
        ));
        return descriptor;
    }

    private Map<String, Object> editorDescriptor(String id, String title, String kindLabel) {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("id", id);
        descriptor.put("title", title);
        descriptor.put("kindLabel", kindLabel);
        return descriptor;
    }

    private Map<String, Object> section(String title, String comment, List<Map<String, Object>> fields) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("title", title);
        section.put("comment", comment);
        section.put("fields", fields);
        return section;
    }

    private Map<String, Object> field(String path, String label, String type, String comment, boolean wide) {
        return field(path, label, type, comment, wide, List.of());
    }

    private Map<String, Object> field(String path, String label, String type, String comment, boolean wide, List<String> options) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("path", path);
        field.put("label", label);
        field.put("type", type);
        field.put("comment", comment);
        if (wide) {
            field.put("wide", true);
        }
        if (options != null && !options.isEmpty()) {
            field.put("options", options);
        }
        return field;
    }

    public void ensurePlaceholderExpansion() {
        if (placeholderExpansion != null) {
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        placeholderExpansion = new GemPlaceholderExpansion(this, stateService, gemItemLoader);
        placeholderExpansion.register();
        messageService.info("console.papi_registered");
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

    public PdcAttributeGateway pdcAttributeGateway() {
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

    public GemResonanceLoader resonanceLoader() {
        return resonanceLoader;
    }

    public GemResonanceService resonanceService() {
        return resonanceService;
    }

    public void setResonanceLoader(GemResonanceLoader resonanceLoader) {
        this.resonanceLoader = resonanceLoader;
    }

    public void setResonanceService(GemResonanceService resonanceService) {
        this.resonanceService = resonanceService;
    }

    public DebugCommand debugCommand() {
        return debugCommand;
    }
}
