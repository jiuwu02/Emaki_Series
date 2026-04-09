package emaki.jiuwu.craft.strengthen;

import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.strengthen.loader.AppConfigLoader;
import emaki.jiuwu.craft.strengthen.loader.GuiTemplateLoader;
import emaki.jiuwu.craft.strengthen.loader.LanguageLoader;
import emaki.jiuwu.craft.strengthen.loader.StrengthenRecipeLoader;
import emaki.jiuwu.craft.strengthen.service.BootstrapService;
import emaki.jiuwu.craft.strengthen.service.ChanceCalculator;
import emaki.jiuwu.craft.strengthen.service.MessageService;
import emaki.jiuwu.craft.strengthen.service.StrengthenRecipeResolver;
import emaki.jiuwu.craft.strengthen.service.StrengthenActionCoordinator;
import emaki.jiuwu.craft.strengthen.service.StrengthenAttemptService;
import emaki.jiuwu.craft.strengthen.service.StrengthenEconomyService;
import emaki.jiuwu.craft.strengthen.service.StrengthenGuiService;
import emaki.jiuwu.craft.strengthen.service.StrengthenRefreshService;
import emaki.jiuwu.craft.strengthen.service.StrengthenSnapshotBuilder;

record StrengthenRuntimeComponents(AppConfigLoader appConfigLoader,
        LanguageLoader languageLoader,
        StrengthenRecipeLoader recipeLoader,
        GuiTemplateLoader guiTemplateLoader,
        MessageService messageService,
        BootstrapService bootstrapService,
        GuiService guiService,
        StrengthenRecipeResolver recipeResolver,
        ChanceCalculator chanceCalculator,
        StrengthenEconomyService economyService,
        StrengthenSnapshotBuilder snapshotBuilder,
        StrengthenActionCoordinator actionCoordinator,
        StrengthenAttemptService attemptService,
        StrengthenRefreshService refreshService,
        StrengthenGuiService strengthenGuiService) {

}
