package emaki.jiuwu.craft.gem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapHooks;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.integration.PdcAttributeGateway;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.runtime.AbstractLifecycleCoordinator;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import emaki.jiuwu.craft.gem.config.AppConfig;
import emaki.jiuwu.craft.gem.loader.GemItemLoader;
import emaki.jiuwu.craft.gem.loader.GemLoader;
import emaki.jiuwu.craft.gem.model.SocketOpenerConfig;
import emaki.jiuwu.craft.gem.service.GemActionCoordinator;
import emaki.jiuwu.craft.gem.service.GemEconomyService;
import emaki.jiuwu.craft.gem.service.GemExtractService;
import emaki.jiuwu.craft.gem.service.GemGuiMode;
import emaki.jiuwu.craft.gem.service.GemGuiService;
import emaki.jiuwu.craft.gem.service.GemInlayService;
import emaki.jiuwu.craft.gem.service.GemItemFactory;
import emaki.jiuwu.craft.gem.service.GemItemMatcher;
import emaki.jiuwu.craft.gem.service.GemPdcAttributeWriter;
import emaki.jiuwu.craft.gem.service.GemSnapshotBuilder;
import emaki.jiuwu.craft.gem.service.GemStateService;
import emaki.jiuwu.craft.gem.service.GemUpgradeService;
import emaki.jiuwu.craft.gem.service.SocketOpenerService;

final class GemLifecycleCoordinator extends AbstractLifecycleCoordinator<EmakiGemPlugin, GemRuntimeComponents> {

    private static final String DEFAULT_PREFIX = "<gray>[ <gradient:#B84DFF:#FF8A3D>装备宝石</gradient> ]</gray>";
    private static final String PDC_ATTRIBUTE_SOURCE_ID = "gem";
    private static final List<String> VERSIONED_FILES = List.of("config.yml", "lang/zh_CN.yml");
    private static final List<String> EXTRA_DIRECTORIES = List.of("data");

    @Override
    public GemRuntimeComponents initialize(EmakiGemPlugin plugin) {
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
        GemLoader gemLoader = new GemLoader(plugin);
        GemItemLoader gemItemLoader = new GemItemLoader(plugin);
        GuiTemplateLoader guiTemplateLoader = new GuiTemplateLoader(plugin);
        MessageService messageService = new MessageService(plugin, languageLoader, DEFAULT_PREFIX, false);
        BootstrapService bootstrapService = new BootstrapService(
                plugin,
                messageService,
                VERSIONED_FILES,
                staticFiles(plugin),
                defaultDataFiles(plugin),
                EXTRA_DIRECTORIES,
                List.of(),
                new BootstrapHooks() {
                    @Override
                    public boolean shouldInstallDefaultData() {
                        return shouldReleaseDefaultData(plugin);
                    }
                }
        );
        GuiService guiService = new GuiService(plugin, coreLibPlugin.asyncTaskScheduler(), coreLibPlugin.performanceMonitor());
        PdcAttributeGateway pdcAttributeGateway = new PdcAttributeGateway(plugin);
        syncPdcAttributeRegistration(pdcAttributeGateway, PDC_ATTRIBUTE_SOURCE_ID);
        GemItemMatcher itemMatcher = new GemItemMatcher(plugin, coreLibPlugin.itemSourceService());
        GemItemFactory itemFactory = new GemItemFactory(plugin, coreLibPlugin.itemSourceService());
        GemSnapshotBuilder snapshotBuilder = new GemSnapshotBuilder(plugin);
        GemPdcAttributeWriter pdcAttributeWriter = new GemPdcAttributeWriter(plugin, pdcAttributeGateway);
        GemStateService stateService = new GemStateService(
                plugin,
                itemMatcher,
                snapshotBuilder,
                pdcAttributeWriter,
                coreLibPlugin.itemAssemblyService()
        );
        GemEconomyService economyService = new GemEconomyService(
                plugin,
                coreLibPlugin::economyManager,
                coreLibPlugin.itemSourceService()
        );
        GemActionCoordinator actionCoordinator = new GemActionCoordinator(plugin, coreLibPlugin::actionExecutor);
        SocketOpenerService socketOpenerService = new SocketOpenerService(
                plugin,
                itemMatcher,
                itemFactory,
                stateService,
                actionCoordinator
        );
        GemInlayService inlayService = new GemInlayService(
                plugin,
                itemMatcher,
                stateService,
                economyService,
                actionCoordinator
        );
        GemExtractService extractService = new GemExtractService(
                plugin,
                itemMatcher,
                itemFactory,
                stateService,
                economyService,
                actionCoordinator
        );
        GemUpgradeService upgradeService = new GemUpgradeService(
                plugin,
                itemFactory,
                economyService,
                actionCoordinator
        );
        GemGuiService gemGuiService = new GemGuiService(plugin, guiService);
        return new GemRuntimeComponents(
                appConfigLoader,
                languageLoader,
                gemLoader,
                gemItemLoader,
                guiTemplateLoader,
                messageService,
                bootstrapService,
                guiService,
                coreLibPlugin.itemSourceService(),
                pdcAttributeGateway,
                itemMatcher,
                itemFactory,
                snapshotBuilder,
                pdcAttributeWriter,
                stateService,
                economyService,
                actionCoordinator,
                socketOpenerService,
                inlayService,
                extractService,
                upgradeService,
                gemGuiService
        );
    }

    public void reload(EmakiGemPlugin plugin, boolean closeInventories) {
        if (closeInventories) {
            Bukkit.getOnlinePlayers().forEach(player -> player.closeInventory());
        }
        plugin.languageLoader().load();
        plugin.appConfigLoader().load();
        plugin.languageLoader().setLanguage(plugin.appConfig().language());
        plugin.gemLoader().load();
        plugin.gemItemLoader().load();
        plugin.guiTemplateLoader().load();
        plugin.itemMatcher().refresh();
        syncPdcAttributeRegistration(plugin.pdcAttributeGateway(), PDC_ATTRIBUTE_SOURCE_ID);
    }

    public void shutdown(EmakiGemPlugin plugin) {
        if (plugin.pdcAttributeGateway() != null) {
            plugin.pdcAttributeGateway().shutdown();
        }
        if (plugin.gemGuiService() != null) {
            plugin.gemGuiService().clearAllSessions();
        }
    }

    private AppConfig parseAppConfig(YamlSection configuration) {
        if (configuration == null || configuration.getKeys(false).isEmpty()) {
            return AppConfig.defaults();
        }
        AppConfig defaults = AppConfig.defaults();
        YamlSection inlaySuccess = configuration.getSection("inlay_success");
        YamlSection upgrade = configuration.getSection("upgrade");
        YamlSection permission = configuration.getSection("permission");
        YamlSection numberFormat = configuration.getSection("number_format");
        YamlSection gui = configuration.getSection("gui");
        YamlSection condition = configuration.getSection("condition");
        return new AppConfig(
                configuration.getString("language", defaults.language()),
                configuration.getString("version", defaults.configVersion()),
                configuration.getBoolean("release_default_data", defaults.releaseDefaultData()),
                parseSocketOpeners(configuration.getSection("socket_openers")),
                parseInlaySuccess(inlaySuccess, defaults.inlaySuccess()),
                new AppConfig.UpgradeSettings(
                        parseUpgradeSuccessRates(upgrade, defaults.upgrade().globalSuccessRates()),
                        upgrade == null ? defaults.upgrade().globalFailurePenalty()
                                : upgrade.getString("global_failure_penalty", defaults.upgrade().globalFailurePenalty())
                ),
                numberFormat == null ? defaults.numberFormat() : numberFormat.getString("default", defaults.numberFormat()),
                permission != null && permission.getBoolean("op_bypass", defaults.opBypass()),
                parseGuiSettings(gui, defaults.gui()),
                parseConditionConfig(condition, defaults.condition())
        );
    }

    private Map<String, SocketOpenerConfig> parseSocketOpeners(YamlSection section) {
        if (section == null || section.getKeys(false).isEmpty()) {
            return Map.of();
        }
        Map<String, SocketOpenerConfig> openers = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            SocketOpenerConfig config = SocketOpenerConfig.fromConfig(key, section.getSection(key));
            if (config != null) {
                openers.put(config.id(), config);
            }
        }
        return Map.copyOf(openers);
    }

    private AppConfig.InlaySuccessConfig parseInlaySuccess(YamlSection section, AppConfig.InlaySuccessConfig defaults) {
        if (section == null || section.getKeys(false).isEmpty()) {
            return defaults;
        }
        Map<Integer, Double> perTier = new LinkedHashMap<>();
        YamlSection byTier = section.getSection("by_tier");
        if (byTier != null) {
            for (String key : byTier.getKeys(false)) {
                Integer tier = Numbers.tryParseInt(key, null);
                Double chance = Numbers.tryParseDouble(byTier.get(key), null);
                if (tier != null && chance != null) {
                    perTier.put(tier, chance);
                }
            }
        }
        double defaultChance = section.getDouble("default_chance", defaults.defaultChance());
        return new AppConfig.InlaySuccessConfig(
                section.getBoolean("enabled", defaults.enabled()),
                defaultChance,
                section.getString("rate_formula", defaults.rateFormula()),
                section.getString("failure_action", defaults.failureAction()),
                perTier
        );
    }

    private Map<Integer, Double> parseUpgradeSuccessRates(YamlSection section, Map<Integer, Double> defaults) {
        Map<Integer, Double> rates = new LinkedHashMap<>();
        if (defaults != null) {
            rates.putAll(defaults);
        }
        if (section == null) {
            return Map.copyOf(rates);
        }
        YamlSection configured = section.getSection("global_success_rates");
        if (configured == null) {
            return Map.copyOf(rates);
        }
        for (String key : configured.getKeys(false)) {
            Integer level = Numbers.tryParseInt(key, null);
            Double chance = Numbers.tryParseDouble(configured.get(key), null);
            if (level != null && chance != null) {
                rates.put(level, chance);
            }
        }
        return Map.copyOf(rates);
    }

    private AppConfig.GuiSettings parseGuiSettings(YamlSection section, AppConfig.GuiSettings defaults) {
        if (section == null || section.getKeys(false).isEmpty()) {
            return defaults;
        }
        String configuredMode = section.getString("default_mode", defaults.defaultMode().name());
        GemGuiMode defaultMode = switch (configuredMode == null ? "" : configuredMode.toLowerCase()) {
            case "extract" -> GemGuiMode.EXTRACT;
            case "open", "open_socket", "socket_open" -> GemGuiMode.OPEN_SOCKET;
            case "upgrade" -> GemGuiMode.UPGRADE;
            default -> GemGuiMode.INLAY;
        };
        return new AppConfig.GuiSettings(
                defaultMode,
                section.getBoolean("save_on_close", defaults.saveOnClose())
        );
    }

    private AppConfig.ConditionConfig parseConditionConfig(YamlSection section, AppConfig.ConditionConfig defaults) {
        if (section == null || section.getKeys(false).isEmpty()) {
            return defaults;
        }
        return new AppConfig.ConditionConfig(
                section.getStringList("conditions"),
                section.getString("condition_type", defaults.conditionType()),
                section.getInt("required_count", defaults.requiredCount()),
                section.getBoolean("invalid_as_failure", defaults.invalidAsFailure())
        );
    }

    private boolean shouldReleaseDefaultData(EmakiGemPlugin plugin) {
        YamlSection configuration = YamlFiles.load(plugin.dataPath("config.yml").toFile());
        return configuration.getBoolean("release_default_data", true);
    }

    private List<String> staticFiles(EmakiGemPlugin plugin) {
        List<String> files = new ArrayList<>();
        files.addAll(YamlFiles.listResourcePaths(plugin, "gui"));
        files.addAll(YamlFiles.listResourcePaths(plugin, "conditions"));
        return List.copyOf(files);
    }

    private List<String> defaultDataFiles(EmakiGemPlugin plugin) {
        List<String> files = new ArrayList<>();
        files.addAll(YamlFiles.listResourcePaths(plugin, "gems"));
        files.addAll(YamlFiles.listResourcePaths(plugin, "items"));
        return List.copyOf(files);
    }

}
