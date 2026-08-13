package emaki.jiuwu.craft.item;

import java.util.Map;

import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.item.integration.ItemAttributeBridge;
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
import emaki.jiuwu.craft.item.service.EmakiItemLayerPreviewRegistry;
import emaki.jiuwu.craft.item.service.EmakiItemLayerPreviewService;
import emaki.jiuwu.craft.item.service.EmakiItemMigrationService;
import emaki.jiuwu.craft.item.service.EmakiItemPdcWriter;
import emaki.jiuwu.craft.item.service.EmakiItemSetService;
import emaki.jiuwu.craft.item.service.EmakiItemUpdateService;
import emaki.jiuwu.craft.item.service.ItemComponentInspector;
import emaki.jiuwu.craft.item.service.ItemComponentPlaceholderResolver;
import emaki.jiuwu.craft.item.service.ItemRepairGuiService;
import emaki.jiuwu.craft.item.service.ItemRepairService;

record ItemRuntimeComponents(EmakiScheduling scheduling,
        YamlConfigLoader<AppConfig> appConfigLoader,
        LanguageLoader languageLoader,
        MessageService messageService,
        BootstrapService bootstrapService,
        GuiTemplateLoader guiTemplateLoader,
        GuiService guiService,
        EmakiItemLoader itemLoader,
        EmakiItemSetLoader setLoader,
        EmakiItemAliasLoader aliasLoader,
        EmakiItemIdResolver idResolver,
        EmakiItemMigrationService migrationService,
        EmakiItemLayerPreviewRegistry layerPreviewRegistry,
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
        ItemAttributeBridge pdcAttributeGateway,
        PdcService pdcService,
        ItemRepairService repairService,
        ItemRepairGuiService repairGuiService) implements RuntimeComponents {

    @Override
    public Map<Class<?>, Object> services() {
        return RuntimeComponents.services(
                RuntimeComponents.component(EmakiScheduling.class, scheduling),
                RuntimeComponents.component(YamlConfigLoader.class, appConfigLoader),
                RuntimeComponents.component(LanguageLoader.class, languageLoader),
                RuntimeComponents.component(MessageService.class, messageService),
                RuntimeComponents.component(BootstrapService.class, bootstrapService),
                RuntimeComponents.component(GuiTemplateLoader.class, guiTemplateLoader),
                RuntimeComponents.component(GuiService.class, guiService),
                RuntimeComponents.component(EmakiItemLoader.class, itemLoader),
                RuntimeComponents.component(EmakiItemSetLoader.class, setLoader),
                RuntimeComponents.component(EmakiItemAliasLoader.class, aliasLoader),
                RuntimeComponents.component(EmakiItemIdResolver.class, idResolver),
                RuntimeComponents.component(EmakiItemMigrationService.class, migrationService),
                RuntimeComponents.component(EmakiItemLayerPreviewRegistry.class, layerPreviewRegistry),
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
                RuntimeComponents.component(ItemAttributeBridge.class, pdcAttributeGateway),
                RuntimeComponents.component(PdcService.class, pdcService),
                RuntimeComponents.component(ItemRepairService.class, repairService),
                RuntimeComponents.component(ItemRepairGuiService.class, repairGuiService)
        );
    }
}
