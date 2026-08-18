package emaki.jiuwu.craft.strengthen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.api.assembly.EmakiNamespaceDefinition;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapHooks;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigCommitGate;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.strengthen.integration.StrengthenAttributeBridge;
import emaki.jiuwu.craft.strengthen.integration.StrengthenAttributeBridgeHolder;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.pdc.PdcService;
import emaki.jiuwu.craft.corelib.runtime.AbstractLifecycleCoordinator;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.strengthen.config.AppConfig;
import emaki.jiuwu.craft.strengthen.enhancement.EnhancementAttemptService;
import emaki.jiuwu.craft.strengthen.enhancement.affix.AffixGuiService;
import emaki.jiuwu.craft.strengthen.enhancement.affix.AffixLayerCodec;
import emaki.jiuwu.craft.strengthen.enhancement.affix.AffixSelectionService;
import emaki.jiuwu.craft.strengthen.enhancement.affix.AffixTargetProvider;
import emaki.jiuwu.craft.strengthen.enhancement.pity.InMemoryPityStateStore;
import emaki.jiuwu.craft.strengthen.enhancement.recipe.EnhancementRecipeLoader;
import emaki.jiuwu.craft.strengthen.enhancement.target.EnhancementTargetRegistry;
import emaki.jiuwu.craft.strengthen.enhancement.target.EquipmentTargetProvider;
import emaki.jiuwu.craft.strengthen.loader.StrengthenRecipeLoader;
import emaki.jiuwu.craft.strengthen.service.ChanceCalculator;
import emaki.jiuwu.craft.strengthen.service.StrengthenActionCoordinator;
import emaki.jiuwu.craft.strengthen.service.StrengthenAttemptService;
import emaki.jiuwu.craft.strengthen.service.StrengthenEconomyService;
import emaki.jiuwu.craft.strengthen.service.StrengthenGuiService;
import emaki.jiuwu.craft.strengthen.service.StrengthenRefreshService;
import emaki.jiuwu.craft.strengthen.service.StrengthenRecipeResolver;
import emaki.jiuwu.craft.strengthen.service.StrengthenSnapshotBuilder;
import emaki.jiuwu.craft.strengthen.service.StrengthenTransferService;

final class StrengthenLifecycleCoordinator extends AbstractLifecycleCoordinator<EmakiStrengthenPlugin, StrengthenRuntimeComponents> {

    private static final String DEFAULT_PREFIX = "<gray>[ <gradient:#3636F5:#E02492>EmakiStrengthen</gradient> ]</gray>";
    private static final String PDC_ATTRIBUTE_SOURCE_ID = "strengthen";
    private static final String AFFIX_PDC_ATTRIBUTE_SOURCE_ID = AffixTargetProvider.ATTRIBUTE_SOURCE_ID;
    private static final List<String> VERSIONED_FILES = List.of("config.yml", "lang/zh_CN.yml", "lang/en_US.yml");
    private static final List<String> STATIC_FILES = List.of("gui/strengthen_gui.yml", "gui/affix_strengthen_gui.yml");
    private static final List<String> DEFAULT_DATA_FILES = List.of("recipes/example_branch_recipe.yml", "recipes/example_recipe.yml",
            "enhancement_recipes/example_enhancement_recipe.yml", "enhancement_recipes/example_affix_recipe.yml");

    @Override
    public StrengthenRuntimeComponents initialize(EmakiStrengthenPlugin plugin) {
        EmakiCoreLibPlugin coreLibPlugin = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
        ExecutionDispatcher executionDispatcher = coreLibPlugin.executionDispatcher();
        ThreadOwnership threadOwnership = coreLibPlugin.threadOwnership();
        registerAssemblyLayer(coreLibPlugin);
        YamlConfigLoader<AppConfig> appConfigLoader = new YamlConfigLoader<>(
                plugin,
                "config.yml",
                AppConfig::defaults,
                this::parseAppConfig
        );
        appConfigLoader.load();
        LanguageLoader languageLoader = new LanguageLoader(plugin, "lang", "lang", "zh_CN", "zh_CN");
        StrengthenRecipeLoader recipeLoader = new StrengthenRecipeLoader(plugin);
        GuiTemplateLoader guiTemplateLoader = new GuiTemplateLoader(plugin);
        MessageService messageService = new MessageService(plugin, languageLoader, DEFAULT_PREFIX, true);
        BootstrapService bootstrapService = new BootstrapService(
                plugin,
                messageService,
                VERSIONED_FILES,
                STATIC_FILES,
                DEFAULT_DATA_FILES,
                List.of(),
                new BootstrapHooks() {
                    @Override
                    public boolean shouldInstallDefaultData() {
                        return plugin.appConfig().releaseDefaultData();
                    }
                }
        );
        GuiService guiService = new GuiService(plugin, executionDispatcher, coreLibPlugin.asyncTaskScheduler(), coreLibPlugin.performanceMonitor(), coreLibPlugin.guiBackend());
        StrengthenAttributeBridge pdcAttributeGateway = new StrengthenAttributeBridgeHolder(plugin.getLogger());
        pdcAttributeGateway.syncRegistration(PDC_ATTRIBUTE_SOURCE_ID);
        StrengthenAttributeBridge affixAttributeGateway = new StrengthenAttributeBridgeHolder(plugin.getLogger());
        affixAttributeGateway.syncRegistration(AFFIX_PDC_ATTRIBUTE_SOURCE_ID);
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
        StrengthenActionCoordinator actionCoordinator = new StrengthenActionCoordinator(plugin);
        StrengthenAttemptService attemptService = new StrengthenAttemptService(
                plugin,
                recipeResolver,
                chanceCalculator,
                economyService,
                snapshotBuilder,
                actionCoordinator,
                coreLibPlugin.itemAssemblyService(),
                threadOwnership
        );
        StrengthenTransferService transferService = new StrengthenTransferService(plugin, attemptService);
        StrengthenRefreshService refreshService = new StrengthenRefreshService(plugin, attemptService, executionDispatcher);
        StrengthenGuiService strengthenGuiService = new StrengthenGuiService(plugin, guiService, attemptService, threadOwnership);
        EnhancementRecipeLoader enhancementRecipeLoader = new EnhancementRecipeLoader(plugin);
        EnhancementTargetRegistry enhancementTargetRegistry = new EnhancementTargetRegistry();
        enhancementTargetRegistry.register(new EquipmentTargetProvider(plugin));
        AffixLayerCodec affixLayerCodec = new AffixLayerCodec(new PdcService("emaki_strengthen", "pdc", plugin.debugLogger()));
        AffixSelectionService affixSelectionService = new AffixSelectionService(plugin, affixLayerCodec);
        AffixTargetProvider affixTargetProvider = new AffixTargetProvider(
                plugin,
                affixLayerCodec,
                affixSelectionService,
                affixAttributeGateway
        );
        enhancementTargetRegistry.register(affixTargetProvider);
        InMemoryPityStateStore pityStateStore = new InMemoryPityStateStore();
        EnhancementAttemptService enhancementAttemptService = new EnhancementAttemptService(
                plugin,
                enhancementTargetRegistry,
                pityStateStore
        );
        AffixGuiService affixGuiService = new AffixGuiService(
                plugin,
                guiService,
                threadOwnership,
                affixSelectionService,
                affixTargetProvider,
                affixLayerCodec
        );
        return new StrengthenRuntimeComponents(
                executionDispatcher,
                threadOwnership,
                appConfigLoader,
                languageLoader,
                recipeLoader,
                guiTemplateLoader,
                messageService,
                bootstrapService,
                guiService,
                coreLibPlugin.itemSourceService(),
                pdcAttributeGateway,
                affixAttributeGateway,
                recipeResolver,
                chanceCalculator,
                economyService,
                snapshotBuilder,
                actionCoordinator,
                attemptService,
                transferService,
                refreshService,
                strengthenGuiService,
                enhancementRecipeLoader,
                enhancementTargetRegistry,
                pityStateStore,
                enhancementAttemptService,
                affixSelectionService,
                affixGuiService
        );
    }

    public void reload(EmakiStrengthenPlugin plugin, boolean closeInventories) {
        if (!freezeAndDrain(plugin, closeInventories, "reload")) {
            resumeAccepting(plugin);
            return;
        }
        try {

            ConfigCommitGate.Result gate = ConfigCommitGate.commit(
                    plugin.messageService(),
                    "strengthen",
                    plugin.appConfigLoader()::current,
                    () -> {
                        plugin.languageLoader().load();
                        AppConfig candidate = plugin.appConfigLoader().load();
                        plugin.recipeLoader().load();
                        plugin.enhancementRecipeLoader().load();
                        plugin.guiTemplateLoader().load();
                        return candidate;
                    },
                    plugin.appConfigLoader()::overrideCurrent);
            if (gate.rejected()) {

                return;
            }
            plugin.languageLoader().setLanguage(plugin.appConfig().language());
            StrengthenRecipeResolver.clearPatternCache();
            // 配置可能改了保底阈值或分组，陈旧计数会按新阈值产生错误的触发判断。
            if (plugin.pityStateStore() != null) {
                plugin.pityStateStore().clear();
            }
            plugin.pdcAttributeGateway().syncRegistration(PDC_ATTRIBUTE_SOURCE_ID);
            plugin.affixAttributeGateway().syncRegistration(AFFIX_PDC_ATTRIBUTE_SOURCE_ID);
            plugin.messageService().info("console.pdc_source_registered", Map.of("source", PDC_ATTRIBUTE_SOURCE_ID));
            plugin.messageService().info("console.pdc_source_registered", Map.of("source", AFFIX_PDC_ATTRIBUTE_SOURCE_ID));
            plugin.refreshService().refreshOnlinePlayers();
            plugin.messageService().info("console.recipes_loaded", Map.of(
                    "count", String.valueOf(plugin.recipeLoader().all().size())
            ));
        } finally {
            resumeAccepting(plugin);
        }
    }

    public CompletableFuture<Void> reloadAsync(EmakiStrengthenPlugin plugin, boolean closeInventories, Consumer<String> progressListener) {
        AsyncTaskScheduler scheduler = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class).asyncTaskScheduler();
        if (scheduler == null) {
            reload(plugin, closeInventories);
            return CompletableFuture.completedFuture(null);
        }
        if (!freezeAndDrain(plugin, false, "reload-async")) {
            resumeAccepting(plugin);
            return CompletableFuture.failedFuture(new IllegalStateException("Strengthen operations did not drain"));
        }

        CompletableFuture<Void> closeSessions = closeInventories && plugin.strengthenGuiService() != null
                ? plugin.strengthenGuiService().clearAllSessionsAsync()
                : CompletableFuture.completedFuture(null);
        notifyProgress(progressListener, "Loading configuration files...");

        return closeSessions.thenCompose(_ -> runReloadStageAsync(scheduler, new ReloadStageConfig<>(
                "strengthen", "config-load", "Loading configs...", progressListener,
                () -> {
                    ConfigCommitGate.Result gate = ConfigCommitGate.commit(
                            plugin.messageService(),
                            "strengthen",
                            plugin.appConfigLoader()::current,
                            () -> {
                                plugin.languageLoader().load();
                                AppConfig candidate = plugin.appConfigLoader().load();
                                plugin.recipeLoader().load();
                                plugin.enhancementRecipeLoader().load();
                                plugin.guiTemplateLoader().load();
                                return candidate;
                            },
                            plugin.appConfigLoader()::overrideCurrent);
                    if (gate.rejected()) {

                        throw new IllegalStateException("Strengthen config precheck failed: "
                                + String.join("; ", gate.failures()));
                    }
                },
                null, (stage, ex) -> plugin.getLogger().warning("[Reload] Stage " + stage + " failed: " + ex.getMessage())
        )).thenCompose(_ -> {
            notifyProgress(progressListener, "Applying configuration...");
            return submitGlobalStage(plugin, () -> {
                plugin.languageLoader().setLanguage(plugin.appConfig().language());
                StrengthenRecipeResolver.clearPatternCache();
                // 配置可能改了保底阈值或分组，陈旧计数会按新阈值产生错误的触发判断。
                if (plugin.pityStateStore() != null) {
                    plugin.pityStateStore().clear();
                }
                plugin.pdcAttributeGateway().syncRegistration(PDC_ATTRIBUTE_SOURCE_ID);
                plugin.affixAttributeGateway().syncRegistration(AFFIX_PDC_ATTRIBUTE_SOURCE_ID);
                plugin.messageService().info("console.pdc_source_registered", Map.of("source", PDC_ATTRIBUTE_SOURCE_ID));
                plugin.messageService().info("console.pdc_source_registered", Map.of("source", AFFIX_PDC_ATTRIBUTE_SOURCE_ID));
                plugin.refreshService().refreshOnlinePlayers();
                plugin.messageService().info("console.recipes_loaded", Map.of(
                        "count", String.valueOf(plugin.recipeLoader().all().size())
                ));
                notifyProgress(progressListener, "Reload complete.");
            });
        })).whenComplete((_, _) -> resumeAccepting(plugin));
    }

    private CompletableFuture<Void> submitGlobalStage(EmakiStrengthenPlugin plugin, Runnable stage) {
        try {
            return plugin.executionDispatcher().submitGlobal(plugin, () -> {
                stage.run();
                return null;
            });
        } catch (Throwable throwable) {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(throwable);
            return failed;
        }
    }

    public void shutdown(EmakiStrengthenPlugin plugin) {
        freezeAndDrain(plugin, true, "shutdown");
        EmakiCoreLibPlugin coreLibPlugin = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
        coreLibPlugin.namespaceRegistry().unregister("strengthen");
        if (plugin.pdcAttributeGateway() != null) {
            plugin.pdcAttributeGateway().shutdown();
        }
        if (plugin.affixAttributeGateway() != null) {
            plugin.affixAttributeGateway().shutdown();
        }
    }

    private boolean freezeAndDrain(EmakiStrengthenPlugin plugin, boolean closeInventories, String phase) {
        if (plugin == null) {
            return false;
        }
        if (plugin.attemptService() != null) {
            plugin.attemptService().freezeAccepting();
        }
        boolean attemptsDrained = plugin.attemptService() == null
                || plugin.attemptService().drain(5L, TimeUnit.SECONDS);
        if (closeInventories && plugin.strengthenGuiService() != null) {
            plugin.strengthenGuiService().clearAllSessions();
        }
        if (closeInventories && plugin.affixGuiService() != null) {
            plugin.affixGuiService().clearAllSessions();
        }
        if (!attemptsDrained) {
            plugin.getLogger().severe("[Lifecycle] Strengthen drain incomplete | phase=" + phase
                    + " | attempts=" + (plugin.attemptService() == null ? Map.of() : plugin.attemptService().journalSnapshot()));
            return false;
        }
        return true;
    }

    private void resumeAccepting(EmakiStrengthenPlugin plugin) {
        if (plugin == null) {
            return;
        }
        if (plugin.attemptService() != null) {
            plugin.attemptService().resumeAccepting();
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
                configuration.getBoolean("release_default_data", defaults.releaseDefaultData()),
                configuration.getInt("local_broadcast_radius", defaults.localBroadcastRadius()),
                parseIntegerList(configuration.getSection("broadcast.local_stars"), configuration.get("broadcast.local_stars"), defaults.localBroadcastStars()),
                parseIntegerList(configuration.getSection("broadcast.global_stars"), configuration.get("broadcast.global_stars"), defaults.globalBroadcastStars()),
                parseSuccessRates(configuration.getSection("success_rates"), defaults.successRates()),
                configuration.getInt("affix.max_level", defaults.affixMaxLevel()),
                configuration.getInt("affix.capacity_max", defaults.affixCapacityMax()),
                configuration.getInt("affix.capacity_cost_per_level", defaults.affixCapacityCostPerLevel()),
                configuration.getDouble("affix.bonus_per_level", defaults.affixBonusPerLevel())
        );
    }

    private Map<Integer, Double> parseSuccessRates(YamlSection section, Map<Integer, Double> fallback) {
        Map<Integer, Double> rates = new LinkedHashMap<>();
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

    private List<Integer> parseIntegerList(YamlSection section, Object raw, Set<Integer> fallback) {
        List<Integer> values = new ArrayList<>();
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
            return List.copyOf(fallback);
        }
        return List.copyOf(values);
    }

    private void registerAssemblyLayer(EmakiCoreLibPlugin coreLibPlugin) {
        coreLibPlugin.namespaceRegistry().register(new EmakiNamespaceDefinition("strengthen", 200, "Strengthen"));
    }

}
