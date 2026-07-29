package emaki.jiuwu.craft.item;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapHooks;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.item.integration.ItemAttributeBridge;
import emaki.jiuwu.craft.item.integration.ItemAttributeBridgeHolder;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.pdc.PdcService;
import emaki.jiuwu.craft.corelib.runtime.AbstractLifecycleCoordinator;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import emaki.jiuwu.craft.item.config.AppConfig;
import emaki.jiuwu.craft.item.model.ItemUpdateConfig;
import emaki.jiuwu.craft.item.model.SetBonusConfig;
import emaki.jiuwu.craft.item.loader.EmakiItemAliasLoader;
import emaki.jiuwu.craft.item.loader.EmakiItemLoader;
import emaki.jiuwu.craft.item.loader.EmakiItemSetLoader;
import emaki.jiuwu.craft.item.service.EmakiItemActionService;
import emaki.jiuwu.craft.item.service.EmakiItemConditionChecker;
import emaki.jiuwu.craft.item.service.EmakiItemFactory;
import emaki.jiuwu.craft.item.service.EmakiItemIdentifier;
import emaki.jiuwu.craft.item.service.EmakiItemIdResolver;
import emaki.jiuwu.craft.item.service.EmakiItemLayerPreviewRegistry;
import emaki.jiuwu.craft.item.service.EmakiItemLayerPreviewService;
import emaki.jiuwu.craft.item.service.EmakiItemMigrationService;
import emaki.jiuwu.craft.item.service.EmakiItemPdcWriter;
import emaki.jiuwu.craft.item.service.EmakiItemSetService;
import emaki.jiuwu.craft.item.service.EmakiItemSourceResolver;
import emaki.jiuwu.craft.item.service.EmakiItemUpdateService;
import emaki.jiuwu.craft.item.service.ItemComponentInspector;
import emaki.jiuwu.craft.item.service.ItemComponentPlaceholderResolver;
import emaki.jiuwu.craft.item.service.ItemRepairGuiService;
import emaki.jiuwu.craft.item.service.ItemRepairService;
import emaki.jiuwu.craft.item.service.ItemSetLoreRenderer;

final class ItemLifecycleCoordinator extends AbstractLifecycleCoordinator<EmakiItemPlugin, ItemRuntimeComponents> {

    private static final String DEFAULT_PREFIX = "<gray>[ <gradient:#60A5FA:#34D399>EmakiItem</gradient> ]</gray>";
    private static final String PDC_ATTRIBUTE_SOURCE_ID = "emakiitem";
    private static final List<String> VERSIONED_FILES = List.of("config.yml", "lang/zh_CN.yml", "lang/en_US.yml");
    private static final List<String> DEFAULT_DATA_FILES = List.of("items/example_item.yml", "sets/example_set.yml", "gui/repair_gui.yml", "id_aliases.yml");
    private static final List<String> EXTRA_DIRECTORIES = List.of("items", "sets", "gui");

    private ItemSourceService.ResolverRegistration itemSourceResolverRegistration;

    @Override
    public ItemRuntimeComponents initialize(EmakiItemPlugin plugin) {
        EmakiCoreLibPlugin coreLibPlugin = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
        ExecutionDispatcher executionDispatcher = coreLibPlugin.executionDispatcher();
        ThreadOwnership threadOwnership = coreLibPlugin.threadOwnership();
        YamlConfigLoader<AppConfig> appConfigLoader = new YamlConfigLoader<>(
                plugin,
                "config.yml",
                AppConfig::defaults,
                this::parseAppConfig
        );
        appConfigLoader.load();
        LanguageLoader languageLoader = new LanguageLoader(plugin, "lang", "lang", "zh_CN", "zh_CN");
        MessageService messageService = new MessageService(plugin, languageLoader, DEFAULT_PREFIX, true);
        languageLoader.load();
        languageLoader.setLanguage(appConfigLoader.current().language());
        BootstrapService bootstrapService = new BootstrapService(
                plugin,
                messageService,
                VERSIONED_FILES,
                List.of(),
                DEFAULT_DATA_FILES,
                EXTRA_DIRECTORIES,
                new BootstrapHooks() {
                    @Override
                    public boolean shouldInstallDefaultData() {
                        return shouldReleaseDefaultData(plugin);
                    }

                    @Override
                    public void afterVersionedMerge(String relativePath, YamlSection runtime, YamlSection bundled) {
                        if ("config.yml".equals(relativePath) && runtime != null) {
                            runtime.set("item_update", null);
                        }
                    }
                }
        );
        EmakiItemLoader itemLoader = new EmakiItemLoader(plugin);
        EmakiItemSetLoader setLoader = new EmakiItemSetLoader(plugin);
        EmakiItemAliasLoader aliasLoader = new EmakiItemAliasLoader(plugin);
        GuiTemplateLoader guiTemplateLoader = new GuiTemplateLoader(plugin);
        GuiService guiService = new GuiService(plugin, executionDispatcher, coreLibPlugin.asyncTaskScheduler(), coreLibPlugin.performanceMonitor(), coreLibPlugin.guiBackend());
        EmakiItemIdResolver idResolver = new EmakiItemIdResolver(itemLoader, aliasLoader);
        EmakiItemMigrationService migrationService = new EmakiItemMigrationService(plugin);
        EmakiItemLayerPreviewRegistry layerPreviewRegistry = new EmakiItemLayerPreviewRegistry();
        EmakiItemLayerPreviewService layerPreviewService = new EmakiItemLayerPreviewService(plugin, layerPreviewRegistry);
        PdcService pdcService = new PdcService("emaki", "pdc", plugin.debugLogger());
        EmakiItemIdentifier identifier = new EmakiItemIdentifier(pdcService);
        ItemAttributeBridge pdcAttributeGateway = new ItemAttributeBridgeHolder(plugin.getLogger());
        pdcAttributeGateway.syncRegistration(PDC_ATTRIBUTE_SOURCE_ID);
        EmakiItemPdcWriter pdcWriter = new EmakiItemPdcWriter(
                identifier,
                pdcAttributeGateway,
                plugin.debugLogger()
        );
        EmakiItemFactory itemFactory = new EmakiItemFactory(
                itemLoader,
                idResolver,
                pdcWriter,
                threadOwnership,
                plugin.debugLogger()
        );
        EmakiItemUpdateService updateService = new EmakiItemUpdateService(
                itemLoader,
                idResolver,
                itemFactory,
                identifier,
                pdcAttributeGateway::copyPayloads,
                coreLibPlugin.itemAssemblyService(),
                plugin.debugLogger()
        );
        EmakiItemSetService setService = new EmakiItemSetService(
                itemLoader,
                setLoader,
                itemFactory,
                identifier,
                pdcWriter,
                new ItemSetLoreRenderer(),
                plugin::appConfig,
                plugin::debugLogger,
                plugin.getLogger(),
                threadOwnership
        );
        ItemComponentInspector componentInspector = new ItemComponentInspector();
        ItemComponentPlaceholderResolver componentPlaceholderResolver = new ItemComponentPlaceholderResolver(componentInspector);
        coreLibPlugin.placeholderRegistry().register(componentPlaceholderResolver);
        EmakiItemActionService actionService = new EmakiItemActionService(plugin, coreLibPlugin.actionExecutor());
        EmakiItemConditionChecker conditionChecker = new EmakiItemConditionChecker(plugin, coreLibPlugin.placeholderRegistry(), actionService);
        ItemRepairService repairService = new ItemRepairService(
                plugin,
                coreLibPlugin::economyManager,
                coreLibPlugin.itemSourceService(),
                threadOwnership
        );
        ItemRepairGuiService repairGuiService = new ItemRepairGuiService(plugin, guiService, repairService);
        return new ItemRuntimeComponents(
                executionDispatcher,
                threadOwnership,
                appConfigLoader,
                languageLoader,
                messageService,
                bootstrapService,
                guiTemplateLoader,
                guiService,
                itemLoader,
                setLoader,
                aliasLoader,
                idResolver,
                migrationService,
                layerPreviewRegistry,
                layerPreviewService,
                identifier,
                pdcWriter,
                itemFactory,
                updateService,
                setService,
                actionService,
                conditionChecker,
                componentInspector,
                componentPlaceholderResolver,
                coreLibPlugin.itemSourceService(),
                pdcAttributeGateway,
                pdcService,
                repairService,
                repairGuiService
        );
    }

    public void reload(EmakiItemPlugin plugin) {
        reload(plugin, () -> true);
    }

    public void reload(EmakiItemPlugin plugin, BooleanSupplier allowed) {
        if (!reloadAllowed(allowed)) {
            return;
        }
        closeRepairInventories(plugin);
        if (!reloadAllowed(allowed)) {
            return;
        }
        plugin.languageLoader().load();
        if (!reloadAllowed(allowed)) {
            return;
        }
        plugin.appConfigLoader().load();
        if (!reloadAllowed(allowed)) {
            return;
        }
        plugin.languageLoader().setLanguage(plugin.appConfig().language());
        if (!reloadAllowed(allowed)) {
            return;
        }
        plugin.pdcAttributeGateway().syncRegistration(PDC_ATTRIBUTE_SOURCE_ID);
        if (!reloadAllowed(allowed)) {
            return;
        }
        int loadedItems = plugin.itemLoader().load(allowed);
        if (!reloadAllowed(allowed)) {
            return;
        }
        int loadedSets = plugin.setLoader().load(allowed);
        if (!reloadAllowed(allowed)) {
            return;
        }
        int loadedAliases = plugin.aliasLoader().load(allowed);
        if (!reloadAllowed(allowed)) {
            return;
        }
        plugin.guiTemplateLoader().load();
        if (!reloadAllowed(allowed)) {
            return;
        }
        plugin.itemFactory().clearCache();
        plugin.setService().clearAllCachedState();
        if (plugin.messageService() != null && reloadAllowed(allowed)) {
            plugin.messageService().info("console.items_loaded", java.util.Map.of("count", loadedItems));
            plugin.messageService().info("console.sets_loaded", java.util.Map.of("count", loadedSets));
            plugin.getLogger().info("Loaded " + loadedAliases + " EmakiItem ID aliases.");
        }
    }

    public CompletableFuture<Void> reloadAsync(EmakiItemPlugin plugin, Consumer<String> progressListener) {
        return reloadAsync(plugin, progressListener, () -> true);
    }

    public CompletableFuture<Void> reloadAsync(EmakiItemPlugin plugin,
                                               Consumer<String> progressListener,
                                               BooleanSupplier allowed) {
        if (!reloadAllowed(allowed)) {
            return CompletableFuture.completedFuture(null);
        }
        closeRepairInventories(plugin);
        if (!reloadAllowed(allowed)) {
            return CompletableFuture.completedFuture(null);
        }
        AsyncTaskScheduler scheduler = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class).asyncTaskScheduler();
        if (scheduler == null) {
            reload(plugin, allowed);
            return CompletableFuture.completedFuture(null);
        }

        notifyProgress(progressListener, "Loading configuration files...");

        return runReloadStageAsync(scheduler, new ReloadStageConfig<>(
                "item", "config-load", "Loading configs...", progressListener,
                () -> {
                    if (!reloadAllowed(allowed)) {
                        return;
                    }
                    plugin.languageLoader().load();
                    if (!reloadAllowed(allowed)) {
                        return;
                    }
                    plugin.appConfigLoader().load();
                    if (!reloadAllowed(allowed)) {
                        return;
                    }
                    plugin.itemLoader().load(allowed);
                    if (!reloadAllowed(allowed)) {
                        return;
                    }
                    plugin.setLoader().load(allowed);
                    if (!reloadAllowed(allowed)) {
                        return;
                    }
                    plugin.aliasLoader().load(allowed);
                    if (!reloadAllowed(allowed)) {
                        return;
                    }
                    plugin.guiTemplateLoader().load();
                },
                null, (stage, ex) -> plugin.getLogger().warning("[Reload] Stage " + stage + " failed: " + ex.getMessage())
        )).thenCompose(ignored -> {
            if (!reloadAllowed(allowed)) {
                return CompletableFuture.completedFuture(null);
            }
            notifyProgress(progressListener, "Applying configuration...");
            return plugin.executionDispatcher().submitGlobal(plugin, () -> {
                if (!reloadAllowed(allowed)) {
                    return null;
                }
                plugin.languageLoader().setLanguage(plugin.appConfig().language());
                if (!reloadAllowed(allowed)) {
                    return null;
                }
                plugin.pdcAttributeGateway().syncRegistration(PDC_ATTRIBUTE_SOURCE_ID);
                if (!reloadAllowed(allowed)) {
                    return null;
                }
                plugin.itemFactory().clearCache();
                plugin.setService().clearAllCachedState();
                if (plugin.messageService() != null && reloadAllowed(allowed)) {
                    plugin.messageService().info("console.items_loaded", java.util.Map.of("count", plugin.itemLoader().all().size()));
                    plugin.messageService().info("console.sets_loaded", java.util.Map.of("count", plugin.setLoader().all().size()));
                }
                if (reloadAllowed(allowed)) {
                    notifyProgress(progressListener, "Reload complete.");
                }
                return null;
            });
        });
    }

    private boolean reloadAllowed(BooleanSupplier allowed) {
        return allowed == null || allowed.getAsBoolean();
    }

    private void closeRepairInventories(EmakiItemPlugin plugin) {
        if (plugin == null || plugin.repairGuiService() == null) {
            return;
        }
        for (var player : Bukkit.getOnlinePlayers()) {
            if (plugin.repairGuiService().getSession(player) != null) {
                player.closeInventory();
            }
        }
        plugin.repairGuiService().clearAllSessions();
    }

    public void registerServices(EmakiItemPlugin plugin) {
        closeItemSourceResolver();
        itemSourceResolverRegistration = plugin.itemSourceService()
                .registerResolverHandle(new EmakiItemSourceResolver(plugin.itemApi()));
    }

    public void shutdown(EmakiItemPlugin plugin) {
        if (plugin.messageService() != null) {
            plugin.messageService().info("console.plugin_stopping");
        }
        closeRepairInventories(plugin);
        EmakiCoreLibPlugin coreLibPlugin = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
        if (coreLibPlugin.placeholderRegistry() != null && plugin.componentPlaceholderResolver() != null) {
            coreLibPlugin.placeholderRegistry().unregister(plugin.componentPlaceholderResolver());
        }
        closeItemSourceResolver();
        if (plugin.layerPreviewRegistry() != null) {
            plugin.layerPreviewRegistry().close();
        }
        if (plugin.pdcWriter() != null) {
            plugin.pdcWriter().shutdown();
        }
        if (plugin.messageService() != null) {
            plugin.messageService().info("console.plugin_stopped");
        }
    }

    private AppConfig parseAppConfig(YamlSection configuration) {
        if (configuration == null || configuration.getKeys(false).isEmpty()) {
            return AppConfig.defaults();
        }
        List<String> mythicDropNames = configuration.getStringList("mythicmobs.drops.names");
        if (mythicDropNames.isEmpty()) {
            mythicDropNames = AppConfig.defaultMythicDropNames();
        }
        return new AppConfig(
                configuration.getString("language", "zh_CN"),
                configuration.getString("version", "2.5.19"),
                configuration.getBoolean("release_default_data", true),
                parseSetBonus(configuration.getSection("set_bonus")),
                configuration.getBoolean("mythicmobs.enabled", true),
                configuration.getBoolean("mythicmobs.drops.enabled", true),
                mythicDropNames
        );
    }

    private SetBonusConfig parseSetBonus(YamlSection section) {
        if (section == null) {
            return SetBonusConfig.defaults();
        }
        return new SetBonusConfig(section.getBoolean("enabled", true), parseTriggers(section.getSection("refresh_triggers")));
    }

    private ItemUpdateConfig.TriggerConfig parseTriggers(YamlSection section) {
        if (section == null) {
            return ItemUpdateConfig.TriggerConfig.defaults();
        }
        return new ItemUpdateConfig.TriggerConfig(
                section.getBoolean("join", true),
                section.getBoolean("held_change", true),
                section.getBoolean("inventory_click", true),
                section.getBoolean("inventory_drag", true),
                section.getBoolean("pickup", true),
                section.getBoolean("interact", true),
                section.getBoolean("command", true)
        );
    }

    private boolean shouldReleaseDefaultData(EmakiItemPlugin plugin) {
        YamlSection configuration = YamlFiles.load(plugin.dataPath("config.yml").toFile());
        return configuration.getBoolean("release_default_data", true);
    }

    private void closeItemSourceResolver() {
        ItemSourceService.ResolverRegistration registration = itemSourceResolverRegistration;
        itemSourceResolverRegistration = null;
        if (registration != null) {
            registration.close();
        }
    }
}
