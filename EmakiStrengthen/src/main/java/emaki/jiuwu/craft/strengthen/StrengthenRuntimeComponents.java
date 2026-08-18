package emaki.jiuwu.craft.strengthen;

import java.util.Map;

import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.strengthen.integration.StrengthenAttributeBridge;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.runtime.RuntimeComponents;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.strengthen.config.AppConfig;
import emaki.jiuwu.craft.strengthen.enhancement.EnhancementAttemptService;
import emaki.jiuwu.craft.strengthen.enhancement.affix.AffixGuiService;
import emaki.jiuwu.craft.strengthen.enhancement.affix.AffixSelectionService;
import emaki.jiuwu.craft.strengthen.enhancement.pity.InMemoryPityStateStore;
import emaki.jiuwu.craft.strengthen.enhancement.recipe.EnhancementRecipeLoader;
import emaki.jiuwu.craft.strengthen.enhancement.target.EnhancementTargetRegistry;
import emaki.jiuwu.craft.strengthen.loader.StrengthenRecipeLoader;
import emaki.jiuwu.craft.strengthen.service.ChanceCalculator;
import emaki.jiuwu.craft.strengthen.service.StrengthenRecipeResolver;
import emaki.jiuwu.craft.strengthen.service.StrengthenActionCoordinator;
import emaki.jiuwu.craft.strengthen.service.StrengthenAttemptService;
import emaki.jiuwu.craft.strengthen.service.StrengthenEconomyService;
import emaki.jiuwu.craft.strengthen.service.StrengthenGuiService;
import emaki.jiuwu.craft.strengthen.service.StrengthenRefreshService;
import emaki.jiuwu.craft.strengthen.service.StrengthenSnapshotBuilder;
import emaki.jiuwu.craft.strengthen.service.StrengthenTransferService;

record StrengthenRuntimeComponents(ExecutionDispatcher executionDispatcher,
        ThreadOwnership threadOwnership,
        YamlConfigLoader<AppConfig> appConfigLoader,
        LanguageLoader languageLoader,
        StrengthenRecipeLoader recipeLoader,
        GuiTemplateLoader guiTemplateLoader,
        MessageService messageService,
        BootstrapService bootstrapService,
        GuiService guiService,
        ItemSourceService coreItemSourceService,
        StrengthenAttributeBridge pdcAttributeGateway,
        StrengthenAttributeBridge affixAttributeGateway,
        StrengthenRecipeResolver recipeResolver,
        ChanceCalculator chanceCalculator,
        StrengthenEconomyService economyService,
        StrengthenSnapshotBuilder snapshotBuilder,
        StrengthenActionCoordinator actionCoordinator,
        StrengthenAttemptService attemptService,
        StrengthenTransferService transferService,
        StrengthenRefreshService refreshService,
        StrengthenGuiService strengthenGuiService,
        EnhancementRecipeLoader enhancementRecipeLoader,
        EnhancementTargetRegistry enhancementTargetRegistry,
        InMemoryPityStateStore pityStateStore,
        EnhancementAttemptService enhancementAttemptService,
        AffixSelectionService affixSelectionService,
        AffixGuiService affixGuiService) implements RuntimeComponents {

    @Override
    public Map<Class<?>, Object> services() {
        return RuntimeComponents.services(
                RuntimeComponents.component(ExecutionDispatcher.class, executionDispatcher),
                RuntimeComponents.component(ThreadOwnership.class, threadOwnership),
                RuntimeComponents.component(YamlConfigLoader.class, appConfigLoader),
                RuntimeComponents.component(LanguageLoader.class, languageLoader),
                RuntimeComponents.component(StrengthenRecipeLoader.class, recipeLoader),
                RuntimeComponents.component(GuiTemplateLoader.class, guiTemplateLoader),
                RuntimeComponents.component(MessageService.class, messageService),
                RuntimeComponents.component(BootstrapService.class, bootstrapService),
                RuntimeComponents.component(GuiService.class, guiService),
                RuntimeComponents.component(ItemSourceService.class, coreItemSourceService),
                RuntimeComponents.component(StrengthenAttributeBridge.class, pdcAttributeGateway),
                RuntimeComponents.component(StrengthenRecipeResolver.class, recipeResolver),
                RuntimeComponents.component(ChanceCalculator.class, chanceCalculator),
                RuntimeComponents.component(StrengthenEconomyService.class, economyService),
                RuntimeComponents.component(StrengthenSnapshotBuilder.class, snapshotBuilder),
                RuntimeComponents.component(StrengthenActionCoordinator.class, actionCoordinator),
                RuntimeComponents.component(StrengthenAttemptService.class, attemptService),
                RuntimeComponents.component(StrengthenTransferService.class, transferService),
                RuntimeComponents.component(StrengthenRefreshService.class, refreshService),
                RuntimeComponents.component(StrengthenGuiService.class, strengthenGuiService),
                RuntimeComponents.component(EnhancementRecipeLoader.class, enhancementRecipeLoader),
                RuntimeComponents.component(EnhancementTargetRegistry.class, enhancementTargetRegistry),
                RuntimeComponents.component(InMemoryPityStateStore.class, pityStateStore),
                RuntimeComponents.component(EnhancementAttemptService.class, enhancementAttemptService),
                RuntimeComponents.component(AffixSelectionService.class, affixSelectionService),
                RuntimeComponents.component(AffixGuiService.class, affixGuiService)
        );
    }
}
