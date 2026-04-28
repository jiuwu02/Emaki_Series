package emaki.jiuwu.craft.strengthen;

import java.util.Map;

import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.integration.PdcAttributeGateway;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.runtime.RuntimeComponents;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.strengthen.config.AppConfig;
import emaki.jiuwu.craft.strengthen.loader.StrengthenRecipeLoader;
import emaki.jiuwu.craft.strengthen.service.ChanceCalculator;
import emaki.jiuwu.craft.strengthen.service.StrengthenRecipeResolver;
import emaki.jiuwu.craft.strengthen.service.StrengthenActionCoordinator;
import emaki.jiuwu.craft.strengthen.service.StrengthenAttemptService;
import emaki.jiuwu.craft.strengthen.service.StrengthenEconomyService;
import emaki.jiuwu.craft.strengthen.service.StrengthenGuiService;
import emaki.jiuwu.craft.strengthen.service.StrengthenRefreshService;
import emaki.jiuwu.craft.strengthen.service.StrengthenSnapshotBuilder;

record StrengthenRuntimeComponents(YamlConfigLoader<AppConfig> appConfigLoader,
        LanguageLoader languageLoader,
        StrengthenRecipeLoader recipeLoader,
        GuiTemplateLoader guiTemplateLoader,
        MessageService messageService,
        BootstrapService bootstrapService,
        GuiService guiService,
        ItemSourceService coreItemSourceService,
        PdcAttributeGateway pdcAttributeGateway,
        StrengthenRecipeResolver recipeResolver,
        ChanceCalculator chanceCalculator,
        StrengthenEconomyService economyService,
        StrengthenSnapshotBuilder snapshotBuilder,
        StrengthenActionCoordinator actionCoordinator,
        StrengthenAttemptService attemptService,
        StrengthenRefreshService refreshService,
        StrengthenGuiService strengthenGuiService) implements RuntimeComponents {

    @Override
    public Map<Class<?>, Object> services() {
        return RuntimeComponents.services(
                RuntimeComponents.component(YamlConfigLoader.class, appConfigLoader),
                RuntimeComponents.component(LanguageLoader.class, languageLoader),
                RuntimeComponents.component(StrengthenRecipeLoader.class, recipeLoader),
                RuntimeComponents.component(GuiTemplateLoader.class, guiTemplateLoader),
                RuntimeComponents.component(MessageService.class, messageService),
                RuntimeComponents.component(BootstrapService.class, bootstrapService),
                RuntimeComponents.component(GuiService.class, guiService),
                RuntimeComponents.component(ItemSourceService.class, coreItemSourceService),
                RuntimeComponents.component(PdcAttributeGateway.class, pdcAttributeGateway),
                RuntimeComponents.component(StrengthenRecipeResolver.class, recipeResolver),
                RuntimeComponents.component(ChanceCalculator.class, chanceCalculator),
                RuntimeComponents.component(StrengthenEconomyService.class, economyService),
                RuntimeComponents.component(StrengthenSnapshotBuilder.class, snapshotBuilder),
                RuntimeComponents.component(StrengthenActionCoordinator.class, actionCoordinator),
                RuntimeComponents.component(StrengthenAttemptService.class, attemptService),
                RuntimeComponents.component(StrengthenRefreshService.class, refreshService),
                RuntimeComponents.component(StrengthenGuiService.class, strengthenGuiService)
        );
    }
}
