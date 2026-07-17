package emaki.jiuwu.craft.strengthen;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.strengthen.script.ScriptStrengthenModuleApi;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.assembly.EmakiNamespaceDefinition;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapHooks;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.integration.PdcAttributeGateway;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.runtime.AbstractLifecycleCoordinator;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
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

    private static final String DEFAULT_PREFIX = "<gray>[ <gradient:#A78BFA:#60A5FA>EmakiStrengthen</gradient> ]</gray>";
    private static final String PDC_ATTRIBUTE_SOURCE_ID = "strengthen";
    private static final List<String> VERSIONED_FILES = List.of("config.yml", "lang/zh_CN.yml", "lang/en_US.yml");
    private static final List<String> STATIC_FILES = List.of("gui/strengthen_gui.yml");
    private static final List<String> DEFAULT_DATA_FILES = List.of("recipes/example_branch_recipe.yml", "recipes/example_recipe.yml");

    @Override
    public StrengthenRuntimeComponents initialize(EmakiStrengthenPlugin plugin) {
        EmakiCoreLibPlugin coreLibPlugin = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
        registerAssemblyLayer(coreLibPlugin);
        registerScriptModule(coreLibPlugin);
        releaseBundledScripts(coreLibPlugin, plugin);
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
        GuiService guiService = new GuiService(plugin, coreLibPlugin.asyncTaskScheduler(), coreLibPlugin.performanceMonitor(), coreLibPlugin.guiBackend());
        PdcAttributeGateway pdcAttributeGateway = new PdcAttributeGateway(plugin);
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
        if (!freezeAndDrain(plugin, closeInventories, "reload")) {
            resumeAccepting(plugin);
            return;
        }
        try {
            plugin.languageLoader().load();
            plugin.appConfigLoader().load();
            plugin.languageLoader().setLanguage(plugin.appConfig().language());
            plugin.recipeLoader().load();
            StrengthenRecipeResolver.clearPatternCache();
            plugin.guiTemplateLoader().load();
            syncPdcAttributeRegistration(plugin.pdcAttributeGateway(), PDC_ATTRIBUTE_SOURCE_ID);
            plugin.messageService().info("console.pdc_source_registered", Map.of("source", PDC_ATTRIBUTE_SOURCE_ID));
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
                    plugin.languageLoader().load();
                    plugin.appConfigLoader().load();
                    plugin.recipeLoader().load();
                    plugin.guiTemplateLoader().load();
                },
                null, (stage, ex) -> plugin.getLogger().warning("[Reload] Stage " + stage + " failed: " + ex.getMessage())
        )).thenCompose(_ -> {
            notifyProgress(progressListener, "Applying configuration...");
            return scheduler.callSync("strengthen-reload-apply", () -> {
                plugin.languageLoader().setLanguage(plugin.appConfig().language());
                StrengthenRecipeResolver.clearPatternCache();
                syncPdcAttributeRegistration(plugin.pdcAttributeGateway(), PDC_ATTRIBUTE_SOURCE_ID);
                plugin.messageService().info("console.pdc_source_registered", Map.of("source", PDC_ATTRIBUTE_SOURCE_ID));
                plugin.refreshService().refreshOnlinePlayers();
                plugin.messageService().info("console.recipes_loaded", Map.of(
                        "count", String.valueOf(plugin.recipeLoader().all().size())
                ));
                notifyProgress(progressListener, "Reload complete.");
                return null;
            });
        })).whenComplete((_, _) -> resumeAccepting(plugin));
    }

    public void shutdown(EmakiStrengthenPlugin plugin) {
        freezeAndDrain(plugin, true, "shutdown");
        EmakiCoreLibPlugin coreLibPlugin = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
        coreLibPlugin.javaScriptRegistrationTracker().unregisterOwner(plugin);
        coreLibPlugin.scriptModuleRegistry().unregister("strengthen");
        coreLibPlugin.namespaceRegistry().unregister("strengthen");
        if (plugin.pdcAttributeGateway() != null) {
            plugin.pdcAttributeGateway().shutdown();
        }
    }

    private boolean freezeAndDrain(EmakiStrengthenPlugin plugin, boolean closeInventories, String phase) {
        if (plugin == null) {
            return false;
        }
        if (plugin.attemptService() != null) {
            plugin.attemptService().freezeAccepting();
        }
        if (plugin.javaScriptResultHookRegistry() != null) {
            plugin.javaScriptResultHookRegistry().freeze();
        }
        boolean attemptsDrained = plugin.attemptService() == null
                || plugin.attemptService().drain(5L, TimeUnit.SECONDS);
        boolean hooksDrained = plugin.javaScriptResultHookRegistry() == null
                || plugin.javaScriptResultHookRegistry().drain(5L, TimeUnit.SECONDS);
        if (closeInventories && plugin.strengthenGuiService() != null) {
            plugin.strengthenGuiService().clearAllSessions();
        }
        if (!attemptsDrained || !hooksDrained) {
            plugin.getLogger().severe("[Lifecycle] Strengthen drain incomplete | phase=" + phase
                    + " | attempts=" + (plugin.attemptService() == null ? Map.of() : plugin.attemptService().journalSnapshot())
                    + " | resultHooks=" + (plugin.javaScriptResultHookRegistry() == null
                            ? 0 : plugin.javaScriptResultHookRegistry().inFlightCount()));
            return false;
        }
        return true;
    }

    private void resumeAccepting(EmakiStrengthenPlugin plugin) {
        if (plugin == null) {
            return;
        }
        if (plugin.javaScriptResultHookRegistry() != null) {
            plugin.javaScriptResultHookRegistry().resume();
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

    private void registerAssemblyLayer(EmakiCoreLibPlugin coreLibPlugin) {
        coreLibPlugin.namespaceRegistry().register(new EmakiNamespaceDefinition("strengthen", 200, "Strengthen"));
    }

    private void registerScriptModule(EmakiCoreLibPlugin coreLibPlugin) {
        coreLibPlugin.scriptModuleRegistry().register("strengthen", context -> new ScriptStrengthenModuleApi(JavaPlugin.getPlugin(EmakiStrengthenPlugin.class), context));
    }

    private void releaseBundledScripts(EmakiCoreLibPlugin coreLibPlugin, EmakiStrengthenPlugin plugin) {
        coreLibPlugin.releaseBundledScripts(plugin, "examples", false, List.of("strengthen_success.js"));
    }

}
