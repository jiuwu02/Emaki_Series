package emaki.jiuwu.craft.corelib;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.action.ActionExecutor;
import emaki.jiuwu.craft.corelib.action.ActionLineParser;
import emaki.jiuwu.craft.corelib.action.ActionRegistry;
import emaki.jiuwu.craft.corelib.action.ActionTemplateRegistry;
import emaki.jiuwu.craft.corelib.action.builtin.BuiltinActions;
import emaki.jiuwu.craft.corelib.action.builtin.RunJavaScriptAction;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.library.RuntimeLibraryLoader;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyService;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemLayerCodecRegistry;
import emaki.jiuwu.craft.corelib.assembly.EmakiNamespaceDefinition;
import emaki.jiuwu.craft.corelib.assembly.EmakiNamespaceRegistry;
import emaki.jiuwu.craft.corelib.async.AsyncFileService;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.command.CoreLibCommandRouter;
import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.api.integration.CraftEngineBlockBridge;
import emaki.jiuwu.craft.corelib.integration.CraftEngineBlockBridgeProvider;
import emaki.jiuwu.craft.corelib.api.integration.CustomBlockBridge;
import emaki.jiuwu.craft.corelib.integration.ItemsAdderBlockBridgeProvider;
import emaki.jiuwu.craft.corelib.integration.NexoBlockBridgeProvider;
import emaki.jiuwu.craft.corelib.item.ItemSourceIntegrationCoordinator;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.monitor.PerformanceMonitor;
import emaki.jiuwu.craft.corelib.pdc.PdcService;
import emaki.jiuwu.craft.corelib.placeholder.ActionContextPlaceholderResolver;
import emaki.jiuwu.craft.corelib.placeholder.ActionInlineTokenResolver;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderApiResolver;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRegistry;
import emaki.jiuwu.craft.corelib.script.JavaScriptService;
import emaki.jiuwu.craft.corelib.script.ScriptService;
import emaki.jiuwu.craft.corelib.script.graal.GraalJavaScriptService;
import emaki.jiuwu.craft.corelib.service.EmakiServiceRegistry;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.text.AdventureSupport;
import emaki.jiuwu.craft.corelib.web.WebConsoleRegistry;
import emaki.jiuwu.craft.corelib.web.WebConsoleService;
import emaki.jiuwu.craft.corelib.yaml.AsyncYamlFiles;
import emaki.jiuwu.craft.corelib.yaml.VersionedYamlFile;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;

public final class EmakiCoreLibPlugin extends JavaPlugin implements LogMessagesProvider, EmakiServiceRegistry {

    private static final String STARTUP_ASCII = """
 ______  __    __  ______  __  __   __  ______  ______  ______  ______  __      __  ______
/\\  ___\\/\\ "-./  \\/\\  __ \\/\\ \\/ /  /\\ \\/\\  ___\\/\\  __ \\/\\  == \\/\\  ___\\/\\ \\    /\\ \\/\\  == \\
\\ \\  __\\\\ \\ \\-./\\ \\ \\  __ \\ \\  _"-.\\ \\ \\ \\ \\___\\ \\ \\/\\ \\ \\  __<\\ \\  __\\\\ \\ \\___\\ \\ \\ \\  __<
 \\ \\_____\\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_\\ \\_\\\\ \\_\\ \\_____\\ \\_____\\ \\_\\ \\_\\ \\_____\\ \\_____\\ \\_\\ \\_____\\
  \\/_____/\\/_/  \\/_/\\/_/\\/_/\\/_/\\/_/ \\/_/\\/_____/\\/_____/\\/_/ /_/\\/_____/\\/_____/\\/_/\\/_____/
""";

    private static final String WEB_ICON = """
            <svg viewBox="0 0 38 38" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="M19 4l1.5 3.5L24 9l-3.5 1.5L19 14l-1.5-3.5L14 9l3.5-1.5L19 4zM19 14v4m-6 2h12a2 2 0 012 2v8a2 2 0 01-2 2H13a2 2 0 01-2-2v-8a2 2 0 012-2zm2 4h3m-3 3h5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>
            """;

    private LanguageLoader languageLoader;
    private MessageService messageService;
    private CoreLibConfig configModel = CoreLibConfig.defaults();
    private PerformanceMonitor performanceMonitor;
    private AsyncTaskScheduler asyncTaskScheduler;
    private AsyncFileService asyncFileService;
    private AsyncYamlFiles asyncYamlFiles;
    private ActionRegistry actionRegistry;
    private ActionTemplateRegistry actionTemplateRegistry;
    private PlaceholderRegistry placeholderRegistry;
    private EconomyManager economyManager;
    private ActionExecutor actionExecutor;
    private JavaScriptService javaScriptService;
    private final PdcService pdcService = new PdcService("emaki_corelib");
    private final ItemSourceService itemSourceService = new ItemSourceService();
    private ItemSourceIntegrationCoordinator itemSourceIntegrationCoordinator;
    private final EmakiNamespaceRegistry namespaceRegistry = new EmakiNamespaceRegistry();
    private final EmakiItemLayerCodecRegistry itemLayerCodecRegistry = new EmakiItemLayerCodecRegistry();
    private final CraftEngineBlockBridge craftEngineBlockBridge = new CraftEngineBlockBridgeProvider(this);
    private final CustomBlockBridge itemsAdderBlockBridge = new ItemsAdderBlockBridgeProvider(this);
    private final CustomBlockBridge nexoBlockBridge = new NexoBlockBridgeProvider(this);
    private EmakiItemAssemblyService itemAssemblyService;
    private final emaki.jiuwu.craft.corelib.assembly.LayerMigrationRegistry layerMigrationRegistry
            = new emaki.jiuwu.craft.corelib.assembly.LayerMigrationRegistry();
    private final emaki.jiuwu.craft.corelib.event.EmakiEventBus eventBus
            = new emaki.jiuwu.craft.corelib.event.EmakiEventBus();
    private final Map<Class<?>, Object> serviceRegistry = new ConcurrentHashMap<>();
    private DebugLogger debugLogger;
    private WebConsoleService webConsoleService;
    private CoreLibCommandRouter commandRouter;

    @Override
    public void onLoad() {
        new RuntimeLibraryLoader(this).load();
    }

    @Override
    public void onEnable() {
        initializeServices();
        ConsoleOutputs.sendGradientAscii(this, STARTUP_ASCII);
        messageService.info("console.plugin_starting");
        ensureBundledFile("config.yml");
        configModel = loadConfigModel();
        itemSourceIntegrationCoordinator.initialize();
        reloadActionSystem();
        registerCommandHandler();
        logStartupAudit();
        messageService.info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        if (messageService != null) {
            messageService.info("console.plugin_stopped");
        }
        if (webConsoleService != null) {
            webConsoleService.stop();
        }
        WebConsoleRegistry.unregisterModule(this);
        if (javaScriptService != null) {
            javaScriptService.close();
        }
        if (asyncTaskScheduler != null) {
            asyncTaskScheduler.shutdown(5_000L);
        }
        // 清理表达式引擎的全局缓存和当前线程缓存，防止内存泄漏
        ExpressionEngine.clearGlobalCache();
        ExpressionEngine.clearThreadLocalCache();
        emaki.jiuwu.craft.corelib.assembly.OperationTemplateRenderer.clearRegexCache();
        AdventureSupport.close(this);
    }

    @Override
    public MessageService messageService() {
        return messageService;
    }

    @Override
    public <T> T getService(Class<T> type) {
        if (type == null) {
            return null;
        }
        Object service = serviceRegistry.get(type);
        return type.isInstance(service) ? type.cast(service) : null;
    }

    public LanguageLoader languageLoader() {
        return languageLoader;
    }

    public void reloadActionSystem() {
        configModel = loadConfigModel();
        reloadWebConsole();
        reloadScriptSystem();
        actionRegistry = new ActionRegistry();
        actionTemplateRegistry = new ActionTemplateRegistry();
        placeholderRegistry = new PlaceholderRegistry();
        economyManager = new EconomyManager(this);
        placeholderRegistry.register(new ActionContextPlaceholderResolver());
        placeholderRegistry.register(new ActionInlineTokenResolver());
        placeholderRegistry.register(new PlaceholderApiResolver());
        for (var entry : configModel.actionTemplates().entrySet()) {
            actionTemplateRegistry.register(entry.getKey(), entry.getValue());
        }
        BuiltinActions.registerAll(
                actionRegistry,
                economyManager,
                itemSourceService,
                craftEngineBlockBridge,
                itemsAdderBlockBridge,
                nexoBlockBridge
        );
        if (javaScriptService != null && javaScriptService.enabled()) {
            for (RunJavaScriptAction action : RunJavaScriptAction.createAll(javaScriptService, configModel.scriptConfig())) {
                actionRegistry.register(action);
            }
        }
        actionExecutor = new ActionExecutor(
                this,
                actionRegistry,
                new ActionLineParser(),
                placeholderRegistry,
                actionTemplateRegistry,
                asyncTaskScheduler,
                performanceMonitor
        );
        refreshServiceRegistry();
    }

    private void reloadScriptSystem() {
        if (javaScriptService != null) {
            javaScriptService.close();
            javaScriptService = null;
        }
        if (configModel == null || configModel.scriptConfig() == null || !configModel.scriptConfig().enabled()) {
            messageService.info("console.script_engine_disabled");
            return;
        }
        try {
            javaScriptService = new GraalJavaScriptService(
                    this,
                    configModel.scriptConfig(),
                    dataPath(configModel.scriptConfig().paths().root()),
                    () -> actionExecutor
            );
            messageService.info("console.script_engine_ready");
            messageService.info("console.scripts_loaded", Map.of("count", String.valueOf(javaScriptService.loadedScripts().size())));
        } catch (Exception exception) {
            messageService.warning("console.script_engine_failed", Map.of("error", String.valueOf(exception.getMessage())));
        }
    }

    private void reloadWebConsole() {
        registerWebConsole();
        if (webConsoleService == null) {
            webConsoleService = new WebConsoleService(this, configModel.webConsoleConfig());
        }
        webConsoleService.stop();
        webConsoleService.restart(configModel.webConsoleConfig());
        refreshServiceRegistry();
    }

    private void registerWebConsole() {
        WebConsoleRegistry.unregisterModule(this);
        WebConsoleRegistry.registerModule(this, "CoreLib 框架", "Web Console、Action、脚本与公共运行库", "core", WEB_ICON);
        WebConsoleRegistry.registerConfigFile(this, "CoreLib 主配置", "config.yml", "完整 config.yml 结构化配置注册。所有字段均通过 CoreLib 注释注册器补充说明。");
        WebConsoleRegistry.registerScriptFile(this, "CoreLib JS 脚本", "scripts/**/*.js", "CoreLib JavaScript 脚本目录，当前仅保留文本预览入口。");
        WebConsoleRegistry.registerCommonConfigComments(this);
        registerCoreLibWebComments();
    }

    private void registerCoreLibWebComments() {
        WebConsoleRegistry.registerNodeComment(this, "web_console", "Web Console", "内置前端控制台开放策略与鉴权配置。", "object");
        WebConsoleRegistry.registerNodeComment(this, "web_console.enabled", "启用前端", "开启后监听 host:port，reload 会先关闭再按新配置启动。", "boolean");
        WebConsoleRegistry.registerNodeComment(this, "web_console.host", "监听地址", "127.0.0.1 仅本机，0.0.0.0 表示所有网卡。", "text");
        WebConsoleRegistry.registerNodeComment(this, "web_console.port", "监听端口", "Web Console HTTP 端口。", "number");
        WebConsoleRegistry.registerNodeComment(this, "web_console.public_access_warning", "公网提示", "当监听地址可能对外开放时，在登录响应中提示风险。", "boolean");
        WebConsoleRegistry.registerNodeComment(this, "web_console.auth", "登录鉴权", "Web Console 登录账号、密码和会话有效期。", "object");
        WebConsoleRegistry.registerNodeComment(this, "web_console.auth.username", "账号", "Web Console 登录账号。", "text");
        WebConsoleRegistry.registerNodeComment(this, "web_console.auth.password", "密码", "Web Console 登录密码，启用前必须修改默认值。", "text");
        WebConsoleRegistry.registerNodeComment(this, "web_console.auth.session_timeout_minutes", "会话分钟", "登录 Token 的有效分钟数。", "number");
        WebConsoleRegistry.registerNodeComment(this, "web_console.security", "安全限制", "Web Console 请求体、写入权限等安全限制。", "object");
        WebConsoleRegistry.registerNodeComment(this, "web_console.security.allow_config_write", "允许写配置", "开启后 Web Console 才允许保存配置变更。", "boolean");
        WebConsoleRegistry.registerNodeComment(this, "web_console.security.max_request_body_kb", "请求体上限", "单次 Web 请求体大小上限，单位 KB。", "number");
        WebConsoleRegistry.registerNodeComment(this, "action", "Action", "CoreLib 动作系统配置。", "object");
        WebConsoleRegistry.registerNodeComment(this, "action.templates", "动作模板", "可在配方或动作列表中通过 @template=名称 引用的动作模板。每个子键为模板名称，值为动作列表。", "dynamic_map");
        WebConsoleRegistry.registerNodeComment(this, "script", "CoreLib JS", "CoreLib JavaScript 引擎与脚本安全配置。", "object");
        WebConsoleRegistry.registerNodeComment(this, "script.enabled", "启用脚本", "是否启用 CoreLib JavaScript 动作能力。", "boolean");
        WebConsoleRegistry.registerNodeComment(this, "script.engine", "脚本引擎", "GraalJS 引擎、超时、缓存和宿主访问配置。", "object");
        WebConsoleRegistry.registerNodeComment(this, "script.security", "脚本安全", "脚本路径、动作派发和调用深度限制。", "object");
    }

    private void logStartupAudit() {
        if (economyManager == null) {
            return;
        }
        for (String providerId : economyManager.availableProviderIds()) {
            messageService.info("console.economy_bridge_ready", Map.of("provider", providerId));
        }
        for (String blockProvider : new String[]{"CraftEngine", "ItemsAdder", "Nexo"}) {
            if (getServer().getPluginManager().isPluginEnabled(blockProvider)) {
                messageService.info("console.block_source_bridge_ready", Map.of("provider", blockProvider));
            }
        }
    }

    public Path dataPath(String first, String... more) {
        return getDataFolder().toPath().resolve(Path.of(first, more));
    }

    private void registerCommandHandler() {
        commandRouter = new CoreLibCommandRouter(this);
        PluginCommand pluginCommand = getCommand("emakicorelib");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(commandRouter);
            pluginCommand.setTabCompleter(commandRouter);
        }
    }

    private void initializeServices() {
        languageLoader = new LanguageLoader(this);
        messageService = new MessageService(this, languageLoader);
        debugLogger = new DebugLogger(getLogger(), languageLoader);
        itemSourceIntegrationCoordinator = new ItemSourceIntegrationCoordinator(this, messageService, itemSourceService);
        performanceMonitor = new PerformanceMonitor();
        asyncTaskScheduler = AsyncTaskScheduler.forPlugin(this, "emaki-corelib-async", performanceMonitor);
        asyncFileService = new AsyncFileService(asyncTaskScheduler, 3, performanceMonitor);
        asyncYamlFiles = new AsyncYamlFiles(asyncFileService);
        languageLoader.load();
        namespaceRegistry.register(new EmakiNamespaceDefinition("forge", 100, "Forge"));
        namespaceRegistry.register(new EmakiNamespaceDefinition("strengthen", 200, "Strengthen"));
        namespaceRegistry.register(new EmakiNamespaceDefinition("gem", 300, "Gem"));
        namespaceRegistry.register(new EmakiNamespaceDefinition("cooking", 10000, "Cooking"));
        itemAssemblyService = new EmakiItemAssemblyService(namespaceRegistry, itemLayerCodecRegistry, itemSourceService);
        itemAssemblyService.configureAsync(asyncTaskScheduler, performanceMonitor);
        refreshServiceRegistry();
    }

    private void ensureBundledFile(String relativePath) {
        File target = new File(getDataFolder(), relativePath);
        try {
            boolean copied = YamlFiles.copyResourceIfMissing(this, relativePath, target);
            if (!copied && !target.exists()) {
                messageService.warning("loader.bundled_resource_missing", java.util.Map.of(
                        "type", "资源",
                        "path", target.getPath(),
                        "resource", relativePath
                ));
            }
        } catch (Exception exception) {
            messageService.warning("loader.bundled_resource_write_failed", java.util.Map.of(
                    "path", target.getPath(),
                    "error", String.valueOf(exception.getMessage())
            ));
        }
    }

    private CoreLibConfig loadConfigModel() {
        try {
            File file = new File(getDataFolder(), "config.yml");
            VersionedYamlFile versionedFile = YamlFiles.syncVersionedResource(this, file, "config.yml", "version");
            return CoreLibConfig.fromConfig(versionedFile == null ? YamlFiles.load(file) : versionedFile.root());
        } catch (Exception exception) {
            messageService.warning("console.action_config_load_failed", java.util.Map.of(
                    "error", String.valueOf(exception.getMessage())
            ));
            return CoreLibConfig.defaults();
        }
    }

    public CoreLibConfig configModel() {
        return configModel;
    }

    public ActionRegistry actionRegistry() {
        return actionRegistry;
    }

    public ActionTemplateRegistry actionTemplateRegistry() {
        return actionTemplateRegistry;
    }

    public PlaceholderRegistry placeholderRegistry() {
        return placeholderRegistry;
    }

    public EconomyManager economyManager() {
        return economyManager;
    }

    public ActionExecutor actionExecutor() {
        return actionExecutor;
    }

    public JavaScriptService javaScriptService() {
        return javaScriptService;
    }

    public AsyncTaskScheduler asyncTaskScheduler() {
        return asyncTaskScheduler;
    }

    public PerformanceMonitor performanceMonitor() {
        return performanceMonitor;
    }

    public AsyncFileService asyncFileService() {
        return asyncFileService;
    }

    public AsyncYamlFiles asyncYamlFiles() {
        return asyncYamlFiles;
    }

    public PdcService pdcService() {
        return pdcService;
    }

    public ItemSourceService itemSourceService() {
        return itemSourceService;
    }

    public EmakiNamespaceRegistry namespaceRegistry() {
        return namespaceRegistry;
    }

    public EmakiItemLayerCodecRegistry itemLayerCodecRegistry() {
        return itemLayerCodecRegistry;
    }

    public EmakiItemAssemblyService itemAssemblyService() {
        return itemAssemblyService;
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

    public DebugLogger debugLogger() {
        return debugLogger;
    }

    public WebConsoleService webConsoleService() {
        return webConsoleService;
    }

    private void refreshServiceRegistry() {
        serviceRegistry.clear();
        registerService(LanguageLoader.class, languageLoader);
        registerService(MessageService.class, messageService);
        registerService(PerformanceMonitor.class, performanceMonitor);
        registerService(AsyncTaskScheduler.class, asyncTaskScheduler);
        registerService(AsyncFileService.class, asyncFileService);
        registerService(AsyncYamlFiles.class, asyncYamlFiles);
        registerService(ActionRegistry.class, actionRegistry);
        registerService(ActionTemplateRegistry.class, actionTemplateRegistry);
        registerService(PlaceholderRegistry.class, placeholderRegistry);
        registerService(EconomyManager.class, economyManager);
        registerService(ActionExecutor.class, actionExecutor);
        registerService(JavaScriptService.class, javaScriptService);
        registerService(ScriptService.class, javaScriptService);
        registerService(WebConsoleService.class, webConsoleService);
        registerService(PdcService.class, pdcService);
        registerService(ItemSourceService.class, itemSourceService);
        registerService(EmakiNamespaceRegistry.class, namespaceRegistry);
        registerService(EmakiItemLayerCodecRegistry.class, itemLayerCodecRegistry);
        registerService(CraftEngineBlockBridge.class, craftEngineBlockBridge);
        registerService(ItemsAdderBlockBridgeProvider.class, (ItemsAdderBlockBridgeProvider) itemsAdderBlockBridge);
        registerService(NexoBlockBridgeProvider.class, (NexoBlockBridgeProvider) nexoBlockBridge);
        registerService(EmakiItemAssemblyService.class, itemAssemblyService);
        registerService(emaki.jiuwu.craft.corelib.assembly.LayerMigrationRegistry.class, layerMigrationRegistry);
        registerService(emaki.jiuwu.craft.corelib.event.EmakiEventBus.class, eventBus);
    }

    private <T> void registerService(Class<T> type, T service) {
        if (service != null) {
            serviceRegistry.put(type, service);
        }
    }
}
