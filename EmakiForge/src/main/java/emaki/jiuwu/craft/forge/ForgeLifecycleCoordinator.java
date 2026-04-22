package emaki.jiuwu.craft.forge;

import java.util.Map;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapHooks;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.integration.ReflectivePdcAttributeGateway;
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
import emaki.jiuwu.craft.forge.loader.BlueprintLoader;
import emaki.jiuwu.craft.forge.loader.MaterialLoader;
import emaki.jiuwu.craft.forge.loader.PlayerDataStore;
import emaki.jiuwu.craft.forge.loader.RecipeLoader;
import emaki.jiuwu.craft.forge.service.EditorGuiService;
import emaki.jiuwu.craft.forge.service.ForgeItemRefreshService;
import emaki.jiuwu.craft.forge.service.ForgeGuiService;
import emaki.jiuwu.craft.forge.service.ForgeService;
import emaki.jiuwu.craft.forge.service.ItemIdentifierService;
import emaki.jiuwu.craft.forge.service.RecipeBookGuiService;

final class ForgeLifecycleCoordinator extends AbstractLifecycleCoordinator<EmakiForgePlugin, ForgeRuntimeComponents> {

    private static final String DEFAULT_PREFIX = "<gray>[ <gradient:#F2C46D:#C9703D>Emaki Forge</gradient> ]</gray>";
    private static final String PDC_ATTRIBUTE_SOURCE_ID = "forge";
    private static final List<String> VERSIONED_FILES = List.of("config.yml", "lang/zh_CN.yml");
    private static final List<String> STATIC_FILES = List.of("gui/forge_gui.yml", "gui/recipe_book.yml", "gui/editor_gui.yml");
    private static final List<String> DEFAULT_DATA_FILES = List.of("recipes/flame_sword.yml");
    private static final List<String> EXTRA_DIRECTORIES = List.of("data");

    @Override
    public ForgeRuntimeComponents initialize(EmakiForgePlugin plugin) {
        EmakiCoreLibPlugin coreLibPlugin = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
        YamlConfigLoader<AppConfig> appConfigLoader = new YamlConfigLoader<>(
                plugin,
                "config.yml",
                "config_version",
                AppConfig::defaults,
                this::parseAppConfig
        );
        appConfigLoader.load();
        LanguageLoader languageLoader = new LanguageLoader(plugin, "lang", "lang", "zh_CN", "zh_CN");
        BlueprintLoader blueprintLoader = new BlueprintLoader(plugin);
        MaterialLoader materialLoader = new MaterialLoader(plugin);
        RecipeLoader recipeLoader = new RecipeLoader(plugin, coreLibPlugin::actionRegistry, coreLibPlugin::actionTemplateRegistry);
        GuiTemplateLoader guiTemplateLoader = new GuiTemplateLoader(plugin);
        PlayerDataStore playerDataStore = new PlayerDataStore(plugin, coreLibPlugin::asyncYamlFiles);
        MessageService messageService = new MessageService(plugin, languageLoader, DEFAULT_PREFIX, false);
        languageLoader.load();
        BootstrapService bootstrapService = new BootstrapService(
                plugin,
                messageService,
                VERSIONED_FILES,
                STATIC_FILES,
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
        GuiService guiService = new GuiService(plugin, coreLibPlugin.asyncTaskScheduler(), coreLibPlugin.performanceMonitor());
        ItemIdentifierService itemIdentifierService = new ItemIdentifierService(plugin, coreLibPlugin.itemSourceService());
        ReflectivePdcAttributeGateway pdcAttributeGateway = new ReflectivePdcAttributeGateway(plugin);
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
        EditorGuiService editorGuiService = new EditorGuiService(plugin, guiService);
        return new ForgeRuntimeComponents(
                appConfigLoader,
                languageLoader,
                blueprintLoader,
                materialLoader,
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
                recipeBookGuiService,
                editorGuiService
        );
    }

    public BukkitTask reload(EmakiForgePlugin plugin, BukkitTask currentTask, boolean closeOpenInventories) {
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
        plugin.itemIdentifierService().refresh();
        plugin.forgeService().refreshIndexes();
        validateConfiguredExternalSources(plugin);
        if (plugin.itemRefreshService() != null) {
            plugin.itemRefreshService().refreshOnlinePlayers();
        }
        return rescheduleAutoSave(plugin, currentTask);
    }

    public BukkitTask rescheduleAutoSave(EmakiForgePlugin plugin, BukkitTask currentTask) {
        BukkitTask nextTask = cancelAutoSave(currentTask);
        AppConfig config = plugin.appConfig();
        if (config.historyEnabled() && config.historyAutoSave()) {
            nextTask = plugin.getServer().getScheduler().runTaskTimer(
                    plugin,
                    () -> plugin.playerDataStore().saveAllAsync(),
                    config.historySaveInterval(),
                    config.historySaveInterval()
            );
        }
        return nextTask;
    }

    public BukkitTask cancelAutoSave(BukkitTask currentTask) {
        if (currentTask != null) {
            currentTask.cancel();
        }
        return null;
    }

    public void shutdown(EmakiForgePlugin plugin, BukkitTask autoSaveTask) {
        if (plugin.messageService() != null) {
            plugin.messageService().info("console.plugin_stopping");
        }
        cancelAutoSave(autoSaveTask);
        if (plugin.pdcAttributeGateway() != null) {
            plugin.pdcAttributeGateway().shutdown();
        }
        if (plugin.playerDataStore() != null && plugin.messageService() != null) {
            plugin.messageService().info("console.saving_player_data");
            int saved = plugin.playerDataStore().saveAllAsync().join();
            plugin.playerDataStore().waitForPendingSaves().join();
            plugin.messageService().info("console.player_data_saved", Map.of("count", saved));
        }
        if (plugin.forgeGuiService() != null) {
            plugin.forgeGuiService().clearAllSessions();
        }
        if (plugin.recipeBookGuiService() != null) {
            plugin.recipeBookGuiService().clearAllBooks();
        }
        if (plugin.editorGuiService() != null) {
            plugin.editorGuiService().clearAllSessions();
        }
        if (plugin.messageService() != null) {
            plugin.messageService().info("console.plugin_stopped");
        }
    }

    private void closeOpenInventories(EmakiForgePlugin plugin) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.forgeGuiService().getSession(player) != null
                    || plugin.recipeBookGuiService().isRecipeBookInventory(player)
                    || plugin.editorGuiService().hasSession(player)) {
                player.closeInventory();
            }
        }
        plugin.forgeGuiService().clearAllSessions();
        plugin.recipeBookGuiService().clearAllBooks();
        plugin.editorGuiService().clearAllSessions();
    }

    private void validateConfiguredExternalSources(EmakiForgePlugin plugin) {
        for (var entry : plugin.blueprintLoader().all().entrySet()) {
            validateSource(plugin, entry.getValue().source(), "blueprint:" + entry.getKey() + ".source");
        }
        for (var entry : plugin.materialLoader().all().entrySet()) {
            validateSource(plugin, entry.getValue().source(), "material:" + entry.getKey() + ".source");
        }
        for (var entry : plugin.recipeLoader().all().entrySet()) {
            validateSource(plugin, entry.getValue().configuredOutputSource(), "recipe:" + entry.getKey() + ".result.output_item");
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
                configuration.getString("config_version", AppConfig.defaults().configVersion()),
                configuration.getBoolean("release_default_data", true),
                emaki.jiuwu.craft.forge.model.QualitySettings.fromConfig(configuration.get("quality")),
                numberFormat == null ? "0.##" : numberFormat.getString("default", "0.##"),
                numberFormat == null ? "0" : numberFormat.getString("integer", "0"),
                numberFormat == null ? "0.##%" : numberFormat.getString("percentage", "0.##%"),
                permission != null && permission.getBoolean("op_bypass", false),
                condition == null || condition.getBoolean("invalid_as_failure", true),
                history == null || history.getBoolean("enabled", true),
                history == null || history.getBoolean("auto_save", true),
                history == null ? 6000 : Numbers.tryParseInt(history.get("save_interval"), 6000)
        );
    }

    private boolean shouldReleaseDefaultData(EmakiForgePlugin plugin) {
        YamlSection configuration = YamlFiles.load(plugin.dataPath("config.yml").toFile());
        return configuration.getBoolean("release_default_data", true);
    }

}
