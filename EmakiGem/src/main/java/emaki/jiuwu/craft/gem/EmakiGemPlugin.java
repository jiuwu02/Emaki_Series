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
        WebConsoleRegistry.registerModule(this, "Gem 宝石", "开孔、镶嵌、升级与 GUI", "gem", WEB_ICON);
        WebConsoleRegistry.registerConfigFile(this, "宝石系统配置", "config.yml", "宝石系统主配置，包含开孔器、镶嵌、升级和 GUI 入口设置。");
        WebConsoleRegistry.registerGuiFile(this, "宝石 GUI 模板", "gui/**/*.yml", "宝石镶嵌、开孔、升级 GUI 模板文件。", "emakigem:gui");
        WebConsoleRegistry.registerWebExtension(this, "emakigem:item-surface", "web-extensions/emakigem-item-surface.js");
        WebConsoleRegistry.registerGuiEditorDescriptor(this, "emakigem:gui", editorDescriptor("emakigem:gui", "宝石 GUI", "宝石 GUI 模板"));
        registerGuiEditorFields("emakigem:gui");
        WebConsoleRegistry.registerEditorDescriptor(this, "emakigem:socket-item", socketItemEditorDescriptor());
        WebConsoleRegistry.registerEditorDescriptor(this, "emakigem:gem", gemEditorDescriptor());
        registerGemEditorFields();
        registerSocketItemEditorFields();
        WebConsoleRegistry.registerItemFile(this, "宝石物品定义", "items/**/*.yml", "宝石插件物品定义文件。", "emakigem:socket-item");
        WebConsoleRegistry.registerItemFile(this, "宝石定义", "gems/**/*.yml", "宝石定义文件，包含宝石物品来源、效果、插槽兼容和升级配置。", "emakigem:gem");
        WebConsoleRegistry.registerCommonConfigComments(this);

        // socket_openers
        WebConsoleRegistry.registerNodeComment(this, "socket_openers", "开孔器", "攻击、防御、通用等开孔器的物品与规则配置。", "object");

        // inlay_success
        WebConsoleRegistry.registerNodeComment(this, "inlay_success", "镶嵌成功率", "宝石镶嵌成功率、公式与失败处理策略。", "object");
        WebConsoleRegistry.registerNodeComment(this, "inlay_success.enabled", "启用成功率", "是否启用镶嵌成功率机制（关闭则必定成功）。", "boolean");
        WebConsoleRegistry.registerNodeComment(this, "inlay_success.default_chance", "默认成功率", "未单独配置时的默认镶嵌成功率百分比。", "number");
        WebConsoleRegistry.registerNodeComment(this, "inlay_success.rate_formula", "成功率公式", "镶嵌成功率的计算公式表达式。", "text");
        WebConsoleRegistry.registerNodeComment(this, "inlay_success.failure_action", "失败处理", "镶嵌失败时的处理方式。", "enum:return_gem,destroy_gem,destroy_both");

        // upgrade
        WebConsoleRegistry.registerNodeComment(this, "upgrade", "升级配置", "宝石升级成功率与失败惩罚配置。", "object");
        WebConsoleRegistry.registerNodeComment(this, "upgrade.global_success_rates", "升级成功率", "各等级宝石升级的全局成功率配置。", "object");
        WebConsoleRegistry.registerNodeComment(this, "upgrade.global_failure_penalty", "失败惩罚", "宝石升级失败时的全局惩罚方式。", "enum:none,downgrade,destroy");

        // number_format
        WebConsoleRegistry.registerNodeComment(this, "number_format", "数值格式", "宝石属性数值的格式化配置。", "object");
        WebConsoleRegistry.registerNodeComment(this, "number_format.default", "默认格式", "默认数值显示格式（如 #.##）。", "text");

        // permission
        WebConsoleRegistry.registerNodeComment(this, "permission.op_bypass", "OP跳过", "OP 是否跳过宝石操作的条件检查。", "boolean");

        // gui
        WebConsoleRegistry.registerNodeComment(this, "gui", "GUI", "宝石 GUI 默认模式和关闭保存策略。", "object");
        WebConsoleRegistry.registerNodeComment(this, "gui.default_mode", "默认模式", "打开宝石 GUI 时的默认显示模式。", "enum:inlay,open,upgrade");
        WebConsoleRegistry.registerNodeComment(this, "gui.save_on_close", "关闭保存", "关闭 GUI 时是否自动保存当前操作。", "boolean");
    }

    private void registerGuiEditorFields(String editorId) {
        editorField(editorId, "id", "ID", "GUI 模板唯一标识。", "text");
        editorField(editorId, "gui_type", "GUI 类型", "Bukkit InventoryType。只有 CHEST 支持行数。", "enum");
        editorField(editorId, "title", "标题", "GUI 窗口标题，支持 MiniMessage。", "text");
        editorField(editorId, "rows", "箱子行数", "仅 CHEST 类型可用，范围 1-6。", "number");
        editorField(editorId, "slots", "槽位定义", "GUI 中所有可渲染槽位配置。", "object");
        editorField(editorId, "type", "槽位类型", "插件业务识别的槽位语义，例如 target_item、confirm。", "text");
        editorField(editorId, "item", "物品", "槽位显示物品，支持原版材料或 ItemSource。", "text");
        editorField(editorId, "display_name", "显示名", "槽位物品显示名称，支持 MiniMessage。", "text");
        editorField(editorId, "lore", "Lore", "槽位物品描述，每行一条。", "stringList");
        editorField(editorId, "hidden_components", "隐藏组件", "隐藏 tooltip、附魔、属性等原版组件。", "stringList");
        editorField(editorId, "item_model", "物品模型", "资源包 item model 标识。", "text");
        editorField(editorId, "custom_model_data", "模型数据", "Custom Model Data 数值。", "number");
        editorField(editorId, "sounds", "声音", "点击槽位时播放的声音配置。", "object");
        editorField(editorId, "target_item", "目标装备槽", "放入待镶嵌或查看的装备。", "text");
        editorField(editorId, "socket_slot", "宝石槽位", "展示或操作装备上的宝石槽。", "text");
        editorField(editorId, "confirm", "确认按钮", "确认当前宝石操作。", "text");
    }

    private void registerGemEditorFields() {
        String editorId = "emakigem:gem";
        editorField(editorId, "id", "ID", "宝石唯一标识。", "text");
        editorField(editorId, "display_name", "显示名", "宝石在物品与 GUI 中显示的名称，支持 MiniMessage。", "text");
        editorField(editorId, "lore", "描述 Lore", "宝石自身说明，每行一条。", "stringList");
        editorField(editorId, "gem_type", "宝石类型", "用于插槽兼容匹配。", "enum");
        editorField(editorId, "level", "基础等级", "宝石初始等级。", "number");
        editorField(editorId, "item_sources", "物品来源", "识别这颗宝石的 ItemSource 列表。", "stringList");
        editorField(editorId, "custom_model_data", "模型数据", "资源包使用的 Custom Model Data。", "number");
        editorField(editorId, "socket_compatibility", "兼容插槽", "允许镶嵌到的插槽类型。", "stringList");
        editorField(editorId, "effects", "宝石效果", "实际写入属性、技能和名称/Lore 动作的效果列表。", "list");
        editorField(editorId, "type", "效果类型", "当前效果条目的类型。", "enum");
        editorField(editorId, "variables", "变量", "表达式引擎与 Lore 占位符使用的变量。", "object");
        editorField(editorId, "ea_attributes", "属性", "写入 EmakiAttribute 的属性数值。", "object");
        editorField(editorId, "es_skills", "技能", "附加的 EmakiSkills 技能 ID。", "stringList");
        editorField(editorId, "name_actions", "名称动作", "镶嵌后对装备名称执行的动作。", "actions");
        editorField(editorId, "lore_actions", "Lore 动作", "镶嵌后对装备 Lore 执行的动作。", "actions");
        editorField(editorId, "inlay_cost", "镶嵌费用", "镶嵌宝石时消耗的货币与材料。", "object");
        editorField(editorId, "extract_cost", "拆卸费用", "拆卸宝石时消耗的货币与材料。", "object");
        editorField(editorId, "extract_return", "拆卸返还", "拆卸后宝石原样返还、销毁或降级返还。", "object");
        editorField(editorId, "mode", "返还模式", "拆卸后宝石处理方式。", "enum");
        editorField(editorId, "downgrade_levels", "降级等级", "降级返还时降低的等级数。", "number");
        editorField(editorId, "degraded_chance", "降级概率", "拆卸返还降级概率。", "number");
        editorField(editorId, "upgrade", "升级配置", "宝石升级开关、费用、成功率和等级覆盖。", "object");
        editorField(editorId, "enabled", "启用", "是否启用此配置。", "boolean");
        editorField(editorId, "max_level", "最高等级", "宝石可升级到的最高等级。", "number");
        editorField(editorId, "gui_template", "GUI 模板", "升级 GUI 模板路径。", "text");
        editorField(editorId, "failure_penalty", "失败惩罚", "升级失败后的惩罚方式。", "enum");
        editorField(editorId, "economy", "经济消耗", "升级使用的货币和材料消耗。", "object");
        editorField(editorId, "success_rates", "成功率", "各目标等级的升级成功率。", "object");
        editorField(editorId, "levels", "等级配置", "每个等级的显示、效果、材料和动作覆盖。", "object");
        editorField(editorId, "success_rate", "成功率", "升级到该等级的成功率。", "number");
        editorField(editorId, "materials", "材料消耗", "升级或操作所需材料。", "list");
        editorField(editorId, "currencies", "货币消耗", "Vault 或其他经济提供者的货币消耗。", "list");
        editorField(editorId, "provider", "经济提供者", "货币提供者，例如 vault。", "text");
        editorField(editorId, "currency_id", "货币 ID", "多货币系统中的货币标识。", "text");
        editorField(editorId, "amount", "数量", "材料数量或货币数量。", "number");
        editorField(editorId, "base_cost", "基础费用", "费用公式中使用的基础值。", "number");
        editorField(editorId, "cost_formula", "费用公式", "根据等级等变量计算最终费用。", "text");
        editorField(editorId, "actions", "动作", "操作成功或失败时执行的 Action 列表。", "object");
        editorField(editorId, "inlay_success", "镶嵌成功动作", "宝石镶嵌成功后执行的动作。", "list");
        editorField(editorId, "extract_success", "拆卸成功动作", "宝石拆卸成功后执行的动作。", "list");
        editorField(editorId, "success", "成功动作", "升级成功时执行的动作。", "list");
        editorField(editorId, "failure", "失败动作", "升级失败时执行的动作。", "list");
        editorField(editorId, "action", "动作类型", "名称或 Lore 操作类型。", "enum");
        editorField(editorId, "value", "文本值", "动作使用的文本值。", "text");
        editorField(editorId, "content", "内容", "Lore 动作追加、插入或替换的内容。", "stringList");
        editorField(editorId, "target_pattern", "目标匹配", "Lore 动作查找目标行的匹配文本。", "text");
        editorField(editorId, "anchor", "锚点", "插入动作使用的锚点。", "text");
    }

    private void registerSocketItemEditorFields() {
        String editorId = "emakigem:socket-item";
        editorField(editorId, "id", "ID", "插槽物品定义唯一标识。", "text");
        editorField(editorId, "match", "匹配规则", "决定哪些物品会拥有宝石插槽。", "object");
        editorField(editorId, "match.item_sources", "匹配物品来源", "按 ItemSource 匹配装备。", "stringList");
        editorField(editorId, "match.slot_groups", "装备分组", "按 weapon、armor、offhand 等槽位组匹配。", "stringList");
        editorField(editorId, "match.lore_contains", "Lore 包含", "仅匹配 Lore 包含指定文本的物品。", "stringList");
        editorField(editorId, "slots", "插槽列表", "该物品拥有的宝石插槽。", "list");
        editorField(editorId, "index", "插槽索引", "从 0 开始的唯一插槽编号。", "number");
        editorField(editorId, "type", "插槽类型", "决定哪些宝石可以镶嵌。", "text");
        editorField(editorId, "display_name", "显示名", "插槽在 GUI 中显示的名称。", "text");
        editorField(editorId, "default_open_slots", "默认开放插槽", "物品初始已开放的插槽索引。", "list");
        editorField(editorId, "allowed_gem_types", "允许宝石类型", "该物品允许镶嵌的宝石类型白名单。", "stringList");
        editorField(editorId, "max_same_type", "同类型上限", "同类型宝石最大数量，0 表示不限制。", "number");
        editorField(editorId, "max_same_id", "同 ID 上限", "同一宝石 ID 可镶嵌数量。", "number");
        editorField(editorId, "gui", "GUI 模板", "宝石镶嵌和开槽界面模板。", "object");
        editorField(editorId, "gui.gem_template", "镶嵌模板", "宝石镶嵌/查看界面模板。", "text");
        editorField(editorId, "gui.open_template", "开槽模板", "开槽界面模板。", "text");
        editorField(editorId, "name_actions", "名称动作", "插槽激活后对物品名称执行的动作。", "actions");
        editorField(editorId, "lore_actions", "Lore 动作", "插槽激活后对物品 Lore 执行的动作。", "actions");
        editorField(editorId, "action", "动作类型", "名称或 Lore 操作类型。", "enum");
        editorField(editorId, "value", "文本值", "动作使用的文本值。", "text");
        editorField(editorId, "content", "内容", "Lore 动作追加、插入或替换的内容。", "stringList");
        editorField(editorId, "target_pattern", "目标匹配", "Lore 动作查找目标行的匹配文本。", "text");
        editorField(editorId, "anchor", "锚点", "插入动作使用的锚点。", "text");
    }

    private void editorField(String editorId, String path, String label, String comment, String type) {
        WebConsoleRegistry.registerEditorField(this, editorId, path, label, comment, type);
    }

    private Map<String, Object> gemEditorDescriptor() {
        Map<String, Object> descriptor = editorDescriptor("emakigem:gem", "宝石定义", "宝石定义");
        descriptor.put("baseName", "<gray>预览装备</gray>");
        descriptor.put("baseLore", List.of("<gray>原始装备 Lore</gray>"));
        return descriptor;
    }

    private Map<String, Object> socketItemEditorDescriptor() {
        Map<String, Object> descriptor = editorDescriptor("emakigem:socket-item", "宝石插槽物品", "宝石物品定义");
        descriptor.put("baseName", "<gray>预览装备</gray>");
        descriptor.put("baseLore", List.of("<gray>原始装备 Lore</gray>"));
        return descriptor;
    }

    private Map<String, Object> editorDescriptor(String id, String title, String kindLabel) {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("id", id);
        descriptor.put("title", title);
        descriptor.put("kindLabel", kindLabel);
        return descriptor;
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
