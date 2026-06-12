package emaki.jiuwu.craft.item;

import java.util.Map;

import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.integration.PdcAttributeGateway;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.pdc.PdcService;
import emaki.jiuwu.craft.corelib.runtime.RuntimeComponents;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.item.config.AppConfig;
import emaki.jiuwu.craft.item.loader.EmakiItemAliasLoader;
import emaki.jiuwu.craft.item.loader.EmakiItemLoader;
import emaki.jiuwu.craft.item.loader.EmakiItemSetLoader;
import emaki.jiuwu.craft.item.service.EmakiItemActionService;
import emaki.jiuwu.craft.item.service.EmakiItemConditionChecker;
import emaki.jiuwu.craft.item.service.EmakiItemFactory;
import emaki.jiuwu.craft.item.service.EmakiItemIdentifier;
import emaki.jiuwu.craft.item.service.EmakiItemIdResolver;
import emaki.jiuwu.craft.item.service.EmakiItemLayerPreviewService;
import emaki.jiuwu.craft.item.service.EmakiItemMigrationService;
import emaki.jiuwu.craft.item.service.EmakiItemPdcWriter;
import emaki.jiuwu.craft.item.service.EmakiItemSetService;
import emaki.jiuwu.craft.item.service.EmakiItemUpdateService;
import emaki.jiuwu.craft.item.service.ItemComponentInspector;
import emaki.jiuwu.craft.item.service.ItemComponentPlaceholderResolver;

record ItemRuntimeComponents(YamlConfigLoader<AppConfig> appConfigLoader,
        LanguageLoader languageLoader,
        MessageService messageService,
        BootstrapService bootstrapService,
        EmakiItemLoader itemLoader,
        EmakiItemSetLoader setLoader,
        EmakiItemAliasLoader aliasLoader,
        EmakiItemIdResolver idResolver,
        EmakiItemMigrationService migrationService,
        EmakiItemLayerPreviewService layerPreviewService,
        EmakiItemIdentifier identifier,
        EmakiItemPdcWriter pdcWriter,
        EmakiItemFactory itemFactory,
        EmakiItemUpdateService updateService,
        EmakiItemSetService setService,
        EmakiItemActionService actionService,
        EmakiItemConditionChecker conditionChecker,
        ItemComponentInspector componentInspector,
        ItemComponentPlaceholderResolver componentPlaceholderResolver,
        ItemSourceService itemSourceService,
        PdcAttributeGateway pdcAttributeGateway,
        PdcService pdcService) implements RuntimeComponents {

    @Override
    public Map<Class<?>, Object> services() {
        return RuntimeComponents.services(
                RuntimeComponents.component(YamlConfigLoader.class, appConfigLoader),
                RuntimeComponents.component(LanguageLoader.class, languageLoader),
                RuntimeComponents.component(MessageService.class, messageService),
                RuntimeComponents.component(BootstrapService.class, bootstrapService),
                RuntimeComponents.component(EmakiItemLoader.class, itemLoader),
                RuntimeComponents.component(EmakiItemSetLoader.class, setLoader),
                RuntimeComponents.component(EmakiItemAliasLoader.class, aliasLoader),
                RuntimeComponents.component(EmakiItemIdResolver.class, idResolver),
                RuntimeComponents.component(EmakiItemMigrationService.class, migrationService),
                RuntimeComponents.component(EmakiItemLayerPreviewService.class, layerPreviewService),
                RuntimeComponents.component(EmakiItemIdentifier.class, identifier),
                RuntimeComponents.component(EmakiItemPdcWriter.class, pdcWriter),
                RuntimeComponents.component(EmakiItemFactory.class, itemFactory),
                RuntimeComponents.component(EmakiItemUpdateService.class, updateService),
                RuntimeComponents.component(EmakiItemSetService.class, setService),
                RuntimeComponents.component(EmakiItemActionService.class, actionService),
                RuntimeComponents.component(EmakiItemConditionChecker.class, conditionChecker),
                RuntimeComponents.component(ItemComponentInspector.class, componentInspector),
                RuntimeComponents.component(ItemComponentPlaceholderResolver.class, componentPlaceholderResolver),
                RuntimeComponents.component(ItemSourceService.class, itemSourceService),
                RuntimeComponents.component(PdcAttributeGateway.class, pdcAttributeGateway),
                RuntimeComponents.component(PdcService.class, pdcService)
        );
    }
}
