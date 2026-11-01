package emaki.jiuwu.craft.dummy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.async.AsyncFileService;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.plugin.AbstractConfigurableEmakiPlugin;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.dummy.command.DummyCommandAdapter;
import emaki.jiuwu.craft.dummy.command.DummyCommandRouter;
import emaki.jiuwu.craft.dummy.config.AppConfig;
import emaki.jiuwu.craft.dummy.listener.ServerEventBridge;
import emaki.jiuwu.craft.dummy.log.DummyLogger;
import emaki.jiuwu.craft.dummy.log.LogLevel;
import emaki.jiuwu.craft.dummy.scenario.ScenarioEngine;
import emaki.jiuwu.craft.dummy.session.DummyManager;
import emaki.jiuwu.craft.dummy.transport.DummyTransport;
import emaki.jiuwu.craft.dummy.transport.mcpl.McplTransport;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

public class EmakiDummyPlugin extends AbstractConfigurableEmakiPlugin<AppConfig> {

    private static final String ROOT_COMMAND = "emdum";
    private static final int RECONCILE_PERIOD_TICKS = 100;

    private final AtomicBoolean shutdownStarted = new AtomicBoolean(false);

    private YamlConfigLoader<AppConfig> appConfigLoader;
    private LanguageLoader languageLoader;
    private MessageService messageService;
    private DummyLogger dummyLogger;
    private DummyManager manager;
    private ScenarioEngine engine;
    private AsyncFileService.FileScope fileScope;
    private ServerEventBridge bridge;
    private DummyCommandRouter router;
    private boolean runtimeInitialized;
    private ScheduledTask movementTask;
    private ScheduledTask scenarioTask;
    private ScheduledTask reconcileTask;

    public EmakiDummyPlugin() {
        super(AppConfig::defaults);
    }

    @Override
    public void onEnable() {
        shutdownStarted.set(false);
        languageLoader = new LanguageLoader(this);
        languageLoader.load();
        messageService = new MessageService(this, languageLoader, "EmakiDummy");
        appConfigLoader = new YamlConfigLoader<>(this, "config.yml", AppConfig::defaults,
                AppConfig::parse);
        appConfigLoader.load();
        AppConfig appConfig = appConfigLoader.current();
        messageService.info("console.plugin_starting");
        dummyLogger = new DummyLogger(dataPath("logs", "dummy"), appConfig.logRetentionDays(),
                appConfig.perDummyLogFiles());
        int removed = dummyLogger.open();
        if (removed > 0) {
            messageService.info("console.log_retention_removed", Map.of("count", removed));
        }
        DummyTransport transport = new McplTransport();
        ExecutionDispatcher dispatcher = null;
        AsyncFileService.FileScope scope = null;
        try {
            EmakiCoreLibPlugin coreLib = EmakiCoreLibPlugin.lookup();
            dispatcher = coreLib.executionDispatcher();
            scope = coreLib.asyncFileScope(this);
        } catch (RuntimeException failure) {
            dummyLogger.log(LogLevel.ERROR, "runtime",
                    "corelib_services_unavailable reason=" + failure.getMessage());
        }
        fileScope = scope;
        manager = new DummyManager(this, this::appConfig, dummyLogger, transport, dispatcher);
        Path dataDirectory = getDataFolder().getAbsoluteFile().toPath();
        Path serverRoot = dataDirectory.getParent().getParent();
        engine = new ScenarioEngine(this::appConfig, manager, dummyLogger, serverRoot, scope);
        bridge = new ServerEventBridge(this, manager, dummyLogger, engine);
        engine.bridge(bridge);
        getServer().getPluginManager().registerEvents(bridge, this);
        extractScenarios();
        int loaded = engine.reload(dataPath("scenarios"));
        messageService.info("console.scenarios_loaded", Map.of("count", loaded));
        for (String failure : engine.loadFailures()) {
            int separator = failure.indexOf(": ");
            String file = separator >= 0 ? failure.substring(0, separator) : failure;
            String reason = separator >= 0 ? failure.substring(separator + 2) : "";
            messageService.warning("console.scenario_load_failed",
                    Map.of("file", file, "reason", reason));
        }
        router = new DummyCommandRouter(messageService, manager, engine);
        registerCommandHandler();
        startTimers(appConfig);
        runtimeInitialized = true;
        messageService.info("console.plugin_started", Map.of(
                "mode", appConfig.connectionMode(),
                "host", "velocity".equalsIgnoreCase(appConfig.connectionMode())
                        ? appConfig.velocityHost()
                        : appConfig.host(),
                "port", "velocity".equalsIgnoreCase(appConfig.connectionMode())
                        ? appConfig.velocityPort()
                        : appConfig.port()));
    }

    @Override
    public void onDisable() {
        if (!shutdownStarted.compareAndSet(false, true)) {
            return;
        }
        cancelTask(movementTask);
        cancelTask(scenarioTask);
        cancelTask(reconcileTask);
        movementTask = null;
        scenarioTask = null;
        reconcileTask = null;
        AsyncFileService.FileScope scope = fileScope;
        fileScope = null;
        if (scope != null) {
            AsyncFileService.DrainResult result = scope.sealAndDrain(2L, TimeUnit.SECONDS);
            if (!result.drained() && dummyLogger != null) {
                dummyLogger.log(LogLevel.ERROR, "runtime",
                        "async_file_drain_timeout pending=" + result.pendingOperations());
            }
        }
        if (engine != null) {
            engine.shutdown();
            engine.tick();
            engine = null;
        }
        if (manager != null) {
            manager.stopAll();
            manager.closeTransport();
            manager = null;
        }
        bridge = null;
        router = null;
        if (dummyLogger != null) {
            dummyLogger.close();
            dummyLogger = null;
        }
        runtimeInitialized = false;
        if (messageService != null) {
            messageService.info("console.plugin_stopped");
        }
    }

    @Override
    public YamlConfigLoader<AppConfig> appConfigLoader() {
        return appConfigLoader;
    }

    public MessageService messageService() {
        return messageService;
    }

    public LanguageLoader languageLoader() {
        return languageLoader;
    }

    public DummyManager dummyManager() {
        return manager;
    }

    public ScenarioEngine scenarioEngine() {
        return engine;
    }

    public boolean runtimeInitialized() {
        return runtimeInitialized;
    }

    private void registerCommandHandler() {
        registerCommand(ROOT_COMMAND, "EmakiDummy 命令", List.of("dummy", "emdummy"),
                new DummyCommandAdapter(router, "emakidummy.use"));
    }

    private void startTimers(AppConfig appConfig) {
        long movementPeriod = Math.max(1, appConfig.movementIntervalTicks());
        long scenarioPeriod = Math.max(1, appConfig.scenarioPollIntervalTicks());
        movementTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(this,
                task -> manager.tickWalks(), movementPeriod, movementPeriod);
        scenarioTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(this,
                task -> tickScenario(), scenarioPeriod, scenarioPeriod);
        reconcileTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(this,
                task -> manager.tickReconcile(),
                RECONCILE_PERIOD_TICKS, RECONCILE_PERIOD_TICKS);
    }

    private void tickScenario() {
        ScenarioEngine activeEngine = engine;
        if (activeEngine == null) {
            return;
        }
        activeEngine.tick();
        ScenarioEngine.CompletedResult completed = activeEngine.drainCompleted();
        if (completed == null || messageService == null) {
            return;
        }
        CommandSender recipient = activeEngine.drainRecipient();
        if (recipient == null) {
            return;
        }
        messageService.send(recipient, "command.scenario.result", Map.of(
                "id", completed.id(),
                "result", completed.result(),
                "passed", completed.passed(),
                "failed", completed.failed(),
                "elapsed", completed.elapsedMs()));
    }

    private void extractScenarios() {
        Path directory = dataPath("scenarios");
        try {
            Files.createDirectories(directory);
        } catch (IOException failure) {
            messageService.warning("console.scenario_load_failed", Map.of(
                    "file", "scenarios", "reason", String.valueOf(failure.getMessage())));
            return;
        }
        for (String resource : YamlFiles.listResourcePaths(this, "scenarios")) {
            String fileName = Path.of(resource).getFileName().toString();
            Path target = directory.resolve(fileName);
            try (InputStream bundled = getResource(resource)) {
                if (bundled == null) {
                    continue;
                }
                Files.copy(bundled, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException failure) {
                messageService.warning("console.scenario_load_failed", Map.of(
                        "file", fileName, "reason", String.valueOf(failure.getMessage())));
            }
        }
    }

    private static void cancelTask(ScheduledTask task) {
        if (task != null) {
            task.cancel();
        }
    }
}
