package emaki.jiuwu.craft.forge;

import java.util.Map;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.forge.script.ScriptForgeModuleApi;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.async.FoliaSchedulerAdapter;
import emaki.jiuwu.craft.corelib.async.TaskHandle;
import emaki.jiuwu.craft.corelib.assembly.EmakiNamespaceDefinition;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapHooks;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.condition.ConditionBlock;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.integration.PdcAttributeGateway;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.runtime.AbstractLifecycleCoordinator;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.forge.config.AppConfig;
import emaki.jiuwu.craft.forge.loader.PlayerDataStore;
import emaki.jiuwu.craft.forge.loader.RecipeLoader;
import emaki.jiuwu.craft.forge.service.ForgeItemRefreshService;
import emaki.jiuwu.craft.forge.service.ForgeGuiService;
import emaki.jiuwu.craft.forge.service.ForgeService;
import emaki.jiuwu.craft.forge.service.ItemIdentifierService;
import emaki.jiuwu.craft.forge.service.RecipeBookGuiService;

final class ForgeLifecycleCoordinator extends AbstractLifecycleCoordinator<EmakiForgePlugin, ForgeRuntimeComponents> {

    private static final String DEFAULT_PREFIX = "<gray>[ <gradient:#A78BFA:#60A5FA>EmakiForge</gradient> ]</gray>";
    private static final String PDC_ATTRIBUTE_SOURCE_ID = "forge";
    private static final List<String> VERSIONED_FILES = List.of("config.yml", "lang/zh_CN.yml", "lang/en_US.yml");
    private static final List<String> STATIC_FILES = List.of("gui/forge_gui.yml", "gui/recipe_book.yml");
    private static final List<String> DEFAULT_DATA_FILES = List.of("recipes/example_recipe.yml");
    private static final List<String> EXTRA_DIRECTORIES = List.of("data");

    @Override
    public ForgeRuntimeComponents initialize(EmakiForgePlugin plugin) {
        EmakiCoreLibPlugin coreLibPlugin = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
        registerAssemblyLayer(coreLibPlugin);
        registerScriptModule(coreLibPlugin);
        releaseBundledScripts(coreLibPlugin, plugin);
        YamlConfigLoader<AppConfig> appConfigLoader = new YamlConfigLoader<>(
                plugin,
                "config.yml",
                AppConfig::defaults,
                this::parseAppConfig
        );
        appConfigLoader.load();
        LanguageLoader languageLoader = new LanguageLoader(plugin, "lang", "lang", "zh_CN", "zh_CN");
        RecipeLoader recipeLoader = new RecipeLoader(plugin, coreLibPlugin::actionRegistry, coreLibPlugin::actionTemplateRegistry);
        GuiTemplateLoader guiTemplateLoader = new GuiTemplateLoader(plugin);
        PlayerDataStore playerDataStore = new PlayerDataStore(plugin, coreLibPlugin::asyncYamlFiles);
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
                        return shouldReleaseDefaultData(plugin);
                    }
                }
        );
        GuiService guiService = new GuiService(plugin, coreLibPlugin.asyncTaskScheduler(), coreLibPlugin.performanceMonitor(), coreLibPlugin.guiBackend());
        ItemIdentifierService itemIdentifierService = new ItemIdentifierService(plugin, coreLibPlugin.itemSourceService());
        PdcAttributeGateway pdcAttributeGateway = new PdcAttributeGateway(plugin);
        syncPdcAttributeRegistration(pdcAttributeGateway, PDC_ATTRIBUTE_SOURCE_ID);
        ForgeService forgeService = new ForgeService(
                plugin,
                coreLibPlugin.asyncTaskScheduler(),
                coreLibPlugin.performanceMonitor(),
                coreLibPlugin.itemAssemblyService(),
                coreLibPlugin::actionExecutor
        );
        ForgeItemRefreshService itemRefreshService = new ForgeItemRefreshService(
                plugin,
                coreLibPlugin.itemAssemblyService()
        );
        ForgeGuiService forgeGuiService = new ForgeGuiService(plugin, guiService);
        RecipeBookGuiService recipeBookGuiService = new RecipeBookGuiService(plugin, guiService);
        return new ForgeRuntimeComponents(
                appConfigLoader,
                languageLoader,
                recipeLoader,
                guiTemplateLoader,
                playerDataStore,
                messageService,
                bootstrapService,
                guiService,
                itemIdentifierService,
                pdcAttributeGateway,
                itemRefreshService,
                forgeService,
                forgeGuiService,
                recipeBookGuiService
        );
    }

    public TaskHandle reload(EmakiForgePlugin plugin, TaskHandle currentTask, boolean closeOpenInventories) {
        if (closeOpenInventories) {
            closeOpenInventories(plugin);
        }
        plugin.languageLoader().load();
        plugin.appConfigLoader().load();
        plugin.languageLoader().setLanguage(plugin.appConfig().language());
        plugin.recipeLoader().load();
        plugin.guiTemplateLoader().load();
        plugin.playerDataStore().load();
        syncPdcAttributeRegistration(plugin.pdcAttributeGateway(), PDC_ATTRIBUTE_SOURCE_ID);
        plugin.messageService().info("console.pdc_source_registered", Map.of("source", PDC_ATTRIBUTE_SOURCE_ID));
        plugin.itemIdentifierService().refresh();
        plugin.forgeService().refreshIndexes();
        validateConfiguredExternalSources(plugin);
        if (plugin.itemRefreshService() != null) {
            plugin.itemRefreshService().refreshOnlinePlayers();
        }
        plugin.messageService().info("console.recipes_loaded", Map.of(
                "count", String.valueOf(plugin.recipeLoader().all().size())
        ));
        return rescheduleAutoSave(plugin, currentTask);
    }

    public CompletableFuture<TaskHandle> reloadAsync(EmakiForgePlugin plugin, TaskHandle currentTask,
            boolean closeOpenInventories, Consumer<String> progressListener) {
        AsyncTaskScheduler scheduler = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class).asyncTaskScheduler();
        if (closeOpenInventories) {
            closeOpenInventories(plugin);
        }

        return runReloadPipelineAsync(scheduler, new ReloadPipelineConfig<TaskHandle, TaskHandle>(
                "forge",
                "config-load",
                "Loading configs...",
                () -> {
                    plugin.languageLoader().load();
                    plugin.appConfigLoader().load();
                    plugin.recipeLoader().load();
                    plugin.guiTemplateLoader().load();
                    plugin.playerDataStore().load();
                    return currentTask;
                },
                "apply",
                "Applying configuration...",
                passedTask -> {
                    plugin.languageLoader().setLanguage(plugin.appConfig().language());
                    syncPdcAttributeRegistration(plugin.pdcAttributeGateway(), PDC_ATTRIBUTE_SOURCE_ID);
                    plugin.messageService().info("console.pdc_source_registered", Map.of("source", PDC_ATTRIBUTE_SOURCE_ID));
                    plugin.itemIdentifierService().refresh();
                    plugin.forgeService().refreshIndexes();
                    validateConfiguredExternalSources(plugin);
                    if (plugin.itemRefreshService() != null) {
                        plugin.itemRefreshService().refreshOnlinePlayers();
                    }
                    plugin.messageService().info("console.recipes_loaded", Map.of(
                            "count", String.valueOf(plugin.recipeLoader().all().size())
                    ));
                    notifyProgress(progressListener, "Reload complete.");
                    return rescheduleAutoSave(plugin, passedTask);
                },
                null,
                null,
                null,
                (stage, ex) -> plugin.getLogger().warning("[Reload] Stage " + stage + " failed: " + ex.getMessage()),
                progressListener
        ));
    }

    public TaskHandle rescheduleAutoSave(EmakiForgePlugin plugin, TaskHandle currentTask) {
        TaskHandle nextTask = cancelAutoSave(currentTask);
        AppConfig config = plugin.appConfig();
        if (config.historyEnabled() && config.historyAutoSave()) {
            nextTask = FoliaSchedulerAdapter.runTaskTimer(
                    plugin,
                    () -> plugin.playerDataStore().saveAllAsync(),
                    config.historySaveInterval(),
                    config.historySaveInterval()
            );
        }
        return nextTask;
    }

    public TaskHandle cancelAutoSave(TaskHandle currentTask) {
        FoliaSchedulerAdapter.cancelTask(currentTask);
        return null;
    }

    public void shutdown(EmakiForgePlugin plugin, TaskHandle autoSaveTask) {
        EmakiCoreLibPlugin coreLibPlugin = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
        coreLibPlugin.namespaceRegistry().unregister("forge");
        coreLibPlugin.javaScriptRegistrationTracker().unregisterOwner(plugin);
        coreLibPlugin.scriptModuleRegistry().unregister("forge");
        if (plugin.messageService() != null) {
            plugin.messageService().info("console.plugin_stopping");
        }
        cancelAutoSave(autoSaveTask);
        if (plugin.pdcAttributeGateway() != null) {
            plugin.pdcAttributeGateway().shutdown();
        }
        if (plugin.playerDataStore() != null && plugin.messageService() != null) {
            plugin.messageService().info("console.saving_player_data");
            try {
                int saved = plugin.playerDataStore().saveAllAsync().join();
                plugin.playerDataStore().waitForPendingSaves().join();
                plugin.messageService().info("console.player_data_saved", Map.of("count", saved));
            } catch (RuntimeException exception) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "[Shutdown] Failed to save player data: " + exception.getMessage(), exception);
            }
        }
        if (plugin.forgeGuiService() != null) {
            plugin.forgeGuiService().clearAllSessions();
        }
        if (plugin.recipeBookGuiService() != null) {
            plugin.recipeBookGuiService().clearAllBooks();
        }
        if (plugin.messageService() != null) {
            plugin.messageService().info("console.plugin_stopped");
        }
    }

    private void closeOpenInventories(EmakiForgePlugin plugin) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.forgeGuiService().getSession(player) != null
                    || plugin.recipeBookGuiService().isRecipeBookInventory(player)) {
                player.closeInventory();
            }
        }
        plugin.forgeGuiService().clearAllSessions();
        plugin.recipeBookGuiService().clearAllBooks();
    }

    private void validateConfiguredExternalSources(EmakiForgePlugin plugin) {
        for (var entry : plugin.recipeLoader().all().entrySet()) {
            validateSource(plugin, entry.getValue().configuredOutputSource(), "recipe:" + entry.getKey() + ".result.success.outputs[0].item_sources");
        }
        for (Map.Entry<String, GuiTemplate> entry : plugin.guiTemplateLoader().all().entrySet()) {
            for (GuiSlot slot : entry.getValue().slots().values()) {
                ItemSource source = ItemSourceUtil.parse(slot.item());
                if (source != null) {
                    validateSource(plugin, source, "gui:" + entry.getKey() + ".slots." + slot.key() + ".item");
                }
            }
        }
    }

    private void validateSource(EmakiForgePlugin plugin, ItemSource source, String location) {
        if (source != null) {
            plugin.itemIdentifierService().validateConfiguredSource(source, location);
        }
    }

    private AppConfig parseAppConfig(YamlSection configuration) {
        if (configuration == null || configuration.getKeys(false).isEmpty()) {
            return AppConfig.defaults();
        }
        YamlSection permission = configuration.getSection("permission");
        YamlSection condition = configuration.getSection("condition");
        YamlSection history = configuration.getSection("history");
        YamlSection numberFormat = configuration.getSection("number_format");
        return new AppConfig(
                configuration.getString("language", "zh_CN"),
                configuration.getString("version", AppConfig.defaults().configVersion()),
                configuration.getBoolean("release_default_data", true),
                emaki.jiuwu.craft.forge.model.QualitySettings.fromConfig(configuration.get("quality")),
                numberFormat == null ? "0.##" : numberFormat.getString("default", "0.##"),
                numberFormat == null ? "0" : numberFormat.getString("integer", "0"),
                numberFormat == null ? "0.##%" : numberFormat.getString("percentage", "0.##%"),
                permission != null && permission.getBoolean("op_bypass", false),
                ConditionBlock.fromConfig(condition, true, false).invalidAsFailure(),
                history == null || history.getBoolean("enabled", true),
                history == null || history.getBoolean("auto_save", true),
                history == null ? 6000 : Numbers.tryParseInt(history.get("save_interval"), 6000)
        );
    }

    private boolean shouldReleaseDefaultData(EmakiForgePlugin plugin) {
        YamlSection configuration = YamlFiles.load(plugin.dataPath("config.yml").toFile());
        return configuration.getBoolean("release_default_data", true);
    }

    private void registerAssemblyLayer(EmakiCoreLibPlugin coreLibPlugin) {
        coreLibPlugin.namespaceRegistry().register(new EmakiNamespaceDefinition("forge", 100, "Forge"));
    }

    private void registerScriptModule(EmakiCoreLibPlugin coreLibPlugin) {
        coreLibPlugin.scriptModuleRegistry().register("forge", context -> new ScriptForgeModuleApi(JavaPlugin.getPlugin(EmakiForgePlugin.class), context));
    }

    private void releaseBundledScripts(EmakiCoreLibPlugin coreLibPlugin, EmakiForgePlugin plugin) {
        coreLibPlugin.releaseBundledScripts(plugin, "examples", false, List.of("forge_success.js"));
    }

}
