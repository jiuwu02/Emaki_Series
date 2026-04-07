package emaki.jiuwu.craft.strengthen;

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

record StrengthenRuntimeComponents(AppConfigLoader appConfigLoader,
        LanguageLoader languageLoader,
        StrengthenProfileLoader profileLoader,
        StrengthenRuleLoader ruleLoader,
        StrengthenMaterialLoader materialLoader,
        GuiTemplateLoader guiTemplateLoader,
        MessageService messageService,
        BootstrapService bootstrapService,
        GuiService guiService,
        ProfileResolver profileResolver,
        ChanceCalculator chanceCalculator,
        StrengthenSnapshotBuilder snapshotBuilder,
        StrengthenActionCoordinator actionCoordinator,
        StrengthenAttemptService attemptService,
        StrengthenRefreshService refreshService,
        StrengthenGuiService strengthenGuiService) {

}
