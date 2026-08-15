package emaki.jiuwu.craft.station;

import java.util.Map;

import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.runtime.RuntimeComponents;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.station.config.AppConfig;
import emaki.jiuwu.craft.station.definition.StationLoader;
import emaki.jiuwu.craft.station.dismantle.DismantleRecipeLoader;
import emaki.jiuwu.craft.station.dismantle.DismantleService;
import emaki.jiuwu.craft.station.dismantle.DismantleStationLoader;
import emaki.jiuwu.craft.station.gui.StationGuiService;
import emaki.jiuwu.craft.station.material.BackpackChannel;
import emaki.jiuwu.craft.station.material.MergedMaterialChannel;
import emaki.jiuwu.craft.station.material.OutputDelivery;
import emaki.jiuwu.craft.station.material.StationCapabilities;
import emaki.jiuwu.craft.station.material.StorageChannel;
import emaki.jiuwu.craft.station.queue.QueueService;
import emaki.jiuwu.craft.station.queue.QueueStore;
import emaki.jiuwu.craft.station.queue.QueueTicker;
import emaki.jiuwu.craft.station.queue.QueueUnlockService;
import emaki.jiuwu.craft.station.queue.QueueUnlockStore;
import emaki.jiuwu.craft.station.queue.StationCraftService;
import emaki.jiuwu.craft.station.queue.StationQueueUnlockService;
import emaki.jiuwu.craft.station.recipe.RecipeLoader;

record StationRuntimeComponents(YamlConfigLoader<AppConfig> appConfigLoader,
        LanguageLoader languageLoader,
        MessageService messageService,
        BootstrapService bootstrapService,
        ExecutionDispatcher executionDispatcher,
        ThreadOwnership threadOwnership,
        GuiService guiService,
        GuiTemplateLoader layoutLoader,
        StationLoader stationLoader,
        RecipeLoader recipeLoader,
        DismantleStationLoader dismantleStationLoader,
        DismantleRecipeLoader dismantleRecipeLoader,
        DismantleService dismantleService,
        StationCapabilities capabilities,
        BackpackChannel backpackChannel,
        StorageChannel storageChannel,
        MergedMaterialChannel materialChannel,
        OutputDelivery outputDelivery,
        QueueStore queueStore,
        QueueService queueService,
        QueueUnlockStore unlockStore,
        QueueUnlockService unlockService,
        StationQueueUnlockService purchaseService,
        StationCraftService craftService,
        StationGuiService stationGuiService,
        QueueTicker queueTicker) implements RuntimeComponents {

    @Override
    public Map<Class<?>, Object> services() {
        return RuntimeComponents.services(
                RuntimeComponents.component(MessageService.class, messageService),
                RuntimeComponents.component(LanguageLoader.class, languageLoader),
                RuntimeComponents.component(BootstrapService.class, bootstrapService),
                RuntimeComponents.component(ExecutionDispatcher.class, executionDispatcher),
                RuntimeComponents.component(GuiService.class, guiService),
                RuntimeComponents.component(GuiTemplateLoader.class, layoutLoader),
                RuntimeComponents.component(StationLoader.class, stationLoader),
                RuntimeComponents.component(RecipeLoader.class, recipeLoader),
                RuntimeComponents.component(DismantleService.class, dismantleService),
                RuntimeComponents.component(QueueService.class, queueService),
                RuntimeComponents.component(QueueUnlockService.class, unlockService),
                RuntimeComponents.component(StationCraftService.class, craftService),
                RuntimeComponents.component(StationGuiService.class, stationGuiService));
    }
}
