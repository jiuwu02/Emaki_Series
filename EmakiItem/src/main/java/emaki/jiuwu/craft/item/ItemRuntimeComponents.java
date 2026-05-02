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
import emaki.jiuwu.craft.item.api.EmakiItemApi;
import emaki.jiuwu.craft.item.config.AppConfig;
import emaki.jiuwu.craft.item.loader.EmakiItemLoader;
import emaki.jiuwu.craft.item.service.EmakiItemActionService;
import emaki.jiuwu.craft.item.service.EmakiItemConditionChecker;
import emaki.jiuwu.craft.item.service.EmakiItemFactory;
import emaki.jiuwu.craft.item.service.EmakiItemIdentifier;
import emaki.jiuwu.craft.item.service.EmakiItemPdcWriter;

record ItemRuntimeComponents(YamlConfigLoader<AppConfig> appConfigLoader,
        LanguageLoader languageLoader,
        MessageService messageService,
        BootstrapService bootstrapService,
        EmakiItemLoader itemLoader,
        EmakiItemIdentifier identifier,
        EmakiItemPdcWriter pdcWriter,
        EmakiItemFactory itemFactory,
        EmakiItemActionService actionService,
        EmakiItemConditionChecker conditionChecker,
        EmakiItemApi itemApi,
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
                RuntimeComponents.component(EmakiItemIdentifier.class, identifier),
                RuntimeComponents.component(EmakiItemPdcWriter.class, pdcWriter),
                RuntimeComponents.component(EmakiItemFactory.class, itemFactory),
                RuntimeComponents.component(EmakiItemActionService.class, actionService),
                RuntimeComponents.component(EmakiItemConditionChecker.class, conditionChecker),
                RuntimeComponents.component(EmakiItemApi.class, itemApi),
                RuntimeComponents.component(ItemSourceService.class, itemSourceService),
                RuntimeComponents.component(PdcAttributeGateway.class, pdcAttributeGateway),
                RuntimeComponents.component(PdcService.class, pdcService)
        );
    }
}
