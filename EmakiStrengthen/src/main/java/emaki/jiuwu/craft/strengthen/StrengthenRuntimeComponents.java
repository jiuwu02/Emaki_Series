package emaki.jiuwu.craft.strengthen;

import java.util.LinkedHashMap;
import java.util.Map;

import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.integration.ReflectivePdcAttributeGateway;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.runtime.RuntimeComponents;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.strengthen.config.AppConfig;
import emaki.jiuwu.craft.strengthen.loader.GuiTemplateLoader;
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
        ReflectivePdcAttributeGateway pdcAttributeGateway,
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
        Map<Class<?>, Object> services = new LinkedHashMap<>();
        services.put(YamlConfigLoader.class, appConfigLoader);
        services.put(LanguageLoader.class, languageLoader);
        services.put(StrengthenRecipeLoader.class, recipeLoader);
        services.put(GuiTemplateLoader.class, guiTemplateLoader);
        services.put(MessageService.class, messageService);
        services.put(BootstrapService.class, bootstrapService);
        services.put(GuiService.class, guiService);
        services.put(ItemSourceService.class, coreItemSourceService);
        services.put(ReflectivePdcAttributeGateway.class, pdcAttributeGateway);
        services.put(StrengthenRecipeResolver.class, recipeResolver);
        services.put(ChanceCalculator.class, chanceCalculator);
        services.put(StrengthenEconomyService.class, economyService);
        services.put(StrengthenSnapshotBuilder.class, snapshotBuilder);
        services.put(StrengthenActionCoordinator.class, actionCoordinator);
        services.put(StrengthenAttemptService.class, attemptService);
        services.put(StrengthenRefreshService.class, refreshService);
        services.put(StrengthenGuiService.class, strengthenGuiService);
        return Map.copyOf(services);
    }
}
