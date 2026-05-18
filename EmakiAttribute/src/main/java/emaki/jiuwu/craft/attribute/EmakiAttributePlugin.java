package emaki.jiuwu.craft.attribute;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import emaki.jiuwu.craft.attribute.action.AttributeActions;
import emaki.jiuwu.craft.attribute.action.AttributeDamageSkillAction;
import emaki.jiuwu.craft.attribute.api.PdcAttributeApi;
import emaki.jiuwu.craft.attribute.bridge.MmoItemsBridge;
import emaki.jiuwu.craft.attribute.bridge.MythicBridge;
import emaki.jiuwu.craft.attribute.command.AttributeCommand;
import emaki.jiuwu.craft.attribute.config.AttributeConfig;
import emaki.jiuwu.craft.attribute.listener.AttributeListener;
import emaki.jiuwu.craft.attribute.loader.AttributeBalanceRegistry;
import emaki.jiuwu.craft.attribute.loader.AttributePresetRegistry;
import emaki.jiuwu.craft.attribute.loader.AttributeRegistry;
import emaki.jiuwu.craft.attribute.loader.DamageTypeRegistry;
import emaki.jiuwu.craft.attribute.loader.DefaultProfileRegistry;
import emaki.jiuwu.craft.attribute.loader.LanguageLoader;
import emaki.jiuwu.craft.attribute.loader.LoreFormatRegistry;
import emaki.jiuwu.craft.attribute.loader.PdcReadRuleLoader;
import emaki.jiuwu.craft.attribute.papi.AttributePlaceholderExpansion;
import emaki.jiuwu.craft.attribute.service.AttributeService;
import emaki.jiuwu.craft.attribute.service.MessageService;
import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.api.integration.EmakiAttributeBridge;
import emaki.jiuwu.craft.corelib.debug.DebugCommand;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.plugin.AbstractEmakiPlugin;
import emaki.jiuwu.craft.corelib.service.EmakiServiceRegistry;
import emaki.jiuwu.craft.corelib.text.AdventureSupport;
import emaki.jiuwu.craft.corelib.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.web.WebConsoleRegistry;

public final class EmakiAttributePlugin extends AbstractEmakiPlugin implements EmakiServiceRegistry {

    private static final String WEB_ICON = """
            <svg viewBox="0 0 38 38" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="M19 5l10 6v16l-10 6-10-6V11l10-6zM19 10v18M11.5 15.5l15 9M26.5 15.5l-15 9M14 20h10M19 14l4 6-4 4-4-4 4-6z" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>
            """;

    private static final String STARTUP_ASCII = """
  ______  __    __  ______  __  __   __  ______  ______  ______  ______  __  ______  __  __  ______  ______
 /\\  ___\\/\\ "-./  \\/\\  __ \\/\\ \\/ /  /\\ \\/\\  __ \\/\\__  _\\/\\__  _\\/\\  == \\/\\ \\/\\  == \\/\\ \\/\\ \\/\\__  _\\/\\  ___\\   
 \\ \\  __\\\\ \\ \\-./\\ \\ \\  __ \\ \\  _"-.\\ \\ \\ \\  __ \\/_/\\ \\/\\/_/\\ \\/\\ \\  __<\\ \\ \\ \\  __<\\ \\ \\_\\ \\/_/\\ \\/\\ \\  __\\   
  \\ \\_____\\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_\\ \\_\\\\ \\_\\ \\_\\ \\_\\ \\ \\_\\   \\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_____\\ \\_____\\ \\ \\_\\ \\ \\_____\\ 
   \\/_____/\\/_/  \\/_/\\/_/\\/_/\\/_/\\/_/ \\/_/\\/_/\\/_/  \\/_/    \\/_/  \\/_/ /_/\\/_/\\/_____/\\/_____/  \\/_/  \\/_____/
                                                                                                                
""";

    private final AttributeLifecycleCoordinator lifecycleCoordinator = new AttributeLifecycleCoordinator();

    private static final Set<String> DEBUG_MODULES = Set.of("combat", "resync", "snapshot", "resource");

    private DebugCommand debugCommand;

    private AttributeConfig configModel = AttributeConfig.defaults();
    private AttributeRegistry attributeRegistry;
    private AttributeBalanceRegistry attributeBalanceRegistry;
    private DamageTypeRegistry damageTypeRegistry;
    private DefaultProfileRegistry defaultProfileRegistry;
    private LoreFormatRegistry loreFormatRegistry;
    private AttributePresetRegistry presetRegistry;
    private PdcReadRuleLoader pdcReadRuleLoader;
    private LanguageLoader languageLoader;
    private MessageService messageService;
    private EmakiAttributeBridge emakiAttributeBridge;
    private PdcAttributeApi pdcAttributeApi;
    private AttributeService attributeService;
    private AttributeListener listener;
    private AttributeCommand command;
    private MythicBridge mythicBridge;
    private MmoItemsBridge mmoItemsBridge;
    private AttributePlaceholderExpansion placeholderExpansion;
    private BukkitTask regenTask;
    private CompletableFuture<Void> reloadFuture;

    @Override
    public void onEnable() {
        applyRuntimeComponents(lifecycleCoordinator.initialize(this));
        registerAttributeBridgeService();
        registerPdcAttributeApi();
        ConsoleOutputs.sendGradientAscii(this, STARTUP_ASCII);
        reloadPluginState(true);
        ensureMmoItemsBridge();
        lifecycleCoordinator.registerCommand(this);
        lifecycleCoordinator.registerListener(this);
        ensurePlaceholderExpansion();
        registerSkillScriptActions();
        registerWebConsole();
    }

    @Override
    public void onDisable() {
        unregisterCoreLibActions();
        WebConsoleRegistry.unregisterModule(this);
        Bukkit.getServicesManager().unregisterAll(this);
        lifecycleCoordinator.shutdown(this, regenTask);
        AdventureSupport.close(this);
        regenTask = null;
    }

    public void ensureMythicBridge() {
        if (mythicBridge != null) {
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            return;
        }
        mythicBridge = new MythicBridge(this, attributeService);
        getServer().getPluginManager().registerEvents(mythicBridge, this);
    }

    public void ensureMmoItemsBridge() {
        if (mmoItemsBridge != null || attributeService == null) {
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
            return;
        }
        mmoItemsBridge = new MmoItemsBridge(this, attributeService);
        getServer().getPluginManager().registerEvents(mmoItemsBridge, this);
        attributeService.resyncAllPlayers();
    }

    public void ensurePlaceholderExpansion() {
        if (placeholderExpansion != null) {
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        placeholderExpansion = new AttributePlaceholderExpansion(this, attributeService);
        placeholderExpansion.register();
        messageService.info("console.papi_registered");
    }

    public void reloadPluginState(boolean resyncPlayers) {
        regenTask = lifecycleCoordinator.reload(this, regenTask, resyncPlayers);
        registerCoreLibActions();
    }

    public synchronized CompletableFuture<Void> reloadPluginStateAsync(boolean resyncPlayers, Consumer<String> progressListener) {
        if (reloadFuture != null && !reloadFuture.isDone()) {
            if (progressListener != null) {
                progressListener.accept(messageService.message("command.reload.in_progress"));
            }
            return reloadFuture;
        }
        reloadFuture = lifecycleCoordinator.reloadAsync(this, regenTask, resyncPlayers, progressListener)
                .thenAccept(task -> {
                    regenTask = task;
                    registerCoreLibActions();
                })
                .whenComplete((_, throwable) -> {
                    synchronized (this) {
                        reloadFuture = null;
                    }
                });
        return reloadFuture;
    }

    private void applyRuntimeComponents(AttributeRuntimeComponents components) {
        attributeRegistry = components.attributeRegistry();
        attributeBalanceRegistry = components.attributeBalanceRegistry();
        damageTypeRegistry = components.damageTypeRegistry();
        defaultProfileRegistry = components.defaultProfileRegistry();
        loreFormatRegistry = components.loreFormatRegistry();
        presetRegistry = components.presetRegistry();
        pdcReadRuleLoader = components.pdcReadRuleLoader();
        languageLoader = components.languageLoader();
        messageService = components.messageService();
        emakiAttributeBridge = components.emakiAttributeBridge();
        pdcAttributeApi = components.pdcAttributeApi();
        attributeService = components.attributeService();
        listener = components.listener();
        command = components.command();
        mythicBridge = components.mythicBridge();
        initDebugLogger();
        registerServices(components);
    }

    private void initDebugLogger() {
        emaki.jiuwu.craft.corelib.loader.LanguageLoader coreLanguageLoader =
                new emaki.jiuwu.craft.corelib.loader.LanguageLoader(this);
        coreLanguageLoader.load();
        setDebugLogger(new DebugLogger(getLogger(), coreLanguageLoader));
        debugCommand = new DebugCommand(debugLogger(), DEBUG_MODULES);
    }

    private void registerPdcAttributeApi() {
        if (pdcAttributeApi == null) {
            return;
        }
        Bukkit.getServicesManager().unregister(PdcAttributeApi.class, pdcAttributeApi);
        Bukkit.getServicesManager().register(PdcAttributeApi.class, pdcAttributeApi, this, ServicePriority.Normal);
        emaki.jiuwu.craft.corelib.api.integration.PdcAttributeApi coreApi =
                (emaki.jiuwu.craft.corelib.api.integration.PdcAttributeApi) pdcAttributeApi;
        Bukkit.getServicesManager().unregister(emaki.jiuwu.craft.corelib.api.integration.PdcAttributeApi.class, coreApi);
        Bukkit.getServicesManager().register(emaki.jiuwu.craft.corelib.api.integration.PdcAttributeApi.class, coreApi, this, ServicePriority.Normal);
    }

    private void registerAttributeBridgeService() {
        if (emakiAttributeBridge == null) {
            return;
        }
        Bukkit.getServicesManager().unregister(EmakiAttributeBridge.class, emakiAttributeBridge);
        Bukkit.getServicesManager().register(EmakiAttributeBridge.class, emakiAttributeBridge, this, ServicePriority.Normal);
    }

    void setConfigModel(AttributeConfig configModel) {
        this.configModel = configModel == null ? AttributeConfig.defaults() : configModel;
    }

    void setPlaceholderExpansion(AttributePlaceholderExpansion placeholderExpansion) {
        this.placeholderExpansion = placeholderExpansion;
    }

    public AttributeConfig configModel() {
        return configModel;
    }

    public AttributeRegistry attributeRegistry() {
        return attributeRegistry;
    }

    public AttributeBalanceRegistry attributeBalanceRegistry() {
        return attributeBalanceRegistry;
    }

    public DamageTypeRegistry damageTypeRegistry() {
        return damageTypeRegistry;
    }

    public DefaultProfileRegistry defaultProfileRegistry() {
        return defaultProfileRegistry;
    }

    public LoreFormatRegistry loreFormatRegistry() {
        return loreFormatRegistry;
    }

    public AttributePresetRegistry presetRegistry() {
        return presetRegistry;
    }

    public PdcReadRuleLoader pdcReadRuleLoader() {
        return pdcReadRuleLoader;
    }

    public LanguageLoader languageLoader() {
        return languageLoader;
    }

    public MessageService messageService() {
        return messageService;
    }

    public PdcAttributeApi pdcAttributeApi() {
        return pdcAttributeApi;
    }

    public AttributeService attributeService() {
        return attributeService;
    }

    public AttributeListener listener() {
        return listener;
    }

    public AttributeCommand command() {
        return command;
    }

    public MythicBridge mythicBridge() {
        return mythicBridge;
    }

    public MmoItemsBridge mmoItemsBridge() {
        return mmoItemsBridge;
    }

    public AttributePlaceholderExpansion placeholderExpansion() {
        return placeholderExpansion;
    }

    public DebugCommand debugCommand() {
        return debugCommand;
    }

    /**
     * Returns the configured scaling curves for attribute diminishing returns.
     * Loaded from the {@code scaling_curves} section of the attribute config.
     */
    public java.util.List<emaki.jiuwu.craft.attribute.service.ScalingCurveConfig> scalingCurves() {
        return scalingCurves;
    }

    private volatile java.util.List<emaki.jiuwu.craft.attribute.service.ScalingCurveConfig> scalingCurves = java.util.List.of();

    public void loadScalingCurves(emaki.jiuwu.craft.corelib.yaml.YamlSection section) {
        if (section == null) {
            this.scalingCurves = java.util.List.of();
            return;
        }
        java.util.List<emaki.jiuwu.craft.attribute.service.ScalingCurveConfig> curves = new java.util.ArrayList<>();
        for (String key : section.getKeys(false)) {
            emaki.jiuwu.craft.corelib.yaml.YamlSection curveSection = section.getSection(key);
            if (curveSection == null) {
                continue;
            }
            String attributeId = curveSection.getString("attribute", key);
            double threshold = curveSection.getDouble("threshold", 0D);
            String curveType = curveSection.getString("curve_type", "logarithmic");
            double factor = curveSection.getDouble("factor", 1D);
            curves.add(new emaki.jiuwu.craft.attribute.service.ScalingCurveConfig(
                    attributeId, threshold, curveType, factor));
        }
        this.scalingCurves = java.util.List.copyOf(curves);
    }

    private void registerCoreLibActions() {
        EmakiCoreLibPlugin coreLibPlugin = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
        if (coreLibPlugin.actionRegistry() == null || attributeService == null) {
            return;
        }
        AttributeActions.registerAll(coreLibPlugin.actionRegistry(), attributeService);
    }

    private void unregisterCoreLibActions() {
        EmakiCoreLibPlugin coreLibPlugin = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
        if (coreLibPlugin.actionRegistry() == null) {
            return;
        }
        AttributeActions.unregisterAll(coreLibPlugin.actionRegistry());
    }

    private void registerSkillScriptActions() {
        if (attributeService == null) {
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("EmakiSkills")) {
            return;
        }
        try {
            RegisteredServiceProvider<?> provider = Bukkit.getServicesManager().getRegistration(
                    Class.forName("emaki.jiuwu.craft.skills.api.SkillScriptActionRegistry"));
            if (provider == null || provider.getProvider() == null) {
                return;
            }
            Object registry = provider.getProvider();
            java.lang.reflect.Method registerMethod = registry.getClass().getMethod(
                    "register", org.bukkit.plugin.Plugin.class,
                    Class.forName("emaki.jiuwu.craft.skills.api.SkillScriptAction"));
            registerMethod.invoke(registry, this, new AttributeDamageSkillAction(attributeService));
            messageService.info("console.skill_action_registered");
        } catch (Exception exception) {
            getLogger().warning("Failed to register attribute_damage skill action: " + exception.getMessage());
        }
    }

    private void registerWebConsole() {
        WebConsoleRegistry.registerModule(this, "Attribute 属性", "属性、资源、伤害接管与曲线", "attribute", WEB_ICON);

        // ─── config.yml ───
        WebConsoleRegistry.registerConfigFile(this, "属性系统配置", "config.yml", "属性系统主配置，包含伤害接管、资源恢复和属性曲线。");
        WebConsoleRegistry.registerCommonConfigComments(this);
        WebConsoleRegistry.registerNodeComment(this, "hard_lock_damage", "接管原版伤害", "是否接管未命中白名单的原版伤害，true 时所有伤害进入 EA 结算。", "boolean");
        WebConsoleRegistry.registerNodeComment(this, "default_damage_type", "默认伤害类型", "未指定伤害类型时使用的默认伤害类型 ID。", "dynamic_enum:damage_types");
        WebConsoleRegistry.registerNodeComment(this, "regen_interval_ticks", "回复间隔", "资源（生命、法力等）自然回复的间隔 tick 数。", "number");
        WebConsoleRegistry.registerNodeComment(this, "sync_delay_ticks", "同步延迟", "属性计算完成后同步到 Bukkit 原生属性的延迟 tick 数。", "number");
        WebConsoleRegistry.registerNodeComment(this, "allowed_damage_causes", "伤害白名单", "允许进入 EA 伤害结算的 DamageCause 白名单列表。", "list");
        WebConsoleRegistry.registerNodeComment(this, "default_profile", "默认档案", "玩家默认属性与资源基础值配置。", "object");
        WebConsoleRegistry.registerCreatableNode(this, "default_profile.resources", "资源定义", "默认资源（生命、法力等）的基础配置。", "object");
        WebConsoleRegistry.registerCreatableNode(this, "default_profile.attributes", "属性基础值", "玩家默认属性基础数值配置。", "object");
        WebConsoleRegistry.registerNodeComment(this, "synthetic_hit_feedback", "击中反馈", "接管伤害后的击退和音效补发配置。", "object");
        WebConsoleRegistry.registerNodeComment(this, "synthetic_hit_feedback.knockback", "补发击退", "是否在接管伤害后补发击退效果。", "boolean");
        WebConsoleRegistry.registerNodeComment(this, "synthetic_hit_feedback.knockback_strength", "击退强度", "补发击退的力度系数。", "number");
        WebConsoleRegistry.registerNodeComment(this, "synthetic_hit_feedback.hurt_sound", "受伤音效", "是否在接管伤害后补发受伤音效。", "boolean");
        WebConsoleRegistry.registerCreatableNode(this, "scaling_curves", "衰减曲线", "属性超过阈值后的衰减曲线配置。", "object");

        // config.yml - default_profile.resources 通用 key 匹配
        WebConsoleRegistry.registerNodeKeyComment(this, "display_name", "显示名称", "资源或属性在界面中显示的名称。", "text");
        WebConsoleRegistry.registerNodeKeyComment(this, "default_max", "默认最大值", "资源的默认最大值。", "number");
        WebConsoleRegistry.registerNodeKeyComment(this, "min_max", "最大值下限", "资源最大值允许的最低值。", "number");
        WebConsoleRegistry.registerNodeKeyComment(this, "max_max", "最大值上限", "资源最大值允许的最高值。", "number");
        WebConsoleRegistry.registerNodeKeyComment(this, "sync_to_bukkit", "同步Bukkit", "是否将该资源同步到 Bukkit 原生属性。", "boolean");
        WebConsoleRegistry.registerNodeKeyComment(this, "full_on_init", "初始满值", "初始化时是否将资源填充至最大值。", "boolean");

        // ─── attribute_balance.yml ───
        WebConsoleRegistry.registerConfigFile(this, "属性权重配置", "attribute_balance.yml", "属性语义分组、角色定位与评分权重配置。");
        WebConsoleRegistry.registerCreatableNode(this, "attributes", "属性语义", "每个属性的分组、角色和描述定义。", "object");
        WebConsoleRegistry.registerCreatableNode(this, "scores", "评分权重", "各属性在装备评分中的权重系数。", "object");
        WebConsoleRegistry.registerNodeKeyComment(this, "group", "分组", "属性所属的功能分组（如 physical、spell、utility）。", "text");
        WebConsoleRegistry.registerNodeKeyComment(this, "role", "角色", "属性在战斗中的角色定位（如 offense、defense、sustain）。", "text");
        WebConsoleRegistry.registerNodeKeyComment(this, "summary", "描述", "属性的简短功能描述，用于前端和文档展示。", "text");

        // ─── attributes/**/*.yml ───
        WebConsoleRegistry.registerConfigFile(this, "属性定义", "attributes/**/*.yml", "属性定义文件目录，每个文件定义一个属性的 ID、类型、范围和词条格式。");
        WebConsoleRegistry.registerNodeKeyComment(this, "id", "属性ID", "属性的唯一标识符，全局不可重复。", "text");
        WebConsoleRegistry.registerNodeKeyComment(this, "value_kind", "数值类型", "属性数值类型。", "enum:FLAT,PERCENT,CHANCE,REGEN,RESOURCE");
        WebConsoleRegistry.registerNodeKeyComment(this, "target_type", "目标类型", "属性作用目标类型。", "enum:GENERIC,VANILLA,RESOURCE,DAMAGE");
        WebConsoleRegistry.registerNodeKeyComment(this, "target_id", "目标ID", "当 target_type 为 VANILLA 或 RESOURCE 时，映射到的原版属性或资源 ID。", "text");
        WebConsoleRegistry.registerNodeKeyComment(this, "default_value", "默认值", "属性的默认基础数值。", "number");
        WebConsoleRegistry.registerNodeKeyComment(this, "min_value", "最小值", "属性允许的最小值，低于此值会被钳制。", "number");
        WebConsoleRegistry.registerNodeKeyComment(this, "max_value", "最大值", "属性允许的最大值，超过此值会被钳制。", "number");
        WebConsoleRegistry.registerNodeKeyComment(this, "allow_negative", "允许负值", "是否允许属性值为负数。", "boolean");
        WebConsoleRegistry.registerNodeKeyComment(this, "priority", "优先级", "词条解析优先级，数值越大越先匹配。", "number");
        WebConsoleRegistry.registerNodeKeyComment(this, "lore_format_id", "词条格式", "关联的词条格式 ID，决定属性在物品 Lore 中的显示方式。", "text");
        WebConsoleRegistry.registerNodeKeyComment(this, "lore_patterns", "词条正则", "用于从物品 Lore 中识别该属性数值的正则表达式列表。", "list");
        WebConsoleRegistry.registerNodeKeyComment(this, "attribute_power", "属性战力", "该属性每 1 点对应的战力评分系数。", "number");
        WebConsoleRegistry.registerNodeKeyComment(this, "description", "说明", "属性的详细说明文本，用于文档和调试输出。", "text");

        // ─── damage_types/**/*.yml ───
        WebConsoleRegistry.registerConfigFile(this, "伤害类型定义", "damage_types/**/*.yml", "伤害类型定义文件目录，每个文件定义一种伤害的结算阶段和恢复规则。");
        WebConsoleRegistry.registerNodeKeyComment(this, "aliases", "别名", "伤害类型的别名列表，可通过别名引用该伤害类型。", "list");
        WebConsoleRegistry.registerNodeKeyComment(this, "allowed_events", "允许事件", "允许触发该伤害类型的 Bukkit DamageCause 列表。", "list");
        WebConsoleRegistry.registerNodeKeyComment(this, "hard_lock", "硬锁定", "是否强制接管该伤害类型对应的原版伤害事件。", "boolean");
        WebConsoleRegistry.registerNodeKeyComment(this, "stages", "结算阶段", "伤害结算的有序阶段列表，按顺序依次执行。", "list");
        WebConsoleRegistry.registerNodeKeyComment(this, "recovery", "恢复规则", "伤害造成后的生命恢复（吸血）规则配置。", "object");
        WebConsoleRegistry.registerNodeKeyComment(this, "attacker_message", "攻击者消息", "伤害结算后发送给攻击者的消息模板。", "text");
        WebConsoleRegistry.registerNodeKeyComment(this, "target_message", "受击者消息", "伤害结算后发送给受击者的消息模板。", "text");
        // damage_types stages 子字段
        WebConsoleRegistry.registerNodeSuffixComment(this, "stages.kind", "阶段类型", "结算阶段类型。", "enum:FLAT_PERCENT,CUSTOM");
        WebConsoleRegistry.registerNodeSuffixComment(this, "stages.source", "数据来源", "属性数据来源。", "enum:ATTACKER,TARGET");
        WebConsoleRegistry.registerNodeSuffixComment(this, "stages.mode", "运算模式", "数值运算模式。", "enum:ADD,SUBTRACT,MULTIPLY");
        WebConsoleRegistry.registerNodeSuffixComment(this, "stages.flat_attributes", "固定属性", "参与固定值计算的属性 ID 列表。", "list");
        WebConsoleRegistry.registerNodeSuffixComment(this, "stages.percent_attributes", "百分比属性", "参与百分比计算的属性 ID 列表。", "list");
        WebConsoleRegistry.registerNodeSuffixComment(this, "stages.chance_attributes", "概率属性", "决定该阶段是否触发的概率属性 ID 列表（如暴击率）。", "list");
        WebConsoleRegistry.registerNodeSuffixComment(this, "stages.multiplier_attributes", "倍率属性", "触发后的倍率属性 ID 列表（如暴击伤害）。", "list");
        WebConsoleRegistry.registerNodeSuffixComment(this, "stages.expression", "计算表达式", "自定义伤害计算表达式，支持 {input}、{flat}、{percent}、{crit}、{multiplier} 变量。", "text");
        // damage_types recovery 子字段
        WebConsoleRegistry.registerNodeSuffixComment(this, "recovery.source", "恢复来源", "恢复数值的属性来源。", "enum:ATTACKER,TARGET");
        WebConsoleRegistry.registerNodeSuffixComment(this, "recovery.resistance_source", "抗性来源", "吸血抗性属性的来源。", "enum:ATTACKER,TARGET");
        WebConsoleRegistry.registerNodeSuffixComment(this, "recovery.flat_attributes", "固定恢复", "参与固定恢复计算的属性 ID 列表。", "list");
        WebConsoleRegistry.registerNodeSuffixComment(this, "recovery.percent_attributes", "百分比恢复", "参与百分比恢复计算的属性 ID 列表。", "list");
        WebConsoleRegistry.registerNodeSuffixComment(this, "recovery.resistance_attributes", "抗性属性", "降低恢复效果的抗性属性 ID 列表。", "list");

        // ─── lore_formats/**/*.yml ───
        WebConsoleRegistry.registerConfigFile(this, "词条格式定义", "lore_formats/**/*.yml", "词条格式定义文件目录，每个文件定义一种属性在物品 Lore 中的显示模板。");
        WebConsoleRegistry.registerNodeKeyComment(this, "format", "格式模板", "词条显示格式，支持 {name}、{value}、{sign} 等占位符。", "text");
        WebConsoleRegistry.registerNodeKeyComment(this, "precision", "精度", "数值显示的小数位数。", "number");
        WebConsoleRegistry.registerNodeKeyComment(this, "read_priority", "读取优先级", "从 Lore 解析属性时的匹配优先级，数值越大越先尝试。", "number");
        WebConsoleRegistry.registerNodeKeyComment(this, "read_patterns", "读取正则", "用于从 Lore 文本中提取数值的正则表达式列表。", "list");

        // ─── conditions/**/*.yml ───
        WebConsoleRegistry.registerConfigFile(this, "PDC读取条件", "conditions/**/*.yml", "PDC 属性读取条件定义文件目录，控制物品属性在何种条件下生效。");
        WebConsoleRegistry.registerNodeKeyComment(this, "source_id", "来源ID", "条件规则的来源标识，用于日志和调试追踪。", "text");
        WebConsoleRegistry.registerNodeKeyComment(this, "condition_type", "条件逻辑", "多条件组合逻辑。", "enum:all_of,any_of");
        WebConsoleRegistry.registerNodeKeyComment(this, "invalid_as_failure", "解析失败", "条件表达式解析失败时是否视为不通过。", "boolean");
        WebConsoleRegistry.registerNodeKeyComment(this, "conditions", "条件列表", "具体条件项列表，每项包含 type、key/pattern 和 condition 表达式。", "list");
        WebConsoleRegistry.registerNodeSuffixComment(this, "conditions.type", "条件类型", "条件匹配类型。", "enum:pdc_meta,lore_regex");
        WebConsoleRegistry.registerNodeSuffixComment(this, "conditions.key", "PDC键名", "要匹配的 PDC 数据键名。", "text");
        WebConsoleRegistry.registerNodeSuffixComment(this, "conditions.pattern", "正则模式", "用于从 Lore 中提取数值的正则表达式。", "text");
        WebConsoleRegistry.registerNodeSuffixComment(this, "conditions.condition", "判定表达式", "条件判定表达式，支持 {value}、{player_level}、{player_name} 等变量。", "text");
        WebConsoleRegistry.registerNodeSuffixComment(this, "conditions.require_match", "必须匹配", "是否要求正则必须命中才视为通过。", "boolean");

        messageService.info("console.plugin_started");
    }

}
