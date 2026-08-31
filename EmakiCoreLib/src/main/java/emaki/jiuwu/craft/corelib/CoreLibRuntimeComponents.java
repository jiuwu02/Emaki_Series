package emaki.jiuwu.craft.corelib;

import java.util.Map;

import emaki.jiuwu.craft.corelib.action.pipeline.exec.StageDispatcher;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyService;
import emaki.jiuwu.craft.corelib.async.AsyncFileService;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckService;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.dialog.DialogService;
import emaki.jiuwu.craft.corelib.display.ItemDisplayService;
import emaki.jiuwu.craft.corelib.display.TextDisplayService;
import emaki.jiuwu.craft.corelib.event.gameplay.GameplayEventPublisher;
import emaki.jiuwu.craft.corelib.gui.GuiBackend;
import emaki.jiuwu.craft.corelib.gui.GuiBackendRegistry;
import emaki.jiuwu.craft.corelib.item.ConfiguredItemService;
import emaki.jiuwu.craft.corelib.item.ItemSourceIntegrationCoordinator;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.metrics.BStatsService;
import emaki.jiuwu.craft.corelib.monitor.PerformanceMonitor;
import emaki.jiuwu.craft.corelib.runtime.CorePluginLifecycle;
import emaki.jiuwu.craft.corelib.runtime.RuntimeComponents;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.AsyncYamlFiles;

record CoreLibRuntimeComponents(LanguageLoader languageLoader,
        MessageService messageService,
        BStatsService bStatsService,
        DebugLogger debugLogger,
        ItemSourceIntegrationCoordinator itemSourceIntegrationCoordinator,
        ConfiguredItemService configuredItemService,
        ConfigPrecheckService configPrecheckService,
        StageDispatcher stageDispatcher,
        PerformanceMonitor performanceMonitor,
        AsyncTaskScheduler asyncTaskScheduler,
        AsyncFileService asyncFileService,
        AsyncYamlFiles asyncYamlFiles,
        CorePluginLifecycle corePluginLifecycle,
        GuiBackendRegistry guiBackendRegistry,
        GuiBackend guiBackend,
        EmakiItemAssemblyService itemAssemblyService,
        TextDisplayService textDisplayService,
        ItemDisplayService itemDisplayService,
        DialogService dialogService,
        GameplayEventPublisher gameplayEventPublisher) implements RuntimeComponents {

    @Override
    public Map<Class<?>, Object> services() {
        return RuntimeComponents.services(
                RuntimeComponents.component(LanguageLoader.class, languageLoader),
                RuntimeComponents.component(MessageService.class, messageService),
                RuntimeComponents.component(BStatsService.class, bStatsService),
                RuntimeComponents.component(PerformanceMonitor.class, performanceMonitor),
                RuntimeComponents.component(AsyncTaskScheduler.class, asyncTaskScheduler),
                RuntimeComponents.component(AsyncFileService.class, asyncFileService),
                RuntimeComponents.component(AsyncYamlFiles.class, asyncYamlFiles),
                RuntimeComponents.component(CorePluginLifecycle.class, corePluginLifecycle),
                RuntimeComponents.component(ConfigPrecheckService.class, configPrecheckService),
                RuntimeComponents.component(ConfiguredItemService.class, configuredItemService),
                RuntimeComponents.component(EmakiItemAssemblyService.class, itemAssemblyService));
    }
}
