package emaki.jiuwu.craft.item;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.metrics.BStatsRegistration;
import emaki.jiuwu.craft.corelib.debug.DebugCommand;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.api.integration.EmakiAttributeBridge;
import emaki.jiuwu.craft.corelib.integration.PdcAttributeGateway;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.plugin.AbstractConfigurableEmakiPlugin;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.AdventureSupport;
import emaki.jiuwu.craft.corelib.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.web.WebConsoleRegistry;
import emaki.jiuwu.craft.corelib.web.WebPluginApiRegistry;
import emaki.jiuwu.craft.corelib.web.insight.WebInsightAliasRegistry;
import emaki.jiuwu.craft.corelib.web.insight.WebInsightAliasResolver;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.item.api.EmakiItemApi;
import emaki.jiuwu.craft.item.config.AppConfig;
import emaki.jiuwu.craft.item.listener.ItemTriggerListener;
import emaki.jiuwu.craft.item.listener.ItemUpdateListener;
import emaki.jiuwu.craft.item.loader.EmakiItemAliasLoader;
import emaki.jiuwu.craft.item.loader.EmakiItemLoader;
import emaki.jiuwu.craft.item.loader.EmakiItemSetLoader;
import emaki.jiuwu.craft.item.papi.ItemPlaceholderExpansion;
import emaki.jiuwu.craft.item.service.EmakiItemActionService;
import emaki.jiuwu.craft.item.service.EmakiItemConditionChecker;
import emaki.jiuwu.craft.item.service.EmakiItemFactory;
import emaki.jiuwu.craft.item.service.EmakiItemIdentifier;
import emaki.jiuwu.craft.item.service.EmakiItemIdResolver;
import emaki.jiuwu.craft.item.service.EmakiItemLayerPreviewService;
import emaki.jiuwu.craft.item.service.EmakiItemMigrationService;
import emaki.jiuwu.craft.item.service.EmakiItemPdcWriter;
import emaki.jiuwu.craft.item.service.EmakiItemSetService;
import emaki.jiuwu.craft.item.service.EmakiItemUpdateService;
import emaki.jiuwu.craft.item.service.ItemComponentInspector;
import emaki.jiuwu.craft.item.service.ItemComponentPlaceholderResolver;

public final class EmakiItemPlugin extends AbstractConfigurableEmakiPlugin<AppConfig> implements LogMessagesProvider {

    private static final String ROOT_COMMAND = "emakiitem";
    private static final Set<String> DEBUG_MODULES = Set.of("create", "update", "identify");
    private static final String STARTUP_ASCII = """
 ______  __    __  ______  __  __   __  __  ______  ______  __    __  ______
/\\  ___\\/\\ "-./  \\/\\  __ \\/\\ \\/ /  /\\ \\/\\ \\/\\__  _\\/\\  ___\\/\\ "-./  \\/\\  ___\\
\\ \\  __\\\\ \\ \\-./\\ \\ \\  __ \\ \\  _"-.\\ \\ \\ \\ \\/_/\\ \\/\\ \\  __\\\\ \\ \\-./\\ \\ \\___  \\
 \\ \\_____\\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_\\ \\_\\\\ \\_\\ \\_\\ \\ \\_\\ \\ \\_____\\ \\_\\ \\ \\_\\/\\_____\\
  \\/_____/\\/_/  \\/_/\\/_/\\/_/\\/_/\\/_/ \\/_/\\/_/  \\/_/  \\/_____/\\/_/  \\/_/\\/_____/
""";
    private static final int BSTATS_PLUGIN_ID = 31770;

    private BStatsRegistration metrics;

    private final ItemLifecycleCoordinator lifecycleCoordinator = new ItemLifecycleCoordinator();
    private final ItemCommandRouter commandRouter = new ItemCommandRouter(this);
    private ItemPlaceholderExpansion placeholderExpansion;
    private DebugCommand debugCommand;

    private YamlConfigLoader<AppConfig> appConfigLoader;
    private LanguageLoader languageLoader;
    private MessageService messageService;
    private BootstrapService bootstrapService;
    private EmakiItemLoader itemLoader;
    private EmakiItemSetLoader setLoader;
    private EmakiItemAliasLoader aliasLoader;
    private EmakiItemIdResolver idResolver;
    private EmakiItemMigrationService migrationService;
    private EmakiItemLayerPreviewService layerPreviewService;
    private EmakiItemIdentifier identifier;
    private EmakiItemPdcWriter pdcWriter;
    private EmakiItemFactory itemFactory;
    private EmakiItemUpdateService updateService;
    private EmakiItemSetService setService;
    private EmakiItemActionService actionService;
    private EmakiItemConditionChecker conditionChecker;
    private ItemComponentInspector componentInspector;
    private ItemComponentPlaceholderResolver componentPlaceholderResolver;
    private ItemSourceService itemSourceService;
    private PdcAttributeGateway pdcAttributeGateway;
    private final EmakiItemApi.Bridge itemApiBridge = new EmakiItemApi.Bridge() {
        @Override
        public boolean exists(String id) {
            return idResolver != null && idResolver.resolveDefinition(id) != null;
        }

        @Override
        public @Nullable ItemStack create(String id, int amount) {
            return itemFactory == null ? null : itemFactory.create(id, amount);
        }

        @Override
        public @Nullable String identify(@Nullable ItemStack itemStack) {
            return identifier == null ? null : identifier.identify(itemStack);
        }

        @Override
        public @NotNull Set<String> definitionIds() {
            return itemLoader == null ? Set.of() : itemLoader.all().keySet();
        }

        @Override
        public @NotNull String displayName(String id) {
            ItemStack itemStack = create(id, 1);
            if (itemStack == null) {
                return "";
            }
            String text = ItemTextBridge.effectiveNameText(itemStack);
            return Texts.isBlank(text) ? MiniMessages.serialize(ItemTextBridge.effectiveName(itemStack)) : text;
        }
    };

    public EmakiItemPlugin() {
        super(AppConfig::defaults);
    }

    @Override
    public void onEnable() {
        ConsoleOutputs.sendGradientAscii(this, STARTUP_ASCII);
        applyRuntimeComponents(lifecycleCoordinator.initialize(this));
        messageService.info("console.plugin_starting");
        bootstrapService.bootstrap();
        reloadPluginState();
        EmakiItemApi.install(itemApiBridge);
        lifecycleCoordinator.registerServices(this);
        registerCommandHandler();
        registerEventHandlers();
        registerWebConsole();
        ensurePlaceholderExpansion();
        metrics = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class).registerBStats(this, BSTATS_PLUGIN_ID);
        messageService.info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
            placeholderExpansion = null;
        }
        if (metrics != null) {
            metrics.close();
            metrics = null;
        }
        WebConsoleRegistry.unregisterModule(this);
        WebPluginApiRegistry.unregister(this);
        WebInsightAliasRegistry.unregister(this);
        EmakiItemApi.uninstall(itemApiBridge);
        lifecycleCoordinator.shutdown(this);
        AdventureSupport.close(this);
    }

    public void reloadPluginState() {
        lifecycleCoordinator.reload(this);
    }

    public CompletableFuture<Void> reloadPluginStateAsync() {
        return lifecycleCoordinator.reloadAsync(this, null);
    }

    private void applyRuntimeComponents(ItemRuntimeComponents components) {
        appConfigLoader = components.appConfigLoader();
        languageLoader = components.languageLoader();
        messageService = components.messageService();
        bootstrapService = components.bootstrapService();
        itemLoader = components.itemLoader();
        setLoader = components.setLoader();
        aliasLoader = components.aliasLoader();
        idResolver = components.idResolver();
        migrationService = components.migrationService();
        layerPreviewService = components.layerPreviewService();
        identifier = components.identifier();
        pdcWriter = components.pdcWriter();
        itemFactory = components.itemFactory();
        updateService = components.updateService();
        setService = components.setService();
        actionService = components.actionService();
        conditionChecker = components.conditionChecker();
        componentInspector = components.componentInspector();
        componentPlaceholderResolver = components.componentPlaceholderResolver();
        itemSourceService = components.itemSourceService();
        pdcAttributeGateway = components.pdcAttributeGateway();
        setDebugLogger(new DebugLogger(getLogger(), languageLoader));
        debugCommand = new DebugCommand(debugLogger(), DEBUG_MODULES);
        registerServices(components);
    }

    private void registerCommandHandler() {
        PluginCommand pluginCommand = getCommand(ROOT_COMMAND);
        if (pluginCommand != null) {
            pluginCommand.setExecutor(commandRouter);
            pluginCommand.setTabCompleter(commandRouter);
        }
    }

    private void registerEventHandlers() {
        getServer().getPluginManager().registerEvents(new ItemTriggerListener(this), this);
        getServer().getPluginManager().registerEvents(new ItemUpdateListener(this), this);
    }

    private void registerWebConsole() {
        WebConsoleRegistry.registerFromYaml(this);
        WebInsightAliasRegistry.register(this, new WebInsightAliasResolver() {
            @Override
            public String idType() {
                return "emaki_item";
            }

            @Override
            public AliasResolution resolve(String sourceId) {
                if (aliasLoader == null) {
                    return null;
                }
                emaki.jiuwu.craft.item.model.EmakiItemAlias alias = aliasLoader.get(sourceId);
                return alias == null ? null : new AliasResolution(alias.oldId(), alias.targetId());
            }
        });
        WebPluginApiRegistry.register(this, "item", "alias-list", request -> Map.of("ok", true, "aliases", aliasLoader == null ? Map.of() : aliasLoader.all().values().stream()
                .map(alias -> Map.of(
                        "oldId", alias.oldId(),
                        "targetId", alias.targetId(),
                        "migratePdc", alias.migratePdc(),
                        "rewriteDisplay", alias.rewriteDisplay(),
                        "expiresAfter", alias.expiresAfter()))
                .toList()));
        WebPluginApiRegistry.register(this, "item", "rename-preview", request -> {
            request.requirePost();
            return migrationService.preview(request.string("oldId"), request.string("newId"));
        });
        WebPluginApiRegistry.register(this, "item", "rename-apply", request -> {
            request.requirePost();
            request.requireConfigWriteAllowed();
            String mode = Texts.lower(request.string("mode"));
            boolean replaceReferences = !"alias_only".equals(mode);
            boolean keepAlias = "alias_only".equals(mode) || "replace_and_alias".equals(mode);
            return migrationService.apply(request.string("oldId"), request.string("newId"), replaceReferences, keepAlias, request.longMap("revisions"), request);
        });
        WebPluginApiRegistry.register(this, "item", "preview-layered", request -> {
            request.requirePost();
            return layerPreviewService.preview(request.string("content"), request.string("itemId"), request.map("layers"));
        });
    }

    private void ensurePlaceholderExpansion() {
        if (placeholderExpansion != null || !Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        placeholderExpansion = new ItemPlaceholderExpansion(this);
        placeholderExpansion.register();
        messageService.info("console.papi_registered");
    }

    @Override
    public YamlConfigLoader<AppConfig> appConfigLoader() {
        return appConfigLoader;
    }

    public LanguageLoader languageLoader() {
        return languageLoader;
    }

    public MessageService messageService() {
        return messageService;
    }

    public BootstrapService bootstrapService() {
        return bootstrapService;
    }

    public EmakiItemLoader itemLoader() {
        return itemLoader;
    }

    public EmakiItemSetLoader setLoader() {
        return setLoader;
    }

    public EmakiItemAliasLoader aliasLoader() {
        return aliasLoader;
    }

    public EmakiItemIdResolver idResolver() {
        return idResolver;
    }

    public EmakiItemMigrationService migrationService() {
        return migrationService;
    }

    public EmakiItemLayerPreviewService layerPreviewService() {
        return layerPreviewService;
    }

    public EmakiItemIdentifier identifier() {
        return identifier;
    }

    public EmakiItemPdcWriter pdcWriter() {
        return pdcWriter;
    }

    public EmakiItemFactory itemFactory() {
        return itemFactory;
    }

    public EmakiItemUpdateService updateService() {
        return updateService;
    }

    public EmakiItemSetService setService() {
        return setService;
    }

    public EmakiItemActionService actionService() {
        return actionService;
    }

    public EmakiItemConditionChecker conditionChecker() {
        return conditionChecker;
    }

    public EmakiItemApi.Bridge itemApi() {
        return itemApiBridge;
    }

    public ItemComponentInspector componentInspector() {
        return componentInspector;
    }

    public ItemComponentPlaceholderResolver componentPlaceholderResolver() {
        return componentPlaceholderResolver;
    }

    public ItemSourceService itemSourceService() {
        return itemSourceService;
    }

    public PdcAttributeGateway pdcAttributeGateway() {
        return pdcAttributeGateway;
    }

    public void scheduleAttributeEquipmentSync(Player player) {
        if (player == null) {
            return;
        }
        RegisteredServiceProvider<EmakiAttributeBridge> registration = Bukkit.getServicesManager().getRegistration(EmakiAttributeBridge.class);
        EmakiAttributeBridge bridge = registration == null ? null : registration.getProvider();
        if (bridge != null && bridge.available()) {
            bridge.scheduleEquipmentSync(player);
        }
    }

    public DebugCommand debugCommand() {
        return debugCommand;
    }
}
