package emaki.jiuwu.craft.codex;

import java.util.List;
import java.util.Map;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.codex.advancement.AdvancementJsonBuilder;
import emaki.jiuwu.craft.codex.advancement.AdvancementPlatform;
import emaki.jiuwu.craft.codex.advancement.AdvancementRegistrar;
import emaki.jiuwu.craft.codex.advancement.AdvancementService;
import emaki.jiuwu.craft.codex.advancement.UnsafeAdvancementPlatform;
import emaki.jiuwu.craft.codex.advancement.loader.AdvancementPageLoader;
import emaki.jiuwu.craft.codex.advancement.packet.AdvancementPacketGateway;
import emaki.jiuwu.craft.codex.advancement.trigger.AdvancementTriggerRegistry;
import emaki.jiuwu.craft.codex.advancement.trigger.CodexTriggerService;
import emaki.jiuwu.craft.codex.config.AppConfig;
import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapHooks;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.runtime.AbstractLifecycleCoordinator;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;





final class CodexLifecycleCoordinator extends AbstractLifecycleCoordinator<EmakiCodexPlugin, CodexRuntimeComponents> {

    private static final String DEFAULT_PREFIX = "<gray>[ <gradient:#F59E0B:#EC4899>EmakiCodex</gradient> ]</gray>";
    private static final List<String> VERSIONED_FILES = List.of("config.yml", "lang/zh_CN.yml", "lang/en_US.yml");
    private static final List<String> STATIC_FILES = List.of();
    private static final List<String> DEFAULT_DATA_FILES = List.of("advancements/example_page.yml");
    private static final List<String> EXTRA_DIRECTORIES = List.of("data");

    @Override
    public CodexRuntimeComponents initialize(EmakiCodexPlugin plugin) {
        EmakiCoreLibPlugin coreLibPlugin = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);

        YamlConfigLoader<AppConfig> appConfigLoader = new YamlConfigLoader<>(
                plugin, "config.yml", AppConfig::defaults, this::parseAppConfig);
        appConfigLoader.load();

        LanguageLoader languageLoader = new LanguageLoader(plugin, "lang", "lang", "zh_CN", "zh_CN");
        MessageService messageService = new MessageService(plugin, languageLoader, DEFAULT_PREFIX, true);
        languageLoader.load();

        BootstrapService bootstrapService = new BootstrapService(
                plugin, messageService, VERSIONED_FILES, STATIC_FILES, DEFAULT_DATA_FILES, EXTRA_DIRECTORIES,
                new BootstrapHooks() {
                    @Override
                    public boolean shouldInstallDefaultData() {
                        return shouldReleaseDefaultData(plugin);
                    }
                });

        AdvancementPageLoader advancementPageLoader = new AdvancementPageLoader(plugin);

        AppConfig config = appConfigLoader.current();
        AdvancementPlatform platform = new UnsafeAdvancementPlatform(plugin.getLogger());
        AdvancementJsonBuilder jsonBuilder = new AdvancementJsonBuilder(coreLibPlugin.itemSourceService());
        AdvancementRegistrar registrar = new AdvancementRegistrar(plugin, advancementPageLoader, platform, jsonBuilder);
        AdvancementService advancementService = new AdvancementService(plugin, registrar);
        var executionDispatcher = coreLibPlugin.executionDispatcher();
        var threadOwnership = coreLibPlugin.threadOwnership();
        AdvancementPacketGateway advancementPacketGateway =
                new AdvancementPacketGateway(plugin, registrar, coreLibPlugin.itemSourceService(),
                        config.packetCoordinates(), executionDispatcher, threadOwnership);
        AdvancementTriggerRegistry advancementTriggerRegistry = new AdvancementTriggerRegistry(plugin);
        CodexTriggerService triggerService = new CodexTriggerService(
                plugin, advancementPageLoader, advancementService, advancementTriggerRegistry);

        return new CodexRuntimeComponents(
                appConfigLoader, languageLoader, messageService, bootstrapService,
                advancementPageLoader, platform, jsonBuilder, registrar, advancementService,
                advancementPacketGateway, advancementTriggerRegistry, triggerService,
                executionDispatcher, threadOwnership);
    }







    public void reload(EmakiCodexPlugin plugin) {
        plugin.languageLoader().load();
        plugin.appConfigLoader().load();
        plugin.languageLoader().setLanguage(plugin.appConfig().language());
        plugin.advancementPageLoader().load();

        AppConfig config = plugin.appConfig();

        if (config.advancementEnabled()) {
            int registered = plugin.advancementRegistrar().registerAll();
            plugin.messageService().info("console.advancements_registered", Map.of("count", registered));
            resyncAdvancements(plugin, registered);
        } else {
            plugin.advancementRegistrar().unregisterConfigured();
        }
    }











    private void resyncAdvancements(EmakiCodexPlugin plugin, int registered) {
        if (registered <= 0 || org.bukkit.Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }
        plugin.advancementPacketGateway().resyncAll().thenAccept(result -> {
            if (result < 0) {
                plugin.messageService().info("console.advancements_need_relog");
            } else if (result > 0) {
                plugin.messageService().info("console.advancements_resynced", Map.of("count", result));
            }
        });
    }

    public void shutdown(EmakiCodexPlugin plugin) {
        if (plugin.stageRegistrar() != null) {
            plugin.stageRegistrar().unregister();
        }
        EmakiCoreLibPlugin coreLibPlugin = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
        coreLibPlugin.actionRegistry().unregisterAll(plugin);

        if (plugin.advancementRegistrar() != null) {
            boolean removeConfigured = plugin.appConfig() != null && plugin.appConfig().removeOnDisable();
            plugin.advancementRegistrar().shutdown(removeConfigured);
        }
        if (plugin.advancementTriggerRegistry() != null) {
            plugin.advancementTriggerRegistry().close();
        }
        if (plugin.advancementPacketGateway() != null) {
            plugin.advancementPacketGateway().shutdown();
        }
    }

    private AppConfig parseAppConfig(YamlSection configuration) {
        if (configuration == null || configuration.getKeys(false).isEmpty()) {
            return AppConfig.defaults();
        }
        YamlSection advancement = configuration.getSection("advancement");

        return new AppConfig(
                configuration.getString("language", "zh_CN"),
                configuration.getString("version", AppConfig.CURRENT_VERSION),
                bool(configuration, "release_default_data", true),
                advancement == null || bool(advancement, "enabled", true),
                advancement == null ? "unsafe" : advancement.getString("platform", "unsafe"),
                advancement != null && bool(advancement, "announce-default", false),
                advancement == null || bool(advancement, "remove-on-disable", true),
                advancement == null || bool(advancement, "packet-coordinates", true),
                advancement == null || bool(advancement, "triggers-enabled", true),
                bool(configuration, "op_bypass", false));
    }

    private boolean bool(YamlSection section, String path, boolean fallback) {
        Boolean value = section.getBoolean(path, fallback);
        return value == null ? fallback : value;
    }

    private boolean shouldReleaseDefaultData(EmakiCodexPlugin plugin) {
        YamlSection configuration = YamlFiles.load(plugin.dataPath("config.yml").toFile());
        Boolean value = configuration.getBoolean("release_default_data", true);
        return value == null || value;
    }
}
