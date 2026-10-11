package emaki.jiuwu.craft.structure;

import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.runtime.RuntimeComponents;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.structure.command.StructureCommandRouter;
import emaki.jiuwu.craft.structure.config.AppConfig;
import emaki.jiuwu.craft.structure.generate.BlockEntityWriter;
import emaki.jiuwu.craft.structure.generate.GenerationFinalizer;
import emaki.jiuwu.craft.structure.generate.GenerationScheduler;
import emaki.jiuwu.craft.structure.generate.GenerationService;
import emaki.jiuwu.craft.structure.selection.SelectionManager;
import emaki.jiuwu.craft.structure.spawn.SpawnExecutor;
import emaki.jiuwu.craft.structure.spawn.SpawnQueue;
import emaki.jiuwu.craft.structure.spawn.SpawnSidecarRepository;
import emaki.jiuwu.craft.structure.template.StructureTemplateCache;
import emaki.jiuwu.craft.structure.template.TemplateRepository;

import java.util.Map;

record StructureRuntimeComponents(
        MessageService messageService,
        LanguageLoader languageLoader,
        ExecutionDispatcher executionDispatcher,
        YamlConfigLoader<AppConfig> appConfigLoader,
        BootstrapService bootstrapService,
        TemplateRepository templateRepository,
        StructureTemplateCache templateCache,
        SpawnSidecarRepository spawnSidecars,
        SpawnQueue spawnQueue,
        SpawnExecutor spawnExecutor,
        SelectionManager selectionManager,
        GenerationScheduler generationScheduler,
        BlockEntityWriter blockEntityWriter,
        GenerationFinalizer generationFinalizer,
        GenerationService generationService,
        StructureCommandRouter commandRouter
) implements RuntimeComponents {

    @Override
    public Map<Class<?>, Object> services() {
        return RuntimeComponents.services(
                RuntimeComponents.component(MessageService.class, messageService),
                RuntimeComponents.component(LanguageLoader.class, languageLoader),
                RuntimeComponents.component(ExecutionDispatcher.class, executionDispatcher),
                RuntimeComponents.component(BootstrapService.class, bootstrapService),
                RuntimeComponents.component(TemplateRepository.class, templateRepository),
                RuntimeComponents.component(GenerationService.class, generationService));
    }
}
