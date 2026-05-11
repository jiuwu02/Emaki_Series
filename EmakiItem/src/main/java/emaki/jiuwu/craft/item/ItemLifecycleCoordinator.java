package emaki.jiuwu.craft.item;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapHooks;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.integration.PdcAttributeGateway;
import emaki.jiuwu.craft.corelib.integration.SkillPdcGateway;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.pdc.PdcService;
import emaki.jiuwu.craft.corelib.runtime.AbstractLifecycleCoordinator;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import emaki.jiuwu.craft.item.api.EmakiItemApi;
import emaki.jiuwu.craft.item.config.AppConfig;
import emaki.jiuwu.craft.item.model.ItemUpdateConfig;
import emaki.jiuwu.craft.item.model.SetBonusConfig;
import emaki.jiuwu.craft.item.loader.EmakiItemLoader;
import emaki.jiuwu.craft.item.loader.EmakiItemSetLoader;
import emaki.jiuwu.craft.item.service.DefaultEmakiItemApi;
import emaki.jiuwu.craft.item.service.EmakiItemActionService;
import emaki.jiuwu.craft.item.service.EmakiItemConditionChecker;
import emaki.jiuwu.craft.item.service.EmakiItemFactory;
import emaki.jiuwu.craft.item.service.EmakiItemIdentifier;
import emaki.jiuwu.craft.item.service.EmakiItemPdcWriter;
import emaki.jiuwu.craft.item.service.EmakiItemSetService;
import emaki.jiuwu.craft.item.service.EmakiItemSourceResolver;
import emaki.jiuwu.craft.item.service.EmakiItemUpdateService;
import emaki.jiuwu.craft.item.service.ItemSetLoreRenderer;

final class ItemLifecycleCoordinator extends AbstractLifecycleCoordinator<EmakiItemPlugin, ItemRuntimeComponents> {

    private static final String DEFAULT_PREFIX = "<gray>[ <gradient:#EBD48A:#7FB08A>Emaki Item</gradient> ]</gray>";
    private static final String PDC_ATTRIBUTE_SOURCE_ID = "emakiitem";
    private static final List<String> VERSIONED_FILES = List.of("config.yml", "lang/zh_CN.yml", "lang/en_US.yml");
    private static final List<String> DEFAULT_DATA_FILES = List.of("items/example_item.yml", "sets/example_set.yml");
    private static final List<String> EXTRA_DIRECTORIES = List.of("items", "sets");

    @Override
    public ItemRuntimeComponents initialize(EmakiItemPlugin plugin) {
        EmakiCoreLibPlugin coreLibPlugin = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
        YamlConfigLoader<AppConfig> appConfigLoader = new YamlConfigLoader<>(
                plugin,
                "config.yml",
                AppConfig::defaults,
                this::parseAppConfig
        );
        appConfigLoader.load();
        LanguageLoader languageLoader = new LanguageLoader(plugin, "lang", "lang", "zh_CN", "zh_CN");
        MessageService messageService = new MessageService(plugin, languageLoader, DEFAULT_PREFIX, false);
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
        PdcService pdcService = new PdcService("emaki");
        EmakiItemIdentifier identifier = new EmakiItemIdentifier(pdcService);
        PdcAttributeGateway pdcAttributeGateway = new PdcAttributeGateway(plugin);
        syncPdcAttributeRegistration(pdcAttributeGateway, PDC_ATTRIBUTE_SOURCE_ID);
        EmakiItemPdcWriter pdcWriter = new EmakiItemPdcWriter(identifier, pdcAttributeGateway, new SkillPdcGateway());
        EmakiItemFactory itemFactory = new EmakiItemFactory(itemLoader, pdcWriter);
        EmakiItemUpdateService updateService = new EmakiItemUpdateService(
                itemLoader,
                itemFactory,
                identifier,
                pdcAttributeGateway::copyPayloads
        );
        EmakiItemSetService setService = new EmakiItemSetService(
                itemLoader,
                setLoader,
                itemFactory,
                identifier,
                pdcWriter,
                updateService,
                new ItemSetLoreRenderer(),
                plugin::appConfig
        );
        DefaultEmakiItemApi itemApi = new DefaultEmakiItemApi(itemLoader, itemFactory, identifier);
        EmakiItemActionService actionService = new EmakiItemActionService(plugin, coreLibPlugin.actionExecutor());
        EmakiItemConditionChecker conditionChecker = new EmakiItemConditionChecker(plugin, coreLibPlugin.placeholderRegistry(), actionService);
        return new ItemRuntimeComponents(
                appConfigLoader,
                languageLoader,
                messageService,
                bootstrapService,
                itemLoader,
                setLoader,
                identifier,
                pdcWriter,
                itemFactory,
                updateService,
                setService,
                actionService,
                conditionChecker,
                itemApi,
                coreLibPlugin.itemSourceService(),
                pdcAttributeGateway,
                pdcService
        );
    }

    public void reload(EmakiItemPlugin plugin) {
        plugin.languageLoader().load();
        plugin.appConfigLoader().load();
        plugin.languageLoader().setLanguage(plugin.appConfig().language());
        syncPdcAttributeRegistration(plugin.pdcAttributeGateway(), PDC_ATTRIBUTE_SOURCE_ID);
        int loadedItems = plugin.itemLoader().load();
        int loadedSets = plugin.setLoader().load();
        plugin.itemFactory().clearCache();
        if (plugin.messageService() != null) {
            plugin.messageService().info("console.items_loaded", java.util.Map.of("count", loadedItems));
            plugin.messageService().info("console.sets_loaded", java.util.Map.of("count", loadedSets));
        }
    }

    /**
     * Asynchronous reload: file I/O stages run on the async thread pool,
     * final registration and cache clearing run on the main thread.
     */
    public CompletableFuture<Void> reloadAsync(EmakiItemPlugin plugin, Consumer<String> progressListener) {
        AsyncTaskScheduler scheduler = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class).asyncTaskScheduler();
        if (scheduler == null) {
            reload(plugin);
            return CompletableFuture.completedFuture(null);
        }

        notifyProgress(progressListener, "Loading configuration files...");

        // 异步阶段：文件 I/O
        return runReloadStageAsync(scheduler, new ReloadStageConfig<>(
                "item", "config-load", "Loading configs...", progressListener,
                () -> {
                    plugin.languageLoader().load();
                    plugin.appConfigLoader().load();
                    plugin.itemLoader().load();
                    plugin.setLoader().load();
                },
                null, (stage, ex) -> plugin.getLogger().warning("[Reload] Stage " + stage + " failed: " + ex.getMessage())
        )).thenCompose(_ -> {
            // 同步阶段：应用配置、刷新缓存
            notifyProgress(progressListener, "Applying configuration...");
            return scheduler.callSync("item-reload-apply", () -> {
                plugin.languageLoader().setLanguage(plugin.appConfig().language());
                syncPdcAttributeRegistration(plugin.pdcAttributeGateway(), PDC_ATTRIBUTE_SOURCE_ID);
                plugin.itemFactory().clearCache();
                if (plugin.messageService() != null) {
                    plugin.messageService().info("console.items_loaded", java.util.Map.of("count", plugin.itemLoader().all().size()));
                    plugin.messageService().info("console.sets_loaded", java.util.Map.of("count", plugin.setLoader().all().size()));
                }
                notifyProgress(progressListener, "Reload complete.");
                return null;
            });
        });
    }

    public void registerServices(EmakiItemPlugin plugin) {
        plugin.getServer().getServicesManager().register(
                EmakiItemApi.class,
                plugin.itemApi(),
                plugin,
                ServicePriority.Normal
        );
        plugin.itemSourceService().registerResolver(new EmakiItemSourceResolver(plugin.itemApi()));
    }

    public void shutdown(EmakiItemPlugin plugin) {
        if (plugin.messageService() != null) {
            plugin.messageService().info("console.plugin_stopping");
        }
        plugin.getServer().getServicesManager().unregister(EmakiItemApi.class, plugin.itemApi());
        if (plugin.itemSourceService() != null) {
            plugin.itemSourceService().unregisterResolver("emakiitem");
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
        return new AppConfig(
                configuration.getString("language", "zh_CN"),
                configuration.getString("version", "2.1.0"),
                configuration.getBoolean("release_default_data", true),
                parseSetBonus(configuration.getSection("set_bonus"))
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
}
