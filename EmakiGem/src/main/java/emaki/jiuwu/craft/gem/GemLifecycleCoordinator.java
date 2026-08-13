package emaki.jiuwu.craft.gem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.api.assembly.EmakiNamespaceDefinition;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapHooks;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.condition.ConditionBlock;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigCommitGate;
import emaki.jiuwu.craft.corelib.condition.ConditionGroup;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.gem.integration.GemAttributeBridge;
import emaki.jiuwu.craft.gem.integration.GemAttributeBridgeHolder;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.runtime.AbstractLifecycleCoordinator;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.gem.config.AppConfig;
import emaki.jiuwu.craft.gem.listener.GemItemObtainListener;
import emaki.jiuwu.craft.gem.loader.GemItemLoader;
import emaki.jiuwu.craft.gem.loader.GemLoader;
import emaki.jiuwu.craft.gem.loader.GemResonanceLoader;
import emaki.jiuwu.craft.gem.model.SocketOpenerConfig;
import emaki.jiuwu.craft.gem.service.GemActionCoordinator;
import emaki.jiuwu.craft.gem.service.GemEconomyService;
import emaki.jiuwu.craft.gem.service.GemExtractService;
import emaki.jiuwu.craft.gem.service.GemGuiMode;
import emaki.jiuwu.craft.gem.service.GemGuiService;
import emaki.jiuwu.craft.gem.service.GemInlayService;
import emaki.jiuwu.craft.gem.service.GemItemFactory;
import emaki.jiuwu.craft.gem.service.GemItemMatcher;
import emaki.jiuwu.craft.gem.service.GemOperationJournal;
import emaki.jiuwu.craft.gem.service.GemPdcAttributeWriter;
import emaki.jiuwu.craft.gem.service.GemResonanceService;
import emaki.jiuwu.craft.gem.service.GemSnapshotBuilder;
import emaki.jiuwu.craft.gem.service.GemStateService;
import emaki.jiuwu.craft.gem.service.SocketOpenerService;

final class GemLifecycleCoordinator extends AbstractLifecycleCoordinator<EmakiGemPlugin, GemRuntimeComponents> {

    private static final String DEFAULT_PREFIX = "<gray>[ <gradient:#22D3EE:#A855F7>EmakiGem</gradient> ]</gray>";
    private static final String PDC_ATTRIBUTE_SOURCE_ID = "gem";
    private static final List<String> VERSIONED_FILES = List.of("config.yml", "lang/zh_CN.yml", "lang/en_US.yml");
    private static final List<String> EXTRA_DIRECTORIES = List.of("data");

    @Override
    public GemRuntimeComponents initialize(EmakiGemPlugin plugin) {
        EmakiCoreLibPlugin coreLibPlugin = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
        EmakiScheduling scheduling = EmakiCoreLibApi.scheduling();
        var executionDispatcher = coreLibPlugin.executionDispatcher();
        registerAssemblyLayer(coreLibPlugin);

        YamlConfigLoader<AppConfig> appConfigLoader = new YamlConfigLoader<>(
                plugin,
                "config.yml",
                AppConfig::defaults,
                this::parseAppConfig
        );
        appConfigLoader.load();
        LanguageLoader languageLoader = new LanguageLoader(plugin, "lang", "lang", "zh_CN", "zh_CN");
        GemLoader gemLoader = new GemLoader(plugin);
        GemItemLoader gemItemLoader = new GemItemLoader(plugin);
        GuiTemplateLoader guiTemplateLoader = new GuiTemplateLoader(plugin);
        MessageService messageService = new MessageService(plugin, languageLoader, DEFAULT_PREFIX, true);
        BootstrapService bootstrapService = new BootstrapService(
                plugin,
                messageService,
                VERSIONED_FILES,
                staticFiles(plugin),
                defaultDataFiles(plugin),
                EXTRA_DIRECTORIES,
                new BootstrapHooks() {
                    @Override
                    public boolean shouldInstallDefaultData() {
                        return shouldReleaseDefaultData(plugin);
                    }
                }
        );
        GuiService guiService = new GuiService(plugin, executionDispatcher, coreLibPlugin.asyncTaskScheduler(), coreLibPlugin.performanceMonitor(), coreLibPlugin.guiBackend());
        GemAttributeBridge pdcAttributeGateway = new GemAttributeBridgeHolder(plugin.getLogger());
        pdcAttributeGateway.syncRegistration(PDC_ATTRIBUTE_SOURCE_ID);
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
        GemActionCoordinator actionCoordinator = new GemActionCoordinator(plugin, plugin.actionLines());
        SocketOpenerService socketOpenerService = new SocketOpenerService(
                plugin,
                itemMatcher,
                itemFactory,
                stateService,
                actionCoordinator,
                scheduling
        );
        GemInlayService inlayService = new GemInlayService(
                plugin,
                itemMatcher,
                stateService,
                economyService,
                actionCoordinator,
                scheduling
        );
        GemExtractService extractService = new GemExtractService(
                plugin,
                itemMatcher,
                itemFactory,
                stateService,
                economyService,
                actionCoordinator,
                scheduling
        );
        GemGuiService gemGuiService = new GemGuiService(plugin, guiService, scheduling);
        return new GemRuntimeComponents(
                scheduling,
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
                gemGuiService
        );
    }

    public void reload(EmakiGemPlugin plugin, boolean closeInventories) {
        if (closeInventories) {
            plugin.gemGuiService().clearAllSessions();
        }
        reloadNow(plugin);
    }

    private void reloadNow(EmakiGemPlugin plugin) {
        // The definition loaders run inside the candidate step because the gem precheck reads their
        // issue lists; gating before they load would certify a candidate nobody parsed yet.
        ConfigCommitGate.Result gate = ConfigCommitGate.commit(
                plugin.messageService(),
                "gem",
                plugin.appConfigLoader()::current,
                () -> {
                    plugin.languageLoader().load();
                    AppConfig candidate = plugin.appConfigLoader().load();
                    plugin.gemLoader().load();
                    plugin.gemItemLoader().load();
                    plugin.guiTemplateLoader().load();
                    return candidate;
                },
                plugin.appConfigLoader()::overrideCurrent);
        if (gate.rejected()) {
            // Previous AppConfig is active again and no candidate value reached a runtime service.
            return;
        }
        plugin.languageLoader().setLanguage(plugin.appConfig().language());
        plugin.itemMatcher().refresh();
        GemOperationJournal.forPlugin(plugin, plugin.scheduling()).recover(plugin.economyService());
        loadResonances(plugin);
        plugin.pdcAttributeGateway().syncRegistration(PDC_ATTRIBUTE_SOURCE_ID);
        refreshOnlinePlayerItems(plugin);
        plugin.messageService().info("console.pdc_source_registered", Map.of("source", PDC_ATTRIBUTE_SOURCE_ID));
        plugin.messageService().info("console.gems_loaded", Map.of(
                "count", String.valueOf(plugin.gemLoader().all().size())
        ));
    }

    public CompletableFuture<Void> reloadAsync(EmakiGemPlugin plugin, boolean closeInventories, Consumer<String> progressListener) {
        AsyncTaskScheduler scheduler = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class).asyncTaskScheduler();
        CompletableFuture<Void> sessionsClosed = closeInventories
                ? plugin.gemGuiService().clearAllSessionsAsync()
                : CompletableFuture.completedFuture(null);
        if (scheduler == null) {
            return sessionsClosed.thenRun(() -> reloadNow(plugin));
        }

        notifyProgress(progressListener, "Closing active gem sessions...");

        return sessionsClosed.thenCompose(_ -> {
            notifyProgress(progressListener, "Loading configuration files...");
            return runReloadStageAsync(scheduler, new ReloadStageConfig<>(
                    "gem", "config-load", "Loading configs...", progressListener,
                    () -> {
                        ConfigCommitGate.Result gate = ConfigCommitGate.commit(
                                plugin.messageService(),
                                "gem",
                                plugin.appConfigLoader()::current,
                                () -> {
                                    plugin.languageLoader().load();
                                    AppConfig candidate = plugin.appConfigLoader().load();
                                    plugin.gemLoader().load();
                                    plugin.gemItemLoader().load();
                                    plugin.guiTemplateLoader().load();
                                    return candidate;
                                },
                                plugin.appConfigLoader()::overrideCurrent);
                        if (gate.rejected()) {
                            // Aborts the stage so the apply step never runs; AppConfig is already restored.
                            throw new IllegalStateException("Gem config precheck failed: "
                                    + String.join("; ", gate.failures()));
                        }
                    },
                    null,
                    (stage, ex) -> plugin.getLogger().warning(
                            "[Reload] Stage " + stage + " failed: " + ex.getMessage())
            )).thenCompose(_ -> {
                notifyProgress(progressListener, "Applying configuration...");
                return submitGlobalStage(plugin, () -> {
                    plugin.languageLoader().setLanguage(plugin.appConfig().language());
                    plugin.itemMatcher().refresh();
                    GemOperationJournal.forPlugin(plugin, plugin.scheduling()).recover(plugin.economyService());
                    loadResonances(plugin);
                    plugin.pdcAttributeGateway().syncRegistration(PDC_ATTRIBUTE_SOURCE_ID);
                    refreshOnlinePlayerItems(plugin);
                    plugin.messageService().info("console.pdc_source_registered", Map.of("source", PDC_ATTRIBUTE_SOURCE_ID));
                    plugin.messageService().info("console.gems_loaded", Map.of(
                            "count", String.valueOf(plugin.gemLoader().all().size())
                    ));
                    notifyProgress(progressListener, "Reload complete.");
                });
            });
        });
    }

    private CompletableFuture<Void> submitGlobalStage(EmakiGemPlugin plugin, Runnable stage) {
        try {
            return plugin.scheduling().submitGlobal(plugin, () -> {
                stage.run();
                return null;
            }).thenApply(ignored -> null);
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private void refreshOnlinePlayerItems(EmakiGemPlugin plugin) {
        // Inventory refresh is per-player entity work: the global region thread owns no player,
        // so every refresh is dispatched to its own owner thread instead of running inline here.
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null) {
                continue;
            }
            if (plugin.scheduling() != null && plugin.scheduling().ownsEntity(player)) {
                GemItemObtainListener.refreshInventory(plugin, player);
                continue;
            }
            if (plugin.scheduling() == null) {
                plugin.getLogger().warning("EmakiGem skipped gem item refresh for " + player.getName()
                        + ": caller thread does not own the player and no scheduling is available.");
                continue;
            }
            var task = plugin.scheduling().runForEntity(plugin, player,
                    () -> GemItemObtainListener.refreshInventory(plugin, player), null);
            if (task.cancelled()) {
                plugin.getLogger().warning("EmakiGem failed to reroute gem item refresh for " + player.getName()
                        + ": entity task scheduling was rejected.");
            }
        }
    }

    public void shutdown(EmakiGemPlugin plugin) {
        EmakiCoreLibPlugin coreLibPlugin = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
        coreLibPlugin.namespaceRegistry().unregister("gem");
        if (plugin.pdcAttributeGateway() != null) {
            plugin.pdcAttributeGateway().shutdown();
        }
        if (plugin.gemGuiService() != null) {
            plugin.gemGuiService().clearAllSessionsAsync().exceptionally(throwable -> {
                plugin.getLogger().warning("Failed to close gem GUI sessions during shutdown: " + throwable.getMessage());
                return null;
            });
        }
    }

    private void loadResonances(EmakiGemPlugin plugin) {
        GemResonanceLoader loader = plugin.resonanceLoader();
        if (loader == null) {
            loader = new GemResonanceLoader(plugin);
            plugin.setResonanceLoader(loader);
        }
        loader.load();
        GemResonanceService service = plugin.resonanceService();
        if (service == null) {
            service = new GemResonanceService(loader);
            plugin.setResonanceService(service);
        } else {
            service.refresh(loader);
        }
    }

    private AppConfig parseAppConfig(YamlSection configuration) {
        if (configuration == null || configuration.getKeys(false).isEmpty()) {
            return AppConfig.defaults();
        }
        AppConfig defaults = AppConfig.defaults();
        YamlSection inlaySuccess = configuration.getSection("inlay_success");
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
        Map<Integer, Double> perLevel = new LinkedHashMap<>();
        YamlSection byLevel = section.getSection("by_level");
        if (byLevel != null) {
            for (String key : byLevel.getKeys(false)) {
                Integer level = Numbers.tryParseInt(key, null);
                Double chance = Numbers.tryParseDouble(byLevel.get(key), null);
                if (level != null && chance != null) {
                    perLevel.put(level, chance);
                }
            }
        }
        double defaultChance = section.getDouble("default_chance", defaults.defaultChance());
        return new AppConfig.InlaySuccessConfig(
                section.getBoolean("enabled", defaults.enabled()),
                defaultChance,
                section.getString("rate_formula", defaults.rateFormula()),
                section.getString("failure_action", defaults.failureAction()),
                perLevel
        );
    }

    private AppConfig.GuiSettings parseGuiSettings(YamlSection section, AppConfig.GuiSettings defaults) {
        if (section == null || section.getKeys(false).isEmpty()) {
            return defaults;
        }
        String configuredMode = section.getString("default_mode", defaults.defaultMode().name());
        GemGuiMode defaultMode = switch (configuredMode == null ? "" : configuredMode.toLowerCase(Locale.ROOT)) {
            case "open", "open_socket", "socket_open" -> GemGuiMode.OPEN_SOCKET;
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
        ConditionBlock condition = ConditionBlock.fromConfig(section, defaults.invalidAsFailure(), false);
        ConditionGroup conditionGroup = condition.group();
        return new AppConfig.ConditionConfig(
                conditionGroup,
                conditionGroup.conditionType(),
                conditionGroup.requiredCount(),
                condition.invalidAsFailure()
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
        files.addAll(YamlFiles.listResourcePaths(plugin, "resonances"));
        return List.copyOf(files);
    }

    private void registerAssemblyLayer(EmakiCoreLibPlugin coreLibPlugin) {
        coreLibPlugin.namespaceRegistry().register(new EmakiNamespaceDefinition("gem", 300, "Gem"));
    }

}
