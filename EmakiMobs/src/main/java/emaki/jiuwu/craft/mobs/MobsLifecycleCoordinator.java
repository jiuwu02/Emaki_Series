package emaki.jiuwu.craft.mobs;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapHooks;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.runtime.AbstractLifecycleCoordinator;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.mobs.config.AppConfig;
import emaki.jiuwu.craft.mobs.config.AppConfigParser;
import emaki.jiuwu.craft.mobs.listener.MobDropHandler;
import emaki.jiuwu.craft.mobs.loader.MobDefinitionLoader;
import emaki.jiuwu.craft.mobs.loader.MobSpec;
import emaki.jiuwu.craft.mobs.loot.LootTableDefinition;
import emaki.jiuwu.craft.mobs.loot.LootTableDefinitionLoader;
import emaki.jiuwu.craft.mobs.service.ComponentMapper;
import emaki.jiuwu.craft.mobs.service.MobFactory;
import emaki.jiuwu.craft.mobs.loader.SpawnRuleLoader;
import emaki.jiuwu.craft.mobs.service.MobIdentifier;
import emaki.jiuwu.craft.mobs.spawner.BiomeSpawnHandler;
import emaki.jiuwu.craft.mobs.spawner.CustomSpawnHandler;
import emaki.jiuwu.craft.mobs.spawner.DayIntervalSpawnHandler;
import emaki.jiuwu.craft.mobs.spawner.NaturalSpawnHandler;
import emaki.jiuwu.craft.mobs.spawner.PlayerRelativeSpawnHandler;
import emaki.jiuwu.craft.mobs.spawner.SpawnConditionEvaluator;
import emaki.jiuwu.craft.mobs.spawner.SpawnRule;
import emaki.jiuwu.craft.mobs.spawner.SpawnRuleDispatcher;
import emaki.jiuwu.craft.mobs.spawner.StructureSpawnHandler;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

final class MobsLifecycleCoordinator
        extends AbstractLifecycleCoordinator<EmakiMobsPlugin, MobsRuntimeComponents> {

    private static final String DEFAULT_PREFIX =
            "<gray>[<gradient:#86EFAC:#34D399>EmakiMobs</gradient>]</gray> ";
    private static final List<String> VERSIONED_FILES = List.of("config.yml");
    private static final List<String> STATIC_FILES = List.of();
    private static final List<String> DEFAULT_DATA_FILES =
            List.of("mobs/example_zombie.yml", "loot_tables/example_zombie.yml",
                    "spawn_rules/overworld_elites.yml");
    private static final List<String> EXTRA_DIRECTORIES =
            List.of("mobs", "loot_tables", "spawn_rules");

    @Override
    public MobsRuntimeComponents initialize(EmakiMobsPlugin plugin) {
        var coreLibPlugin = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
        var appConfigLoader = new YamlConfigLoader<>(plugin,
                "config.yml", AppConfig::defaults, AppConfigParser::parse);
        appConfigLoader.load();
        var languageLoader = new LanguageLoader(plugin, "lang", "lang", "zh_CN", "zh_CN");
        var messageService = new MessageService(plugin, languageLoader, DEFAULT_PREFIX, true);
        languageLoader.load();
        var bootstrapService = new BootstrapService(plugin, messageService,
                VERSIONED_FILES, STATIC_FILES, DEFAULT_DATA_FILES, EXTRA_DIRECTORIES,
                new BootstrapHooks() {
                    @Override
                    public boolean shouldInstallDefaultData() {
                        AppConfig current = appConfigLoader.current();
                        return current == null || current.releaseDefaultData();
                    }
                });
        var executionDispatcher = coreLibPlugin.executionDispatcher();
        var componentMapper = new ComponentMapper();
        var mobIdentifier = new MobIdentifier(plugin);
        var definitionLoader = new MobDefinitionLoader(plugin);
        var lootTableLoader = new LootTableDefinitionLoader(plugin);
        Map<String, MobSpec> initialMobs = Map.of();
        var mobRegistry = new AtomicReference<>(initialMobs);
        Map<String, LootTableDefinition> initialLoot = Map.of();
        var lootRegistry = new AtomicReference<>(initialLoot);
        var mobFactory = new MobFactory(mobRegistry::get, componentMapper, mobIdentifier);
        var mobDropHandler = new MobDropHandler(mobIdentifier, mobRegistry::get, lootRegistry::get, plugin.getLogger());
        var spawnConditionEvaluator = new SpawnConditionEvaluator(mobIdentifier);
        var naturalSpawnHandler = new NaturalSpawnHandler(spawnConditionEvaluator, mobFactory);
        var structureSpawnHandler = new StructureSpawnHandler(plugin, spawnConditionEvaluator, mobFactory);
        var playerRelativeSpawnHandler = new PlayerRelativeSpawnHandler(plugin, spawnConditionEvaluator, mobFactory);
        var dayIntervalSpawnHandler = new DayIntervalSpawnHandler(plugin, spawnConditionEvaluator, mobFactory);
        var customSpawnHandler = new CustomSpawnHandler(plugin, spawnConditionEvaluator, mobFactory);
        var biomeSpawnHandler = new BiomeSpawnHandler(plugin, spawnConditionEvaluator, mobFactory);
        var spawnRuleDispatcher = new SpawnRuleDispatcher(naturalSpawnHandler, structureSpawnHandler,
                playerRelativeSpawnHandler, dayIntervalSpawnHandler, customSpawnHandler, biomeSpawnHandler);
        var spawnRuleLoader = new SpawnRuleLoader(plugin);
        var spawnRegistry = new AtomicReference<>(List.<SpawnRule>of());
        return new MobsRuntimeComponents(messageService, languageLoader, executionDispatcher,
                definitionLoader, componentMapper, mobIdentifier, mobFactory,
                appConfigLoader, bootstrapService, mobRegistry,
                lootTableLoader, lootRegistry, mobDropHandler,
                spawnRuleLoader, spawnRegistry, spawnRuleDispatcher,
                naturalSpawnHandler, structureSpawnHandler);
    }

    int reload(EmakiMobsPlugin plugin) {
        var components = plugin.components();
        var loadedMobs = components.mobDefinitionLoader().loadAll();
        components.mobRegistry().set(loadedMobs);
        var loadedLoot = components.lootTableLoader().loadAll();
        components.lootRegistry().set(loadedLoot);
        var loadedRules = components.spawnRuleLoader().loadAll();
        components.spawnRegistry().set(loadedRules);
        components.spawnRuleDispatcher().reload(loadedRules);
        return loadedMobs.size();
    }
}
