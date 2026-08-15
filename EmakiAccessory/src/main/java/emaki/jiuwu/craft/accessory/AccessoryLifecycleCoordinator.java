package emaki.jiuwu.craft.accessory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.accessory.config.AppConfig;
import emaki.jiuwu.craft.accessory.config.AppConfigParser;
import emaki.jiuwu.craft.accessory.gui.AccessoryGuiService;
import emaki.jiuwu.craft.accessory.loader.AccessoryPartLoader;
import emaki.jiuwu.craft.accessory.loader.AccessorySetLoader;
import emaki.jiuwu.craft.accessory.persistence.AccessoryDataFile;
import emaki.jiuwu.craft.accessory.provider.AccessoryProviderRegistrar;
import emaki.jiuwu.craft.accessory.service.AccessoryAdminService;
import emaki.jiuwu.craft.accessory.service.AccessoryContributionService;
import emaki.jiuwu.craft.accessory.service.AccessoryPartRegistry;
import emaki.jiuwu.craft.accessory.service.AccessorySetService;
import emaki.jiuwu.craft.accessory.service.AccessoryUniqueService;
import emaki.jiuwu.craft.accessory.service.AccessoryWriteSessionRegistry;
import emaki.jiuwu.craft.accessory.service.PlayerAccessoryStore;
import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapHooks;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.runtime.AbstractLifecycleCoordinator;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.AsyncYamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;

final class AccessoryLifecycleCoordinator
        extends AbstractLifecycleCoordinator<EmakiAccessoryPlugin, AccessoryRuntimeComponents> {

    private static final String DEFAULT_PREFIX =
            "<gray>[ <gradient:#F472B6:#C084FC>EmakiAccessory</gradient> ]</gray> ";
    private static final List<String> VERSIONED_FILES =
            List.of("config.yml", "lang/zh_CN.yml", "lang/en_US.yml");
    private static final List<String> STATIC_FILES = List.of("gui/accessory_gui.yml");
    private static final List<String> DEFAULT_DATA_FILES = List.of("parts.yml", "sets/example_set.yml");
    private static final List<String> EXTRA_DIRECTORIES = List.of("gui", "sets", "data");

    @Override
    public AccessoryRuntimeComponents initialize(EmakiAccessoryPlugin plugin) {
        EmakiCoreLibPlugin coreLibPlugin = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);

        YamlConfigLoader<AppConfig> appConfigLoader = new YamlConfigLoader<>(
                plugin, "config.yml", AppConfig::defaults, AppConfigParser::parse);
        appConfigLoader.load();
        AppConfig appConfig = appConfigLoader.current();

        LanguageLoader languageLoader = new LanguageLoader(plugin, "lang", "lang", "zh_CN", "zh_CN");
        MessageService messageService = new MessageService(plugin, languageLoader, DEFAULT_PREFIX, true);
        languageLoader.load();

        BootstrapService bootstrapService = new BootstrapService(
                plugin,
                messageService,
                VERSIONED_FILES,
                STATIC_FILES,
                DEFAULT_DATA_FILES,
                EXTRA_DIRECTORIES,
                new BootstrapHooks() {
                    @Override
                    public boolean shouldInstallDefaultData() {
                        AppConfig current = appConfigLoader.current();
                        return current == null || current.releaseDefaultData();
                    }
                });

        ExecutionDispatcher executionDispatcher = coreLibPlugin.executionDispatcher();
        ThreadOwnership threadOwnership = coreLibPlugin.threadOwnership();

        GuiService guiService = new GuiService(plugin, executionDispatcher,
                coreLibPlugin.asyncTaskScheduler(), coreLibPlugin.performanceMonitor(),
                coreLibPlugin.guiBackend());
        GuiTemplateLoader guiTemplateLoader = new GuiTemplateLoader(plugin);

        AccessoryPartLoader partLoader = new AccessoryPartLoader(plugin, messageService);
        AccessorySetLoader setLoader = new AccessorySetLoader(plugin);

        AsyncYamlFiles accessoryFiles = coreLibPlugin.asyncYamlFiles(plugin);
        AccessoryDataFile dataFile = new AccessoryDataFile(
                plugin.getLogger(), plugin.dataPath("data"));
        PlayerAccessoryStore accessoryStore = new PlayerAccessoryStore(
                plugin.getLogger(), accessoryFiles, dataFile);

        AccessoryUniqueService uniqueService = new AccessoryUniqueService(appConfig.unique());
        AccessorySetService setService = new AccessorySetService();
        AccessoryContributionService contributionService = new AccessoryContributionService(setService,
                plugin::debugLogger);
        AccessoryGuiService accessoryGuiService = new AccessoryGuiService(guiService, messageService);
        AccessoryWriteSessionRegistry writeSessions = new AccessoryWriteSessionRegistry();
        AccessoryAdminService adminService = new AccessoryAdminService(plugin.getLogger(), accessoryStore);
        AccessoryProviderRegistrar providerRegistrar = new AccessoryProviderRegistrar(
                plugin, contributionService, plugin.getLogger());

        return new AccessoryRuntimeComponents(
                appConfigLoader,
                languageLoader,
                messageService,
                bootstrapService,
                executionDispatcher,
                threadOwnership,
                guiService,
                guiTemplateLoader,
                partLoader,
                setLoader,
                uniqueService,
                accessoryStore,
                setService,
                contributionService,
                accessoryGuiService,
                writeSessions,
                adminService,
                providerRegistrar);
    }

    int reload(EmakiAccessoryPlugin plugin) {
        plugin.appConfigLoader().load();
        plugin.languageLoader().load();
        plugin.languageLoader().setLanguage(plugin.appConfig().language());
        plugin.partLoader().load();
        plugin.guiTemplateLoader().load();
        plugin.setLoader().load();

        Map<String, String> rejected = new LinkedHashMap<>();
        AccessoryPartRegistry registry = AccessoryPartRegistry.of(plugin.partLoader().parts(), rejected);
        rejected.forEach((partId, collidingId) -> plugin.messageService().warning(
                "accessory.part_instance_conflict",
                Map.of("part", partId, "slot", collidingId)));

        plugin.uniqueService().reconfigure(plugin.appConfig().unique());
        plugin.setService().reconfigure(plugin.setLoader().all());
        plugin.contributionService().reconfigure(registry);
        plugin.contributionService().invalidateAll();
        plugin.accessoryGuiService().reconfigure(plugin.guiTemplateLoader(), registry);
        plugin.partRegistry(registry);
        return registry.slotCount();
    }
}
