package emaki.jiuwu.craft.mobs;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSource;
import emaki.jiuwu.craft.corelib.api.action.CoreStageRegistration;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapHooks;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.runtime.AbstractLifecycleCoordinator;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.mobs.action.source.AttackerSource;
import emaki.jiuwu.craft.mobs.action.source.KillerSource;
import emaki.jiuwu.craft.mobs.action.source.TargetSource;
import emaki.jiuwu.craft.mobs.action.source.VictimSource;
import emaki.jiuwu.craft.mobs.action.stage.SummonMobStage;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigCommitGate;
import emaki.jiuwu.craft.mobs.config.AppConfig;
import emaki.jiuwu.craft.mobs.config.AppConfigParser;
import emaki.jiuwu.craft.mobs.config.MobsConfigPrecheckContributor;
import emaki.jiuwu.craft.mobs.listener.MobDropHandler;
import emaki.jiuwu.craft.mobs.listener.MobTriggerListener;
import emaki.jiuwu.craft.mobs.loader.MobDefinitionYamlLoader;
import emaki.jiuwu.craft.mobs.loader.MobSpec;
import emaki.jiuwu.craft.mobs.loot.LootTableDefinition;
import emaki.jiuwu.craft.mobs.loot.LootTableYamlLoader;
import emaki.jiuwu.craft.mobs.service.ComponentMapper;
import emaki.jiuwu.craft.mobs.service.MobFactory;
import emaki.jiuwu.craft.mobs.loader.SpawnRuleLoader;
import emaki.jiuwu.craft.mobs.service.MobIdentifier;
import emaki.jiuwu.craft.mobs.display.BossBarManager;
import emaki.jiuwu.craft.mobs.provider.MobAttributeRegistrar;
import emaki.jiuwu.craft.mobs.service.MobRefreshService;
import emaki.jiuwu.craft.mobs.skill.HealthPhaseTracker;
import emaki.jiuwu.craft.mobs.skill.MobSkillExecutor;
import emaki.jiuwu.craft.mobs.spawner.AutonomousSpawnHandler;
import emaki.jiuwu.craft.mobs.spawner.NaturalSpawnHandler;
import emaki.jiuwu.craft.mobs.spawner.SpawnRule;
import emaki.jiuwu.craft.mobs.spawner.SpawnRuleDispatcher;
import emaki.jiuwu.craft.mobs.spawner.TypeOverrideApplicator;
import emaki.jiuwu.craft.mobs.apiimpl.DefaultMobExtensions;
import emaki.jiuwu.craft.mobs.threat.ThreatTableManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
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

    private final List<CoreStageRegistration> customActionRegistrations = new ArrayList<>();

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
        var mobIdentifier = new MobIdentifier(plugin);
        var componentMapper = new ComponentMapper(mobIdentifier);
        var definitionLoader = new MobDefinitionYamlLoader(plugin);
        var lootTableLoader = new LootTableYamlLoader(plugin);
        Map<String, MobSpec> initialMobs = Map.of();
        var mobRegistry = new AtomicReference<>(initialMobs);
        Map<String, LootTableDefinition> initialLoot = Map.of();
        var lootRegistry = new AtomicReference<>(initialLoot);
        var mobFactory = new MobFactory(mobRegistry::get, componentMapper, mobIdentifier,
                plugin, executionDispatcher);
        var mobDropHandler = new MobDropHandler(mobIdentifier, mobRegistry::get, lootRegistry::get, plugin.getLogger());
        var naturalSpawnHandler = new NaturalSpawnHandler(mobIdentifier, mobFactory);
        var autonomousSpawnHandler = new AutonomousSpawnHandler(plugin, mobIdentifier, mobFactory);
        var spawnRuleDispatcher = new SpawnRuleDispatcher(naturalSpawnHandler, autonomousSpawnHandler);
        var spawnRuleLoader = new SpawnRuleLoader(plugin);
        var spawnRegistry = new AtomicReference<>(List.<SpawnRule>of());
        var mobSkillExecutor = new MobSkillExecutor(plugin, mobRegistry::get, plugin.getLogger());
        var healthPhaseTracker = new HealthPhaseTracker();
        var mobTriggerListener = new MobTriggerListener(mobIdentifier, mobSkillExecutor, healthPhaseTracker);
        var typeOverrideApplicator = new TypeOverrideApplicator(
                mobRegistry::get, componentMapper, mobIdentifier);
        var mobRefreshService = new MobRefreshService(
                mobIdentifier, componentMapper, mobRegistry::get);
        var threatTableManager = new ThreatTableManager(
                plugin, executionDispatcher, mobIdentifier, mobRegistry::get);
        var bossBarManager = new BossBarManager(
                plugin, executionDispatcher, mobIdentifier, mobRegistry::get);
        var mobExtensions = new DefaultMobExtensions();
        var mobAttributeRegistrar = new MobAttributeRegistrar(plugin, mobIdentifier, mobRegistry::get);
        mobFactory.setSkillExecutor(mobSkillExecutor);
        mobFactory.setBossBarManager(bossBarManager);

        return new MobsRuntimeComponents(messageService, languageLoader, executionDispatcher,
                definitionLoader, componentMapper, mobIdentifier, mobFactory,
                appConfigLoader, bootstrapService, mobRegistry,
                lootTableLoader, lootRegistry, mobDropHandler,
                spawnRuleLoader, spawnRegistry, spawnRuleDispatcher,
                naturalSpawnHandler, autonomousSpawnHandler,
                mobSkillExecutor, healthPhaseTracker, mobTriggerListener,
                typeOverrideApplicator, mobRefreshService, threatTableManager, bossBarManager,
                mobAttributeRegistrar, mobExtensions);
    }

    int reload(EmakiMobsPlugin plugin) {
        var components = plugin.components();
        ContentSnapshot previous = new ContentSnapshot(
                components.mobRegistry().get(),
                components.lootRegistry().get(),
                components.spawnRegistry().get());
        ConfigCommitGate.Result gate = ConfigCommitGate.commit(
                components.messageService(),
                MobsConfigPrecheckContributor.MODULE,
                () -> previous,
                () -> {
                    components.mobDefinitionLoader().load();
                    components.lootTableLoader().load();
                    components.mobRegistry().set(components.mobDefinitionLoader().all());
                    components.lootRegistry().set(components.lootTableLoader().all());
                    components.spawnRegistry().set(components.spawnRuleLoader().loadAll());
                    return previous;
                },
                restored -> restoreContent(components, restored));
        if (gate.rejected()) {
            return components.mobRegistry().get().size();
        }
        components.mobSkillExecutor().invalidate();
        components.healthPhaseTracker().clearAll();
        var loadedRules = components.spawnRegistry().get();
        components.spawnRuleDispatcher().reload(loadedRules);
        components.mobRefreshService().refreshAll();
        components.mobAttributeRegistrar().register();
        components.mobExtensions().notifyReload();
        return components.mobRegistry().get().size();
    }

    private void restoreContent(MobsRuntimeComponents components, ContentSnapshot restored) {
        if (restored == null) {
            return;
        }
        components.mobRegistry().set(restored.mobs());
        components.lootRegistry().set(restored.loot());
        components.spawnRegistry().set(restored.rules());
    }

    private record ContentSnapshot(Map<String, MobSpec> mobs,
            Map<String, LootTableDefinition> loot,
            List<SpawnRule> rules) { }

    void registerCustomActions(EmakiMobsPlugin plugin) {
        closeCustomActionRegistrations();
        MobsRuntimeComponents components = plugin.components();
        if (components == null) {
            return;
        }

        int registered = 0;
        for (CoreActionSource source : List.of(
                new AttackerSource(),
                new KillerSource(),
                new VictimSource(),
                new TargetSource())) {
            CoreStageRegistration registration = EmakiCoreLibApi.registerActionSource(plugin, source);
            if (rememberRegistration(plugin, "source", source.id(), registration)) {
                registered++;
            }
        }

        SummonMobStage summonMobStage = new SummonMobStage(
                components.mobRegistry()::get,
                components.mobFactory());
        CoreStageRegistration summonRegistration = EmakiCoreLibApi.registerActionStage(plugin, summonMobStage);
        if (rememberRegistration(plugin, "stage", summonMobStage.id(), summonRegistration)) {
            registered++;
        }

        if (!EmakiCoreLibApi.onStageRegistryRebuilt(plugin, () -> registerCustomActions(plugin))) {
            components.messageService().warning("console.custom_action_rebuild_hook_failed");
        }
        components.messageService().info("console.custom_actions_registered", Map.of(
                "registered", registered,
                "expected", 5));
    }

    void unregisterCustomActions() {
        closeCustomActionRegistrations();
    }

    private boolean rememberRegistration(EmakiMobsPlugin plugin,
            String kind,
            String id,
            CoreStageRegistration registration) {
        if (registration.successful()) {
            customActionRegistrations.add(registration);
            return true;
        }
        plugin.components().messageService().warning("console.custom_action_registration_failed", Map.of(
                "kind", kind,
                "id", id,
                "reason", registration.reasonKey()));
        return false;
    }

    private void closeCustomActionRegistrations() {
        for (CoreStageRegistration registration : customActionRegistrations) {
            registration.close();
        }
        customActionRegistrations.clear();
    }
}
