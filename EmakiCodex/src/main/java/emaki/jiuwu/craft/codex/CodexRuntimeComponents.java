package emaki.jiuwu.craft.codex;

import java.util.Map;

import emaki.jiuwu.craft.codex.advancement.AdvancementJsonBuilder;
import emaki.jiuwu.craft.codex.advancement.AdvancementPlatform;
import emaki.jiuwu.craft.codex.advancement.AdvancementRegistrar;
import emaki.jiuwu.craft.codex.advancement.AdvancementService;
import emaki.jiuwu.craft.codex.advancement.loader.AdvancementPageLoader;
import emaki.jiuwu.craft.codex.advancement.packet.AdvancementPacketGateway;
import emaki.jiuwu.craft.codex.advancement.trigger.AdvancementTriggerRegistry;
import emaki.jiuwu.craft.codex.advancement.trigger.CodexTriggerService;
import emaki.jiuwu.craft.codex.config.AppConfig;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.runtime.RuntimeComponents;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;

record CodexRuntimeComponents(YamlConfigLoader<AppConfig> appConfigLoader,
        LanguageLoader languageLoader,
        MessageService messageService,
        BootstrapService bootstrapService,
        AdvancementPageLoader advancementPageLoader,
        AdvancementPlatform advancementPlatform,
        AdvancementJsonBuilder advancementJsonBuilder,
        AdvancementRegistrar advancementRegistrar,
        AdvancementService advancementService,
        AdvancementPacketGateway advancementPacketGateway,
        AdvancementTriggerRegistry advancementTriggerRegistry,
        CodexTriggerService triggerService,
        ExecutionDispatcher executionDispatcher,
        ThreadOwnership threadOwnership) implements RuntimeComponents {

    @Override
    public Map<Class<?>, Object> services() {
        return RuntimeComponents.services(
                RuntimeComponents.component(YamlConfigLoader.class, appConfigLoader),
                RuntimeComponents.component(LanguageLoader.class, languageLoader),
                RuntimeComponents.component(MessageService.class, messageService),
                RuntimeComponents.component(BootstrapService.class, bootstrapService),
                RuntimeComponents.component(AdvancementPageLoader.class, advancementPageLoader),
                RuntimeComponents.component(AdvancementPlatform.class, advancementPlatform),
                RuntimeComponents.component(AdvancementJsonBuilder.class, advancementJsonBuilder),
                RuntimeComponents.component(AdvancementRegistrar.class, advancementRegistrar),
                RuntimeComponents.component(AdvancementService.class, advancementService),
                RuntimeComponents.component(AdvancementPacketGateway.class, advancementPacketGateway),
                RuntimeComponents.component(AdvancementTriggerRegistry.class, advancementTriggerRegistry),
                RuntimeComponents.component(CodexTriggerService.class, triggerService),
                RuntimeComponents.component(ExecutionDispatcher.class, executionDispatcher),
                RuntimeComponents.component(ThreadOwnership.class, threadOwnership)
        );
    }
}
