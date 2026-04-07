package emaki.jiuwu.craft.strengthen;

import org.bukkit.Bukkit;

import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.strengthen.loader.AppConfigLoader;
import emaki.jiuwu.craft.strengthen.loader.GuiTemplateLoader;
import emaki.jiuwu.craft.strengthen.loader.LanguageLoader;
import emaki.jiuwu.craft.strengthen.loader.StrengthenMaterialLoader;
import emaki.jiuwu.craft.strengthen.loader.StrengthenProfileLoader;
import emaki.jiuwu.craft.strengthen.loader.StrengthenRuleLoader;
import emaki.jiuwu.craft.strengthen.service.BootstrapService;
import emaki.jiuwu.craft.strengthen.service.ChanceCalculator;
import emaki.jiuwu.craft.strengthen.service.MessageService;
import emaki.jiuwu.craft.strengthen.service.ProfileResolver;
import emaki.jiuwu.craft.strengthen.service.StrengthenActionCoordinator;
import emaki.jiuwu.craft.strengthen.service.StrengthenAttemptService;
import emaki.jiuwu.craft.strengthen.service.StrengthenGuiService;
import emaki.jiuwu.craft.strengthen.service.StrengthenRefreshService;
import emaki.jiuwu.craft.strengthen.service.StrengthenSnapshotBuilder;

final class StrengthenLifecycleCoordinator {

    public StrengthenRuntimeComponents initialize(EmakiStrengthenPlugin plugin) {
        AppConfigLoader appConfigLoader = new AppConfigLoader(plugin);
        LanguageLoader languageLoader = new LanguageLoader(plugin);
        StrengthenProfileLoader profileLoader = new StrengthenProfileLoader(plugin);
        StrengthenRuleLoader ruleLoader = new StrengthenRuleLoader(plugin);
        StrengthenMaterialLoader materialLoader = new StrengthenMaterialLoader(plugin);
        GuiTemplateLoader guiTemplateLoader = new GuiTemplateLoader(plugin);
        MessageService messageService = new MessageService(plugin, languageLoader);
        BootstrapService bootstrapService = new BootstrapService(plugin, messageService);
        GuiService guiService = new GuiService(plugin);
        ProfileResolver profileResolver = new ProfileResolver(plugin);
        ChanceCalculator chanceCalculator = new ChanceCalculator();
        StrengthenSnapshotBuilder snapshotBuilder = new StrengthenSnapshotBuilder(plugin);
        StrengthenActionCoordinator actionCoordinator = new StrengthenActionCoordinator(plugin);
        StrengthenAttemptService attemptService = new StrengthenAttemptService(plugin, profileResolver, chanceCalculator, snapshotBuilder, actionCoordinator);
        StrengthenRefreshService refreshService = new StrengthenRefreshService(plugin, attemptService);
        StrengthenGuiService strengthenGuiService = new StrengthenGuiService(plugin, guiService, attemptService);
        return new StrengthenRuntimeComponents(
                appConfigLoader,
                languageLoader,
                profileLoader,
                ruleLoader,
                materialLoader,
                guiTemplateLoader,
                messageService,
                bootstrapService,
                guiService,
                profileResolver,
                chanceCalculator,
                snapshotBuilder,
                actionCoordinator,
                attemptService,
                refreshService,
                strengthenGuiService
        );
    }

    public void reload(EmakiStrengthenPlugin plugin, boolean closeInventories) {
        if (closeInventories && plugin.strengthenGuiService() != null) {
            var sessions = Bukkit.getOnlinePlayers();
            for (var player : sessions) {
                if (plugin.strengthenGuiService().getSession(player) != null) {
                    player.closeInventory();
                }
            }
            plugin.strengthenGuiService().clearAllSessions();
        }
        plugin.languageLoader().load();
        plugin.appConfigLoader().load();
        plugin.languageLoader().setLanguage(plugin.appConfig().language());
        plugin.profileLoader().load();
        plugin.ruleLoader().load();
        plugin.materialLoader().load();
        plugin.guiTemplateLoader().load();
        plugin.refreshService().refreshOnlinePlayers();
    }

    public void shutdown(EmakiStrengthenPlugin plugin) {
        if (plugin.strengthenGuiService() != null) {
            plugin.strengthenGuiService().clearAllSessions();
        }
    }
}
