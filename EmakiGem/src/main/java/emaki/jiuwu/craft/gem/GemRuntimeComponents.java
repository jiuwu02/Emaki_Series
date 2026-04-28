package emaki.jiuwu.craft.gem;

import java.util.Map;

import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.integration.PdcAttributeGateway;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.runtime.RuntimeComponents;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.gem.config.AppConfig;
import emaki.jiuwu.craft.gem.loader.GemItemLoader;
import emaki.jiuwu.craft.gem.loader.GemLoader;
import emaki.jiuwu.craft.gem.service.GemActionCoordinator;
import emaki.jiuwu.craft.gem.service.GemEconomyService;
import emaki.jiuwu.craft.gem.service.GemExtractService;
import emaki.jiuwu.craft.gem.service.GemGuiService;
import emaki.jiuwu.craft.gem.service.GemInlayService;
import emaki.jiuwu.craft.gem.service.GemItemFactory;
import emaki.jiuwu.craft.gem.service.GemItemMatcher;
import emaki.jiuwu.craft.gem.service.GemPdcAttributeWriter;
import emaki.jiuwu.craft.gem.service.GemSnapshotBuilder;
import emaki.jiuwu.craft.gem.service.GemStateService;
import emaki.jiuwu.craft.gem.service.GemUpgradeService;
import emaki.jiuwu.craft.gem.service.SocketOpenerService;

record GemRuntimeComponents(YamlConfigLoader<AppConfig> appConfigLoader,
        LanguageLoader languageLoader,
        GemLoader gemLoader,
        GemItemLoader gemItemLoader,
        GuiTemplateLoader guiTemplateLoader,
        MessageService messageService,
        BootstrapService bootstrapService,
        GuiService guiService,
        ItemSourceService coreItemSourceService,
        PdcAttributeGateway pdcAttributeGateway,
        GemItemMatcher itemMatcher,
        GemItemFactory itemFactory,
        GemSnapshotBuilder snapshotBuilder,
        GemPdcAttributeWriter pdcAttributeWriter,
        GemStateService stateService,
        GemEconomyService economyService,
        GemActionCoordinator actionCoordinator,
        SocketOpenerService socketOpenerService,
        GemInlayService inlayService,
        GemExtractService extractService,
        GemUpgradeService upgradeService,
        GemGuiService gemGuiService) implements RuntimeComponents {

    @Override
    public Map<Class<?>, Object> services() {
        return RuntimeComponents.services(
                RuntimeComponents.component(YamlConfigLoader.class, appConfigLoader),
                RuntimeComponents.component(LanguageLoader.class, languageLoader),
                RuntimeComponents.component(GemLoader.class, gemLoader),
                RuntimeComponents.component(GemItemLoader.class, gemItemLoader),
                RuntimeComponents.component(GuiTemplateLoader.class, guiTemplateLoader),
                RuntimeComponents.component(MessageService.class, messageService),
                RuntimeComponents.component(BootstrapService.class, bootstrapService),
                RuntimeComponents.component(GuiService.class, guiService),
                RuntimeComponents.component(ItemSourceService.class, coreItemSourceService),
                RuntimeComponents.component(PdcAttributeGateway.class, pdcAttributeGateway),
                RuntimeComponents.component(GemItemMatcher.class, itemMatcher),
                RuntimeComponents.component(GemItemFactory.class, itemFactory),
                RuntimeComponents.component(GemSnapshotBuilder.class, snapshotBuilder),
                RuntimeComponents.component(GemPdcAttributeWriter.class, pdcAttributeWriter),
                RuntimeComponents.component(GemStateService.class, stateService),
                RuntimeComponents.component(GemEconomyService.class, economyService),
                RuntimeComponents.component(GemActionCoordinator.class, actionCoordinator),
                RuntimeComponents.component(SocketOpenerService.class, socketOpenerService),
                RuntimeComponents.component(GemInlayService.class, inlayService),
                RuntimeComponents.component(GemExtractService.class, extractService),
                RuntimeComponents.component(GemUpgradeService.class, upgradeService),
                RuntimeComponents.component(GemGuiService.class, gemGuiService)
        );
    }
}
