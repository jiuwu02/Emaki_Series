package emaki.jiuwu.craft.item;

import java.util.List;

import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
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
import emaki.jiuwu.craft.item.loader.EmakiItemLoader;
import emaki.jiuwu.craft.item.service.DefaultEmakiItemApi;
import emaki.jiuwu.craft.item.service.EmakiItemActionService;
import emaki.jiuwu.craft.item.service.EmakiItemConditionChecker;
import emaki.jiuwu.craft.item.service.EmakiItemFactory;
import emaki.jiuwu.craft.item.service.EmakiItemIdentifier;
import emaki.jiuwu.craft.item.service.EmakiItemPdcWriter;
import emaki.jiuwu.craft.item.service.EmakiItemSourceResolver;

final class ItemLifecycleCoordinator extends AbstractLifecycleCoordinator<EmakiItemPlugin, ItemRuntimeComponents> {

    private static final String DEFAULT_PREFIX = "<gray>[ <gradient:#EBD48A:#7FB08A>Emaki Item</gradient> ]</gray>";
    private static final String PDC_ATTRIBUTE_SOURCE_ID = "emakiitem";
    private static final List<String> VERSIONED_FILES = List.of("config.yml", "lang/zh_CN.yml", "lang/en_US.yml");
    private static final List<String> DEFAULT_DATA_FILES = List.of("items/example_blade.yml");
    private static final List<String> EXTRA_DIRECTORIES = List.of("items");

    @Override
    public ItemRuntimeComponents initialize(EmakiItemPlugin plugin) {
        EmakiCoreLibPlugin coreLibPlugin = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
        YamlConfigLoader<AppConfig> appConfigLoader = new YamlConfigLoader<>(
                plugin,
                "config.yml",
                "version",
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
                List.of(),
                new BootstrapHooks() {
                    @Override
                    public boolean shouldInstallDefaultData() {
                        return shouldReleaseDefaultData(plugin);
                    }
                }
        );
        EmakiItemLoader itemLoader = new EmakiItemLoader(plugin);
        PdcService pdcService = new PdcService("emaki");
        EmakiItemIdentifier identifier = new EmakiItemIdentifier(pdcService);
        PdcAttributeGateway pdcAttributeGateway = new PdcAttributeGateway(plugin);
        syncPdcAttributeRegistration(pdcAttributeGateway, PDC_ATTRIBUTE_SOURCE_ID);
        EmakiItemPdcWriter pdcWriter = new EmakiItemPdcWriter(identifier, pdcAttributeGateway, new SkillPdcGateway());
        EmakiItemFactory itemFactory = new EmakiItemFactory(itemLoader, pdcWriter);
        DefaultEmakiItemApi itemApi = new DefaultEmakiItemApi(itemLoader, itemFactory, identifier);
        EmakiItemActionService actionService = new EmakiItemActionService(plugin, coreLibPlugin.actionExecutor());
        EmakiItemConditionChecker conditionChecker = new EmakiItemConditionChecker(plugin, coreLibPlugin.placeholderRegistry(), actionService);
        return new ItemRuntimeComponents(
                appConfigLoader,
                languageLoader,
                messageService,
                bootstrapService,
                itemLoader,
                identifier,
                pdcWriter,
                itemFactory,
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
        plugin.itemFactory().clearCache();
        if (plugin.messageService() != null) {
            plugin.messageService().info("console.items_loaded", java.util.Map.of("count", loadedItems));
        }
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
                configuration.getString("version", "1.0.0"),
                configuration.getBoolean("release_default_data", true)
        );
    }

    private boolean shouldReleaseDefaultData(EmakiItemPlugin plugin) {
        YamlSection configuration = YamlFiles.load(plugin.dataPath("config.yml").toFile());
        return configuration.getBoolean("release_default_data", true);
    }
}
