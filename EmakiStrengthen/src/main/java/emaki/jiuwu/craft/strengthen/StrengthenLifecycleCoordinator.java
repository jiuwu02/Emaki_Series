package emaki.jiuwu.craft.strengthen;

import org.bukkit.Bukkit;

import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.strengthen.loader.AppConfigLoader;
import emaki.jiuwu.craft.strengthen.loader.GuiTemplateLoader;
import emaki.jiuwu.craft.strengthen.loader.LanguageLoader;
import emaki.jiuwu.craft.strengthen.loader.StrengthenRecipeLoader;
import emaki.jiuwu.craft.strengthen.service.BootstrapService;
import emaki.jiuwu.craft.strengthen.service.ChanceCalculator;
import emaki.jiuwu.craft.strengthen.service.MessageService;
import emaki.jiuwu.craft.strengthen.service.StrengthenActionCoordinator;
import emaki.jiuwu.craft.strengthen.service.StrengthenAttemptService;
import emaki.jiuwu.craft.strengthen.service.StrengthenEconomyService;
import emaki.jiuwu.craft.strengthen.service.StrengthenGuiService;
import emaki.jiuwu.craft.strengthen.service.StrengthenRefreshService;
import emaki.jiuwu.craft.strengthen.service.StrengthenRecipeResolver;
import emaki.jiuwu.craft.strengthen.service.StrengthenSnapshotBuilder;

final class StrengthenLifecycleCoordinator {

    public StrengthenRuntimeComponents initialize(EmakiStrengthenPlugin plugin) {
        AppConfigLoader appConfigLoader = new AppConfigLoader(plugin);
        LanguageLoader languageLoader = new LanguageLoader(plugin);
        StrengthenRecipeLoader recipeLoader = new StrengthenRecipeLoader(plugin);
        GuiTemplateLoader guiTemplateLoader = new GuiTemplateLoader(plugin);
        MessageService messageService = new MessageService(plugin, languageLoader);
        BootstrapService bootstrapService = new BootstrapService(plugin, messageService);
        GuiService guiService = new GuiService(plugin);
        StrengthenRecipeResolver recipeResolver = new StrengthenRecipeResolver(plugin);
        ChanceCalculator chanceCalculator = new ChanceCalculator();
        StrengthenEconomyService economyService = new StrengthenEconomyService(plugin);
        StrengthenSnapshotBuilder snapshotBuilder = new StrengthenSnapshotBuilder();
        StrengthenActionCoordinator actionCoordinator = new StrengthenActionCoordinator(plugin);
        StrengthenAttemptService attemptService = new StrengthenAttemptService(
                plugin,
                recipeResolver,
                chanceCalculator,
                economyService,
                snapshotBuilder,
                actionCoordinator
        );
        StrengthenRefreshService refreshService = new StrengthenRefreshService(plugin, attemptService);
        StrengthenGuiService strengthenGuiService = new StrengthenGuiService(plugin, guiService, attemptService);
        return new StrengthenRuntimeComponents(
                appConfigLoader,
                languageLoader,
                recipeLoader,
                guiTemplateLoader,
                messageService,
                bootstrapService,
                guiService,
                recipeResolver,
                chanceCalculator,
                economyService,
                snapshotBuilder,
                actionCoordinator,
                attemptService,
                refreshService,
                strengthenGuiService
        );
    }

    public void reload(EmakiStrengthenPlugin plugin, boolean closeInventories) {
        if (closeInventories && plugin.strengthenGuiService() != null) {
            for (var player : Bukkit.getOnlinePlayers()) {
                if (plugin.strengthenGuiService().getSession(player) != null) {
                    player.closeInventory();
                }
            }
            plugin.strengthenGuiService().clearAllSessions();
        }
        plugin.languageLoader().load();
        plugin.appConfigLoader().load();
        plugin.languageLoader().setLanguage(plugin.appConfig().language());
        plugin.recipeLoader().load();
        plugin.guiTemplateLoader().load();
        plugin.refreshService().refreshOnlinePlayers();
    }

    public void shutdown(EmakiStrengthenPlugin plugin) {
        if (plugin.strengthenGuiService() != null) {
            plugin.strengthenGuiService().clearAllSessions();
        }
    }
}
