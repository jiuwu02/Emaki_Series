package emaki.jiuwu.craft.strengthen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapHooks;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.integration.ReflectivePdcAttributeGateway;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.runtime.AbstractLifecycleCoordinator;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.strengthen.config.AppConfig;
import emaki.jiuwu.craft.strengthen.loader.StrengthenRecipeLoader;
import emaki.jiuwu.craft.strengthen.service.ChanceCalculator;
import emaki.jiuwu.craft.strengthen.service.StrengthenActionCoordinator;
import emaki.jiuwu.craft.strengthen.service.StrengthenAttemptService;
import emaki.jiuwu.craft.strengthen.service.StrengthenEconomyService;
import emaki.jiuwu.craft.strengthen.service.StrengthenGuiService;
import emaki.jiuwu.craft.strengthen.service.StrengthenRefreshService;
import emaki.jiuwu.craft.strengthen.service.StrengthenRecipeResolver;
import emaki.jiuwu.craft.strengthen.service.StrengthenSnapshotBuilder;

final class StrengthenLifecycleCoordinator extends AbstractLifecycleCoordinator<EmakiStrengthenPlugin, StrengthenRuntimeComponents> {

    private static final String DEFAULT_PREFIX = "<gray>[ <gradient:#F2C46D:#C9703D>装备强化</gradient> ]</gray>";
    private static final String PDC_ATTRIBUTE_SOURCE_ID = "strengthen";
    private static final List<String> VERSIONED_FILES = List.of("config.yml", "lang/zh_CN.yml");

    @Override
    public StrengthenRuntimeComponents initialize(EmakiStrengthenPlugin plugin) {
        EmakiCoreLibPlugin coreLibPlugin = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
        YamlConfigLoader<AppConfig> appConfigLoader = new YamlConfigLoader<>(
                plugin,
                "config.yml",
                "version",
                AppConfig::defaults,
                this::parseAppConfig
        );
        appConfigLoader.load();
        LanguageLoader languageLoader = new LanguageLoader(plugin, "lang", "lang", "zh_CN", "zh_CN");
        StrengthenRecipeLoader recipeLoader = new StrengthenRecipeLoader(plugin);
        GuiTemplateLoader guiTemplateLoader = new GuiTemplateLoader(plugin);
        MessageService messageService = new MessageService(plugin, languageLoader, DEFAULT_PREFIX, false);
        BootstrapService bootstrapService = new BootstrapService(
                plugin,
                messageService,
                VERSIONED_FILES,
                staticFiles(plugin),
                List.of(),
                List.of(),
                List.of(),
                new BootstrapHooks() {
                }
        );
        GuiService guiService = new GuiService(plugin, coreLibPlugin.asyncTaskScheduler(), coreLibPlugin.performanceMonitor());
        ReflectivePdcAttributeGateway pdcAttributeGateway = new ReflectivePdcAttributeGateway(plugin);
        syncPdcAttributeRegistration(pdcAttributeGateway, PDC_ATTRIBUTE_SOURCE_ID);
        StrengthenRecipeResolver recipeResolver = new StrengthenRecipeResolver(
                plugin,
                coreLibPlugin.itemAssemblyService(),
                coreLibPlugin.itemSourceService()
        );
        ChanceCalculator chanceCalculator = new ChanceCalculator();
        StrengthenEconomyService economyService = new StrengthenEconomyService(
                plugin,
                coreLibPlugin::economyManager,
                coreLibPlugin.itemSourceService()
        );
        StrengthenSnapshotBuilder snapshotBuilder = new StrengthenSnapshotBuilder();
        StrengthenActionCoordinator actionCoordinator = new StrengthenActionCoordinator(plugin, coreLibPlugin::actionExecutor);
        StrengthenAttemptService attemptService = new StrengthenAttemptService(
                plugin,
                recipeResolver,
                chanceCalculator,
                economyService,
                snapshotBuilder,
                actionCoordinator,
                coreLibPlugin.itemAssemblyService()
        );
        StrengthenRefreshService refreshService = new StrengthenRefreshService(plugin, attemptService);
        StrengthenGuiService strengthenGuiService = new StrengthenGuiService(plugin, guiService, attemptService);
        return new StrengthenRuntimeComponents(
                appConfigLoader,
                languageLoader,
                recipeLoader,
                guiTemplateLoader,
                messageService,
                bootstrapService,
                guiService,
                coreLibPlugin.itemSourceService(),
                pdcAttributeGateway,
                recipeResolver,
                chanceCalculator,
                economyService,
                snapshotBuilder,
                actionCoordinator,
                attemptService,
                refreshService,
                strengthenGuiService
        );
    }

    public void reload(EmakiStrengthenPlugin plugin, boolean closeInventories) {
        if (closeInventories && plugin.strengthenGuiService() != null) {
            for (var player : Bukkit.getOnlinePlayers()) {
                if (plugin.strengthenGuiService().getSession(player) != null) {
                    player.closeInventory();
                }
            }
            plugin.strengthenGuiService().clearAllSessions();
        }
        plugin.languageLoader().load();
        plugin.appConfigLoader().load();
        plugin.languageLoader().setLanguage(plugin.appConfig().language());
        plugin.recipeLoader().load();
        plugin.guiTemplateLoader().load();
        syncPdcAttributeRegistration(plugin.pdcAttributeGateway(), PDC_ATTRIBUTE_SOURCE_ID);
        plugin.refreshService().refreshOnlinePlayers();
    }

    public void shutdown(EmakiStrengthenPlugin plugin) {
        if (plugin.pdcAttributeGateway() != null) {
            plugin.pdcAttributeGateway().shutdown();
        }
        if (plugin.strengthenGuiService() != null) {
            plugin.strengthenGuiService().clearAllSessions();
        }
    }

    private AppConfig parseAppConfig(YamlSection configuration) {
        if (configuration == null || configuration.getKeys(false).isEmpty()) {
            return AppConfig.defaults();
        }
        AppConfig defaults = AppConfig.defaults();
        return new AppConfig(
                configuration.getString("language", defaults.language()),
                configuration.getString("version", defaults.configVersion()),
                configuration.getInt("local_broadcast_radius", defaults.localBroadcastRadius()),
                parseIntegerList(configuration.getSection("broadcast.local_stars"), configuration.get("broadcast.local_stars"), defaults.localBroadcastStars()),
                parseIntegerList(configuration.getSection("broadcast.global_stars"), configuration.get("broadcast.global_stars"), defaults.globalBroadcastStars()),
                parseSuccessRates(configuration.getSection("success_rates"), defaults.successRates())
        );
    }

    private Map<Integer, Double> parseSuccessRates(YamlSection section, Map<Integer, Double> fallback) {
        Map<Integer, Double> rates = new java.util.LinkedHashMap<>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Integer star = Numbers.tryParseInt(key, null);
                Double value = Numbers.tryParseDouble(section.get(key), null);
                if (star != null && value != null) {
                    rates.put(star, value);
                }
            }
        }
        return rates.isEmpty() ? fallback : Map.copyOf(rates);
    }

    private java.util.List<Integer> parseIntegerList(YamlSection section, Object raw, java.util.Set<Integer> fallback) {
        java.util.List<Integer> values = new java.util.ArrayList<>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Integer value = Numbers.tryParseInt(section.get(key), null);
                if (value != null) {
                    values.add(value);
                }
            }
        } else if (raw instanceof Iterable<?> iterable) {
            for (Object entry : iterable) {
                Integer value = Numbers.tryParseInt(entry, null);
                if (value != null) {
                    values.add(value);
                }
            }
        }
        if (values.isEmpty()) {
            return java.util.List.copyOf(fallback);
        }
        return java.util.List.copyOf(values);
    }

    private List<String> staticFiles(EmakiStrengthenPlugin plugin) {
        List<String> files = new ArrayList<>();
        files.addAll(YamlFiles.listResourcePaths(plugin, "gui"));
        files.addAll(YamlFiles.listResourcePaths(plugin, "recipes"));
        return List.copyOf(files);
    }

}
