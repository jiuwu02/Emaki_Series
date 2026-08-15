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
import emaki.jiuwu.craft.mobs.loader.MobDefinitionLoader;
import emaki.jiuwu.craft.mobs.loader.MobSpec;
import emaki.jiuwu.craft.mobs.service.ComponentMapper;
import emaki.jiuwu.craft.mobs.service.MobFactory;
import emaki.jiuwu.craft.mobs.service.MobIdentifier;
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
    private static final List<String> DEFAULT_DATA_FILES = List.of("mobs/example_zombie.yml");
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
        Map<String, MobSpec> initial = Map.of();
        var mobRegistry = new AtomicReference<>(initial);
        var mobFactory = new MobFactory(mobRegistry::get, componentMapper, mobIdentifier);
        return new MobsRuntimeComponents(messageService, languageLoader, executionDispatcher,
                definitionLoader, componentMapper, mobIdentifier, mobFactory,
                appConfigLoader, bootstrapService, mobRegistry);
    }

    int reload(EmakiMobsPlugin plugin) {
        var components = plugin.components();
        var loaded = components.mobDefinitionLoader().loadAll();
        components.mobRegistry().set(loaded);
        return loaded.size();
    }
}
