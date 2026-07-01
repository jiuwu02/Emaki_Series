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
import emaki.jiuwu.craft.codex.config.AppConfig;
import emaki.jiuwu.craft.codex.recipe.ItemRefFactory;
import emaki.jiuwu.craft.codex.recipe.RecipeCollector;
import emaki.jiuwu.craft.codex.recipe.RecipeIndex;
import emaki.jiuwu.craft.codex.recipe.RecipeVisibilityService;
import emaki.jiuwu.craft.codex.recipe.loader.ManualRecipeLoader;
import emaki.jiuwu.craft.codex.recipe.model.CodexRecipe;
import emaki.jiuwu.craft.codex.recipe.sync.RecipeSyncGateway;
import emaki.jiuwu.craft.codex.store.PlayerUnlockStore;
import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.async.FoliaSchedulerAdapter;
import emaki.jiuwu.craft.corelib.async.TaskHandle;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapHooks;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.runtime.AbstractLifecycleCoordinator;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

/**
 * Builds and reloads EmakiCodex's runtime services, mirroring the EmakiForge lifecycle
 * pattern. Recipe registry access is confined to the main thread; advancement
 * registration and player resync run on reload.
 */
final class CodexLifecycleCoordinator extends AbstractLifecycleCoordinator<EmakiCodexPlugin, CodexRuntimeComponents> {

    private static final String DEFAULT_PREFIX = "<gray>[ <gradient:#F59E0B:#EC4899>EmakiCodex</gradient> ]</gray>";
    private static final List<String> VERSIONED_FILES = List.of("config.yml", "lang/zh_CN.yml", "lang/en_US.yml");
    private static final List<String> STATIC_FILES = List.of();
    private static final List<String> DEFAULT_DATA_FILES = List.of("advancements/example_page.yml", "recipes/example_recipe.yml");
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

        PlayerUnlockStore unlockStore = new PlayerUnlockStore(plugin, coreLibPlugin::asyncYamlFiles);
        ManualRecipeLoader manualRecipeLoader = new ManualRecipeLoader(plugin);
        AdvancementPageLoader advancementPageLoader = new AdvancementPageLoader(plugin);

        AppConfig config = appConfigLoader.current();
        RecipeIndex recipeIndex = new RecipeIndex();
        ItemRefFactory itemRefFactory = new ItemRefFactory(coreLibPlugin.itemSourceService());
        RecipeCollector recipeCollector = new RecipeCollector(itemRefFactory);
        RecipeVisibilityService visibilityService = new RecipeVisibilityService(recipeIndex, unlockStore, config);
        RecipeSyncGateway syncGateway = new RecipeSyncGateway(plugin, recipeIndex, visibilityService, config);

        AdvancementPlatform platform = new UnsafeAdvancementPlatform(plugin.getLogger());
        AdvancementJsonBuilder jsonBuilder = new AdvancementJsonBuilder(coreLibPlugin.itemSourceService());
        AdvancementRegistrar registrar = new AdvancementRegistrar(plugin, advancementPageLoader, platform, jsonBuilder);
        AdvancementService advancementService = new AdvancementService(registrar);
        AdvancementPacketGateway advancementPacketGateway =
                new AdvancementPacketGateway(plugin, registrar, config.packetCoordinates());

        return new CodexRuntimeComponents(
                appConfigLoader, languageLoader, messageService, bootstrapService, unlockStore,
                manualRecipeLoader, advancementPageLoader, recipeIndex, recipeCollector, visibilityService,
                syncGateway, platform, jsonBuilder, registrar, advancementService, advancementPacketGateway);
    }

    /**
     * Reloads configs, rebuilds the recipe index from the vanilla registry, re-registers
     * advancements, and resyncs online players. Runs synchronously on the main thread.
     *
     * @param plugin the plugin
     * @param autoSaveTask the current auto-save task handle
     * @return the rescheduled auto-save task handle
     */
    public TaskHandle reload(EmakiCodexPlugin plugin, TaskHandle autoSaveTask) {
        plugin.languageLoader().load();
        plugin.appConfigLoader().load();
        plugin.languageLoader().setLanguage(plugin.appConfig().language());
        plugin.manualRecipeLoader().load();
        plugin.advancementPageLoader().load();
        plugin.unlockStore().load();

        AppConfig config = plugin.appConfig();
        plugin.recipeVisibilityService().updateConfig(config);

        if (config.recipeBridgeEnabled()) {
            rebuildRecipeIndex(plugin);
            plugin.recipeSyncGateway().rebuild(plugin.recipeIndex(), config);
        }

        if (config.advancementEnabled()) {
            int registered = plugin.advancementRegistrar().registerAll();
            plugin.messageService().info("console.advancements_registered", Map.of("count", registered));
        }

        if (config.recipeBridgeEnabled() && config.resyncOnReload()) {
            plugin.recipeSyncGateway().syncAll();
        }

        plugin.messageService().info("console.recipes_indexed", Map.of("count", plugin.recipeIndex().size()));
        return rescheduleAutoSave(plugin, autoSaveTask);
    }

    private void rebuildRecipeIndex(EmakiCodexPlugin plugin) {
        Map<String, CodexRecipe> collected = plugin.recipeCollector().collect();
        plugin.recipeIndex().replaceAll(collected);
        Map<String, CodexRecipe> manual = new java.util.LinkedHashMap<>();
        plugin.manualRecipeLoader().all().forEach(manual::put);
        plugin.recipeIndex().merge(manual);
    }

    public TaskHandle rescheduleAutoSave(EmakiCodexPlugin plugin, TaskHandle currentTask) {
        cancelAutoSave(currentTask);
        AppConfig config = plugin.appConfig();
        if (config.autoSaveIntervalSeconds() > 0) {
            return FoliaSchedulerAdapter.runTaskTimer(
                    plugin,
                    () -> plugin.unlockStore().saveAllAsync(),
                    config.autoSaveIntervalTicks(),
                    config.autoSaveIntervalTicks());
        }
        return null;
    }

    public TaskHandle cancelAutoSave(TaskHandle currentTask) {
        FoliaSchedulerAdapter.cancelTask(currentTask);
        return null;
    }

    public void shutdown(EmakiCodexPlugin plugin, TaskHandle autoSaveTask) {
        EmakiCoreLibPlugin coreLibPlugin = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
        coreLibPlugin.actionRegistry().unregisterAll(plugin);
        cancelAutoSave(autoSaveTask);

        if (plugin.appConfig() != null && plugin.appConfig().removeOnDisable() && plugin.advancementRegistrar() != null) {
            plugin.advancementRegistrar().unregisterAll();
        }
        if (plugin.advancementPacketGateway() != null) {
            plugin.advancementPacketGateway().shutdown();
        }
        if (plugin.recipeSyncGateway() != null) {
            plugin.recipeSyncGateway().shutdown();
        }
        if (plugin.unlockStore() != null && plugin.messageService() != null) {
            plugin.messageService().info("console.saving_unlock_data");
            try {
                int saved = plugin.unlockStore().saveAllAsync().join();
                plugin.unlockStore().waitForPendingSaves().join();
                plugin.messageService().info("console.unlock_data_saved", Map.of("count", saved));
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("[Codex] Failed to save unlock data on shutdown: " + exception.getMessage());
            }
        }
    }

    private AppConfig parseAppConfig(YamlSection configuration) {
        if (configuration == null || configuration.getKeys(false).isEmpty()) {
            return AppConfig.defaults();
        }
        YamlSection recipeBridge = configuration.getSection("recipe-bridge");
        YamlSection clientChannel = recipeBridge == null ? null : recipeBridge.getSection("client-channel");
        YamlSection advancement = configuration.getSection("advancement");
        YamlSection storage = configuration.getSection("storage");

        return new AppConfig(
                configuration.getString("language", "zh_CN"),
                configuration.getString("version", AppConfig.CURRENT_VERSION),
                bool(configuration, "release_default_data", true),
                recipeBridge == null || bool(recipeBridge, "enabled", true),
                recipeBridge == null || bool(recipeBridge, "default-unlock-all", true),
                recipeBridge == null || bool(recipeBridge, "sync-on-join", true),
                recipeBridge == null || bool(recipeBridge, "resync-on-reload", true),
                recipeBridge == null ? List.of() : recipeBridge.getStringList("global-blacklist"),
                recipeBridge == null ? List.of() : recipeBridge.getStringList("unlock-whitelist"),
                clientChannel == null || bool(clientChannel, "vanilla-book", true),
                clientChannel == null || bool(clientChannel, "packet-emulation", true),
                clientChannel != null && bool(clientChannel, "jei-message", false),
                advancement == null || bool(advancement, "enabled", true),
                advancement == null ? "unsafe" : advancement.getString("platform", "unsafe"),
                advancement != null && bool(advancement, "announce-default", false),
                advancement == null || bool(advancement, "remove-on-disable", true),
                advancement == null || bool(advancement, "packet-coordinates", true),
                storage == null ? 300 : intOf(storage, "auto-save-interval-seconds", 300),
                bool(configuration, "op_bypass", false));
    }

    private boolean bool(YamlSection section, String path, boolean fallback) {
        Boolean value = section.getBoolean(path, fallback);
        return value == null ? fallback : value;
    }

    private int intOf(YamlSection section, String path, int fallback) {
        Integer value = section.getInt(path, fallback);
        return value == null ? fallback : value;
    }

    private boolean shouldReleaseDefaultData(EmakiCodexPlugin plugin) {
        YamlSection configuration = YamlFiles.load(plugin.dataPath("config.yml").toFile());
        Boolean value = configuration.getBoolean("release_default_data", true);
        return value == null || value;
    }
}
