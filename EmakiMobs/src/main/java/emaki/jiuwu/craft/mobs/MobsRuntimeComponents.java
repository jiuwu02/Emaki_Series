package emaki.jiuwu.craft.mobs;

import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.runtime.RuntimeComponents;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.mobs.config.AppConfig;
import emaki.jiuwu.craft.mobs.listener.MobDropHandler;
import emaki.jiuwu.craft.mobs.listener.MobTriggerListener;
import emaki.jiuwu.craft.mobs.loader.MobDefinitionLoader;
import emaki.jiuwu.craft.mobs.loader.MobSpec;
import emaki.jiuwu.craft.mobs.loot.LootTableDefinition;
import emaki.jiuwu.craft.mobs.loot.LootTableDefinitionLoader;
import emaki.jiuwu.craft.mobs.service.AttributeBridge;
import emaki.jiuwu.craft.mobs.service.ComponentMapper;
import emaki.jiuwu.craft.mobs.service.MobFactory;
import emaki.jiuwu.craft.mobs.loader.SpawnRuleLoader;
import emaki.jiuwu.craft.mobs.service.MobIdentifier;
import emaki.jiuwu.craft.mobs.skill.MobSkillExecutor;
import emaki.jiuwu.craft.mobs.spawner.NaturalSpawnHandler;
import emaki.jiuwu.craft.mobs.spawner.SpawnRule;
import emaki.jiuwu.craft.mobs.spawner.SpawnRuleDispatcher;
import emaki.jiuwu.craft.mobs.spawner.StructureSpawnHandler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

record MobsRuntimeComponents(
        MessageService messageService,
        LanguageLoader languageLoader,
        ExecutionDispatcher executionDispatcher,
        MobDefinitionLoader mobDefinitionLoader,
        ComponentMapper componentMapper,
        MobIdentifier mobIdentifier,
        MobFactory mobFactory,
        YamlConfigLoader<AppConfig> appConfigLoader,
        BootstrapService bootstrapService,
        AtomicReference<Map<String, MobSpec>> mobRegistry,
        LootTableDefinitionLoader lootTableLoader,
        AtomicReference<Map<String, LootTableDefinition>> lootRegistry,
        MobDropHandler mobDropHandler,
        SpawnRuleLoader spawnRuleLoader,
        AtomicReference<List<SpawnRule>> spawnRegistry,
        SpawnRuleDispatcher spawnRuleDispatcher,
        NaturalSpawnHandler naturalSpawnHandler,
        StructureSpawnHandler structureSpawnHandler,
        AttributeBridge attributeBridge,
        MobSkillExecutor mobSkillExecutor,
        MobTriggerListener mobTriggerListener
) implements RuntimeComponents {

    @Override
    public Map<Class<?>, Object> services() {
        return RuntimeComponents.services(
                RuntimeComponents.component(MessageService.class, messageService),
                RuntimeComponents.component(LanguageLoader.class, languageLoader),
                RuntimeComponents.component(ExecutionDispatcher.class, executionDispatcher),
                RuntimeComponents.component(BootstrapService.class, bootstrapService),
                RuntimeComponents.component(MobDefinitionLoader.class, mobDefinitionLoader),
                RuntimeComponents.component(ComponentMapper.class, componentMapper),
                RuntimeComponents.component(MobIdentifier.class, mobIdentifier),
                RuntimeComponents.component(MobFactory.class, mobFactory));
    }
}
