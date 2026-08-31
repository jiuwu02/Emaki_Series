package emaki.jiuwu.craft.codex;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
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
import emaki.jiuwu.craft.codex.codex.gui.CodexGuiService;
import emaki.jiuwu.craft.codex.codex.loader.CodexCategoryLoader;
import emaki.jiuwu.craft.codex.codex.persistence.CodexDataFile;
import emaki.jiuwu.craft.codex.codex.provider.CodexProviderRegistrar;
import emaki.jiuwu.craft.codex.codex.service.CodexEntryService;
import emaki.jiuwu.craft.codex.codex.service.PlayerCodexStore;
import emaki.jiuwu.craft.codex.config.AppConfig;
import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapHooks;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.bootstrap.ConfigKeyMigration;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigCommitGate;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.runtime.AbstractLifecycleCoordinator;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.AsyncYamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

final class CodexLifecycleCoordinator extends AbstractLifecycleCoordinator<EmakiCodexPlugin, CodexRuntimeComponents> {

    private static final String DEFAULT_PREFIX = "<gray>[ <gradient:#D946EF:#F59E0B>EmakiCodex</gradient> ]</gray>";
    private static final List<String> VERSIONED_FILES = List.of("config.yml", "lang/zh_CN.yml", "lang/en_US.yml");
    private static final List<String> STATIC_FILES = List.of("gui/codex_gui.yml");
    private static final List<String> DEFAULT_DATA_FILES = List.of(
            "advancements/example_page.yml", "codex/example_category.yml");
    private static final List<String> EXTRA_DIRECTORIES = List.of("data", "codex");
    private static final long SHUTDOWN_FLUSH_SECONDS = 10L;
    private static final List<ConfigKeyMigration.Rename> CONFIG_RENAMES = List.of(
            new ConfigKeyMigration.Rename("advancement.announce-default", "advancement.announce_default"),
            new ConfigKeyMigration.Rename("advancement.remove-on-disable", "advancement.remove_on_disable"),
            new ConfigKeyMigration.Rename("advancement.packet-coordinates", "advancement.packet_coordinates"),
            new ConfigKeyMigration.Rename("advancement.triggers-enabled", "advancement.triggers_enabled"));

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
                        AppConfig current = appConfigLoader.current();
                        return current == null || current.releaseDefaultData();
                    }

                    @Override
                    public void afterVersionedMerge(String relativePath, YamlSection runtime, YamlSection bundled) {
                        if (!"config.yml".equals(relativePath)) {
                            return;
                        }
                        ConfigKeyMigration.applyRenames(runtime, bundled, CONFIG_RENAMES, plugin.getLogger());
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

        GuiTemplateLoader guiTemplateLoader = new GuiTemplateLoader(plugin);
        GuiService guiService = new GuiService(plugin, executionDispatcher, coreLibPlugin.asyncTaskScheduler(),
                coreLibPlugin.performanceMonitor(), coreLibPlugin.guiBackend());

        CodexCategoryLoader codexCategoryLoader = new CodexCategoryLoader(plugin);
        AsyncYamlFiles codexFiles = coreLibPlugin.asyncYamlFiles(plugin);
        CodexDataFile codexDataFile = new CodexDataFile(plugin.getLogger(), plugin.dataPath("data"));
        PlayerCodexStore codexStore = new PlayerCodexStore(plugin.getLogger(), codexFiles, codexDataFile);
        CodexProviderRegistrar codexProviderRegistrar = new CodexProviderRegistrar(
                plugin, codexCategoryLoader, codexStore, executionDispatcher, threadOwnership);
        CodexEntryService codexEntryService = new CodexEntryService(
                plugin, codexCategoryLoader, codexStore, codexProviderRegistrar);
        CodexGuiService codexGuiService = new CodexGuiService(plugin, guiService, guiTemplateLoader,
                codexCategoryLoader, codexEntryService, messageService);

        CodexTriggerService triggerService = new CodexTriggerService(
                plugin, advancementPageLoader, advancementService, advancementTriggerRegistry,
                codexEntryService);

        return new CodexRuntimeComponents(
                appConfigLoader, languageLoader, messageService, bootstrapService,
                advancementPageLoader, platform, jsonBuilder, registrar, advancementService,
                advancementPacketGateway, advancementTriggerRegistry, triggerService,
                executionDispatcher, threadOwnership,
                guiService, guiTemplateLoader, codexCategoryLoader, codexStore,
                codexProviderRegistrar, codexEntryService, codexGuiService);
    }

    public void reload(EmakiCodexPlugin plugin) {

        ConfigCommitGate.Result gate = ConfigCommitGate.commit(
                plugin.messageService(),
                "codex",
                plugin.appConfigLoader()::current,
                () -> {
                    plugin.languageLoader().load();
                    AppConfig candidate = plugin.appConfigLoader().load();
                    plugin.advancementPageLoader().load();
                    plugin.codexCategoryLoader().load();
                    plugin.guiTemplateLoader().load();
                    return candidate;
                },
                plugin.appConfigLoader()::overrideCurrent);
        if (gate.rejected()) {

            return;
        }
        plugin.languageLoader().setLanguage(plugin.appConfig().language());

        AppConfig config = plugin.appConfig();

        plugin.messageService().info("console.codex_categories_loaded",
                Map.of("count", plugin.codexCategoryLoader().all().size()));
        plugin.codexProviderRegistrar().register();
        plugin.codexProviderRegistrar().resyncAll();

        if (config.advancementEnabled()) {
            int registered = plugin.advancementRegistrar().registerAll();
            plugin.messageService().info("console.advancements_registered", Map.of("count", registered));
            resyncAdvancements(plugin, registered);
        } else {
            plugin.advancementRegistrar().unregisterConfigured();
        }
    }

    private void resyncAdvancements(EmakiCodexPlugin plugin, int registered) {
        if (registered <= 0 || Bukkit.getOnlinePlayers().isEmpty()) {
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
        if (plugin.codexProviderRegistrar() != null) {
            plugin.codexProviderRegistrar().unregister();
        }
        if (plugin.codexStore() != null) {
            PlayerCodexStore.FlushResult flush =
                    plugin.codexStore().flushAndSeal(SHUTDOWN_FLUSH_SECONDS, TimeUnit.SECONDS);
            if (!flush.clean()) {
                plugin.getLogger().warning("Codex progress flush finished dirty: saved="
                        + flush.savedEntries() + " failed=" + flush.failedEntries()
                        + " remaining=" + flush.remainingDirtyEntries());
            }
        }
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
                advancement != null && legacyAwareBool(advancement, "announce_default", "announce-default", false),
                advancement == null || legacyAwareBool(advancement, "remove_on_disable", "remove-on-disable", true),
                advancement == null || legacyAwareBool(advancement, "packet_coordinates", "packet-coordinates", true),
                advancement == null || legacyAwareBool(advancement, "triggers_enabled", "triggers-enabled", true),
                bool(configuration, "op_bypass", false));
    }

    private boolean bool(YamlSection section, String path, boolean fallback) {
        Boolean value = section.getBoolean(path, fallback);
        return value == null ? fallback : value;
    }

    private boolean legacyAwareBool(YamlSection section, String path, String legacyPath, boolean fallback) {
        if (section.contains(path)) {
            return bool(section, path, fallback);
        }
        if (section.contains(legacyPath)) {
            boolean legacyValue = bool(section, legacyPath, fallback);
            JavaPlugin.getPlugin(EmakiCodexPlugin.class).getLogger().warning("配置键 advancement." + legacyPath
                    + " 已更名为 advancement." + path
                    + "，当前按旧键值 " + legacyValue
                    + " 生效，启动时会自动迁移到新键。");
            return legacyValue;
        }
        return fallback;
    }

}
