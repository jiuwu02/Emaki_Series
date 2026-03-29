package emaki.jiuwu.craft.forge;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.forge.config.AppConfig;
import emaki.jiuwu.craft.forge.loader.AppConfigLoader;
import emaki.jiuwu.craft.forge.loader.BlueprintLoader;
import emaki.jiuwu.craft.forge.loader.GuiTemplateLoader;
import emaki.jiuwu.craft.forge.loader.LanguageLoader;
import emaki.jiuwu.craft.forge.loader.MaterialLoader;
import emaki.jiuwu.craft.forge.loader.PlayerDataStore;
import emaki.jiuwu.craft.forge.loader.RecipeLoader;
import emaki.jiuwu.craft.forge.service.BootstrapService;
import emaki.jiuwu.craft.forge.service.ForgeGuiService;
import emaki.jiuwu.craft.forge.service.ForgeService;
import emaki.jiuwu.craft.forge.service.ItemIdentifierService;
import emaki.jiuwu.craft.forge.service.MessageService;
import emaki.jiuwu.craft.forge.service.RecipeBookGuiService;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

final class ForgeLifecycleCoordinator {

    public ForgeRuntimeComponents initialize(EmakiForgePlugin plugin) {
        AppConfigLoader appConfigLoader = new AppConfigLoader(plugin);
        LanguageLoader languageLoader = new LanguageLoader(plugin);
        BlueprintLoader blueprintLoader = new BlueprintLoader(plugin);
        MaterialLoader materialLoader = new MaterialLoader(plugin);
        RecipeLoader recipeLoader = new RecipeLoader(plugin);
        GuiTemplateLoader guiTemplateLoader = new GuiTemplateLoader(plugin);
        PlayerDataStore playerDataStore = new PlayerDataStore(plugin);
        MessageService messageService = new MessageService(plugin, languageLoader, plugin::appConfig);
        languageLoader.load();
        BootstrapService bootstrapService = new BootstrapService(plugin, messageService);
        GuiService guiService = new GuiService(plugin);
        ItemIdentifierService itemIdentifierService = new ItemIdentifierService(plugin);
        registerCoreLibResolvers(itemIdentifierService);
        ForgeService forgeService = new ForgeService(plugin);
        ForgeGuiService forgeGuiService = new ForgeGuiService(plugin, guiService);
        RecipeBookGuiService recipeBookGuiService = new RecipeBookGuiService(plugin, guiService);
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
            forgeService,
            forgeGuiService,
            recipeBookGuiService
        );
    }

    public BukkitTask reload(EmakiForgePlugin plugin, BukkitTask currentTask, boolean closeOpenInventories) {
        if (closeOpenInventories) {
            closeOpenInventories(plugin);
        }
        plugin.languageLoader().load();
        plugin.appConfigLoader().load();
        plugin.languageLoader().setLanguage(plugin.appConfig().language());
        plugin.blueprintLoader().load();
        plugin.materialLoader().load();
        plugin.recipeLoader().load();
        plugin.guiTemplateLoader().load();
        plugin.playerDataStore().load();
        plugin.itemIdentifierService().refresh();
        validateConfiguredExternalSources(plugin);
        return rescheduleAutoSave(plugin, currentTask);
    }

    public BukkitTask rescheduleAutoSave(EmakiForgePlugin plugin, BukkitTask currentTask) {
        BukkitTask nextTask = cancelAutoSave(currentTask);
        AppConfig config = plugin.appConfig();
        if (config.historyEnabled() && config.historyAutoSave()) {
            nextTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                () -> plugin.playerDataStore().saveAll(),
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
        unregisterCoreLibResolvers(plugin.itemIdentifierService());
        cancelAutoSave(autoSaveTask);
        if (plugin.playerDataStore() != null && plugin.messageService() != null) {
            plugin.messageService().info("console.saving_player_data");
            plugin.messageService().info("console.player_data_saved", Map.of("count", plugin.playerDataStore().saveAll()));
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
            if (plugin.forgeGuiService().getSession(player) != null || plugin.recipeBookGuiService().isRecipeBookInventory(player)) {
                player.closeInventory();
            }
        }
        plugin.forgeGuiService().clearAllSessions();
        plugin.recipeBookGuiService().clearAllBooks();
    }

    private void validateConfiguredExternalSources(EmakiForgePlugin plugin) {
        for (var entry : plugin.blueprintLoader().all().entrySet()) {
            validateSource(plugin, entry.getValue().source(), "blueprint:" + entry.getKey() + ".source");
        }
        for (var entry : plugin.materialLoader().all().entrySet()) {
            validateSource(plugin, entry.getValue().source(), "material:" + entry.getKey() + ".source");
        }
        for (var entry : plugin.recipeLoader().all().entrySet()) {
            validateSource(plugin, entry.getValue().targetItemSource(), "recipe:" + entry.getKey() + ".target_item");
            if (entry.getValue().result() != null) {
                validateSource(plugin, entry.getValue().result().outputItem(), "recipe:" + entry.getKey() + ".result.output_item");
            }
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

    private void registerCoreLibResolvers(ItemIdentifierService itemIdentifierService) {
        EmakiCoreLibPlugin coreLib = EmakiCoreLibPlugin.getInstance();
        if (coreLib == null || itemIdentifierService == null) {
            return;
        }
        coreLib.itemSourceService().registerResolver(itemIdentifierService);
    }

    private void unregisterCoreLibResolvers(ItemIdentifierService itemIdentifierService) {
        EmakiCoreLibPlugin coreLib = EmakiCoreLibPlugin.getInstance();
        if (coreLib == null || itemIdentifierService == null) {
            return;
        }
        coreLib.itemSourceService().unregisterResolver(itemIdentifierService.id());
    }
}
