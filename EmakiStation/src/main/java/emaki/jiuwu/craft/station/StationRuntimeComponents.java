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
import emaki.jiuwu.craft.station.gui.StationGuiService;
import emaki.jiuwu.craft.station.material.BackpackChannel;
import emaki.jiuwu.craft.station.material.OutputDelivery;
import emaki.jiuwu.craft.station.material.StationCapabilities;
import emaki.jiuwu.craft.station.material.StorageChannel;
import emaki.jiuwu.craft.station.queue.QueueService;
import emaki.jiuwu.craft.station.queue.QueueStore;
import emaki.jiuwu.craft.station.queue.QueueTicker;
import emaki.jiuwu.craft.station.queue.StationCraftService;
import emaki.jiuwu.craft.station.recipe.RecipeLoader;

/**
 * Everything the lifecycle coordinator builds, handed to the plugin in one piece.
 *
 * @param appConfigLoader     the {@code config.yml} loader
 * @param languageLoader      the language loader
 * @param messageService      player-facing and console messaging
 * @param bootstrapService    default-file installation
 * @param executionDispatcher CoreLib's scheduler facade
 * @param threadOwnership     CoreLib's thread-ownership probe
 * @param guiService          CoreLib's GUI service
 * @param layoutLoader        the station layout loader
 * @param stationLoader       the station definition loader
 * @param recipeLoader        the recipe loader
 * @param capabilities        the capability probe result
 * @param backpackChannel     the inventory channel
 * @param storageChannel      the warehouse channel
 * @param outputDelivery      the output router
 * @param queueStore          queue persistence
 * @param queueService        the queue cache
 * @param craftService        the submission orchestrator
 * @param stationGuiService   the window manager
 * @param queueTicker         the periodic settlement task
 */
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
        StationCapabilities capabilities,
        BackpackChannel backpackChannel,
        StorageChannel storageChannel,
        OutputDelivery outputDelivery,
        QueueStore queueStore,
        QueueService queueService,
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
                RuntimeComponents.component(QueueService.class, queueService),
                RuntimeComponents.component(StationCraftService.class, craftService),
                RuntimeComponents.component(StationGuiService.class, stationGuiService));
    }
}
