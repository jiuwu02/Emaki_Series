package emaki.jiuwu.craft.dungeon.script;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.SourceSection;

import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.script.host.ScriptHost;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.dungeon.api.ErrorCodes;
import emaki.jiuwu.craft.dungeon.api.Result;
import emaki.jiuwu.craft.dungeon.api.ScriptId;
import emaki.jiuwu.craft.dungeon.api.ScriptTriggerHandler;
import emaki.jiuwu.craft.dungeon.audit.AuditService;
import emaki.jiuwu.craft.dungeon.config.AppConfig;
import emaki.jiuwu.craft.dungeon.config.DungeonConfig;
import emaki.jiuwu.craft.dungeon.flow.EventIngressKeys;
import emaki.jiuwu.craft.dungeon.flow.FlowControllers;
import emaki.jiuwu.craft.dungeon.flow.FlowRuntime;
import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;
import emaki.jiuwu.craft.dungeon.instance.EventIngress.IngressEvent;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceRegistry;
import emaki.jiuwu.craft.dungeon.trigger.TriggerType;

public final class ScriptRuntimeManager implements AutoCloseable {

    private static final String LANGUAGE_ID = "js";
    private static final String SCRIPTS_DIRECTORY = "scripts";
    private static final String EXAMPLE_SCRIPT = "examples/example_flow.js";
    private static final String EXAMPLE_TARGET = "example_flow.js";

    public record RegisteredTrigger(TriggerType type, String triggerId, ScriptTriggerHandler handler) {
    }

    private final Plugin plugin;
    private final MessageService messages;
    private final DebugLogger debug;
    private final Supplier<AppConfig> config;
    private final Function<String, DungeonConfig> configOf;
    private final InstanceRegistry registry;
    private final File dataFolder;
    private final AuditService audit;

    private final Map<String, String> apiSources = new ConcurrentHashMap<>();
    private final Map<String, DungeonScriptSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, RegisteredTrigger> apiTriggers = new ConcurrentHashMap<>();

    private volatile Engine engine;
    private volatile ScheduledExecutorService watchdog;
    private volatile boolean started;
    private volatile boolean engineAvailable;

    public ScriptRuntimeManager(Plugin plugin,
            MessageService messages,
            DebugLogger debug,
            Supplier<AppConfig> config,
            Function<String, DungeonConfig> configOf,
            InstanceRegistry registry,
            File dataFolder,
            AuditService audit) {
        this.plugin = plugin;
        this.messages = messages;
        this.debug = debug;
        this.config = config;
        this.configOf = configOf;
        this.registry = registry;
        this.dataFolder = dataFolder;
        this.audit = audit;
    }

    public void startup() {
        AppConfig appConfig = config.get();
        if (!appConfig.scriptEnabled()) {
            messages.info("console.script_disabled");
        } else if (!ScriptHost.isEngineAvailable()) {
            messages.warning("console.script_engine_unavailable");
        } else {
            engine = Engine.newBuilder(LANGUAGE_ID)
                    .option("engine.WarnInterpreterOnly", "false")
                    .build();
            watchdog = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "EmakiDungeon-Script-Watchdog");
                thread.setDaemon(true);
                return thread;
            });
            engineAvailable = true;
        }
        started = true;
        releaseExampleScript();
    }

    public File scriptDirectory() {
        return new File(dataFolder, SCRIPTS_DIRECTORY);
    }

    public DungeonScriptSession createSession(DungeonInstance instance,
            FlowRuntime runtime,
            FlowControllers controllers) {
        if (!started || !engineAvailable) {
            return null;
        }
        AppConfig appConfig = config.get();
        if (!appConfig.scriptEnabled() || engine == null || watchdog == null) {
            return null;
        }
        String dungeonId = instance.dungeonId();
        String scriptName = dungeonId + ".js";
        String source = apiSources.get(dungeonId);
        if (source == null) {
            File file = new File(scriptDirectory(), dungeonId + ".js");
            if (!file.isFile()) {
                debug.log("dungeon", (UUID) null, "script_session_absent",
                        Map.of("dungeon", dungeonId));
                return null;
            }
            try {
                source = Files.readString(file.toPath());
            } catch (IOException exception) {
                messages.warning("console.script_load_failed", Map.of(
                        "instance", instance.instanceId(),
                        "error", String.valueOf(exception.getMessage())));
                return null;
            }
        } else {
            scriptName = "api:" + dungeonId;
        }
        DungeonScriptSession session = new DungeonScriptSession(instance, runtime, controllers,
                engine, messages, debug, watchdog,
                appConfig.scriptTimeoutMs(), appConfig.scriptFailOnError(), scriptName, audit);
        if (!session.load(source)) {
            messages.warning("console.script_load_failed", Map.of(
                    "instance", instance.instanceId(),
                    "error", session.lastError()));
            session.close();
            return null;
        }
        sessions.put(instance.instanceId(), session);
        instance.setScriptSession(session);
        debug.log("dungeon", (UUID) null, "script_session_created", Map.of(
                "instance", instance.instanceId(),
                "dungeon", dungeonId,
                "script", scriptName));
        return session;
    }

    public void closeSession(DungeonInstance instance) {
        DungeonScriptSession session = instance.scriptSession();
        if (session == null) {
            return;
        }
        instance.setScriptSession(null);
        sessions.remove(instance.instanceId(), session);
        session.close();
        debug.log("dungeon", (UUID) null, "script_session_closed",
                Map.of("instance", instance.instanceId()));
    }

    public TriggerSink sink() {
        return this::fireTrigger;
    }

    public void fireTrigger(TriggerType type, String instanceId, String dungeonId,
            Map<String, String> context) {
        if (!started || type == null) {
            return;
        }
        invokeApiHandlers(type, context);
        if (instanceId != null) {
            DungeonInstance instance = registry.get(instanceId);
            if (instance != null && instance.scriptSession() != null) {
                postTrigger(instance, type, context);
            }
            return;
        }
        if (dungeonId != null) {
            for (DungeonInstance instance : registry.all()) {
                if (dungeonId.equals(instance.dungeonId()) && instance.scriptSession() != null) {
                    postTrigger(instance, type, context);
                }
            }
        }
    }

    public void fireTriggerNow(TriggerType type, String instanceId, String dungeonId,
            Map<String, String> context) {
        if (!started || type == null) {
            return;
        }
        invokeApiHandlers(type, context);
        if (instanceId == null) {
            return;
        }
        DungeonInstance instance = registry.get(instanceId);
        if (instance == null || instance.scriptSession() == null) {
            return;
        }
        instance.scriptSession().dispatchTrigger(type.name(), context);
    }

    public void fireTriggerInInstanceWorld(TriggerType type, UUID playerId,
            Map<String, String> context) {
        if (playerId == null) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }
        DungeonInstance instance = registry.get(player.getWorld().getName());
        if (instance == null) {
            return;
        }
        fireTrigger(type, instance.instanceId(), instance.dungeonId(), context);
    }

    public Result<ScriptId> loadFlowScript(String dungeonId, String source) {
        if (!started || !engineAvailable || engine == null
                || source == null || source.isBlank()) {
            return Result.err(ErrorCodes.ENGINE_UNAVAILABLE, "api.engine_unavailable");
        }
        if (configOf.apply(dungeonId) == null) {
            return Result.err(ErrorCodes.DUNGEON_NOT_FOUND, "api.dungeon_not_found",
                    Map.of("dungeon", dungeonId));
        }
        try (Context probe = Context.newBuilder(LANGUAGE_ID)
                .engine(engine)
                .allowHostAccess(HostAccess.EXPLICIT)
                .allowHostClassLookup(className -> false)
                .build()) {
            probe.parse(Source.newBuilder(LANGUAGE_ID, source, dungeonId + ":api").build());
        } catch (PolyglotException exception) {
            return Result.err(ErrorCodes.SCRIPT_COMPILE_ERROR, "api.script_compile_error",
                    Map.of("dungeon", dungeonId, "error", describe(exception)));
        } catch (IOException | RuntimeException exception) {
            return Result.err(ErrorCodes.SCRIPT_COMPILE_ERROR, "api.script_compile_error",
                    Map.of("dungeon", dungeonId,
                            "error", String.valueOf(exception.getMessage())));
        }
        apiSources.put(dungeonId, source);
        return Result.ok(new ScriptId(dungeonId));
    }

    public Result<Void> registerTrigger(String triggerId, String event,
            ScriptTriggerHandler handler) {
        TriggerType type;
        try {
            type = TriggerType.valueOf(event);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("未知副本事件枚举: " + event);
        }
        if (triggerId == null || triggerId.isBlank()) {
            throw new IllegalArgumentException("triggerId 不能为空");
        }
        if (apiTriggers.containsKey(triggerId)) {
            return Result.err(ErrorCodes.TRIGGER_DUPLICATE, "api.trigger_duplicate",
                    Map.of("trigger", triggerId));
        }
        if (!hasAnyScript()) {
            return Result.err(ErrorCodes.SCRIPT_NOT_LOADED, "api.script_not_loaded",
                    Map.of("trigger", triggerId));
        }
        apiTriggers.put(triggerId, new RegisteredTrigger(type, triggerId, handler));
        return Result.ok(null);
    }

    private boolean hasAnyScript() {
        if (!apiSources.isEmpty() || !sessions.isEmpty()) {
            return true;
        }
        File[] files = scriptDirectory().listFiles(
                candidate -> candidate.isFile() && candidate.getName().endsWith(".js"));
        return files != null && files.length > 0;
    }

    private void postTrigger(DungeonInstance instance, TriggerType type,
            Map<String, String> context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("trigger", type.name());
        payload.put("ctx", context == null ? Map.of() : context);
        instance.ingress().post(new IngressEvent(EventIngressKeys.SCRIPT_TRIGGER, type.name(), payload));
    }

    private void invokeApiHandlers(TriggerType type, Map<String, String> context) {
        if (apiTriggers.isEmpty()) {
            return;
        }
        for (RegisteredTrigger registered : apiTriggers.values()) {
            if (registered.type() != type) {
                continue;
            }
            Map<String, String> merged = new LinkedHashMap<>();
            if (context != null) {
                merged.putAll(context);
            }
            try {
                registered.handler().handle(registered.triggerId(), merged);
            } catch (RuntimeException exception) {
                messages.warning("console.script_api_failed", Map.of(
                        "trigger", registered.triggerId(),
                        "error", String.valueOf(exception.getMessage())));
            }
        }
    }

    private void releaseExampleScript() {
        File target = new File(scriptDirectory(), EXAMPLE_TARGET);
        if (target.isFile()) {
            return;
        }
        try (InputStream resource = plugin.getResource(EXAMPLE_SCRIPT)) {
            if (resource == null) {
                return;
            }
            Files.createDirectories(scriptDirectory().toPath());
            Files.copy(resource, target.toPath());
        } catch (IOException exception) {
            debug.logRaw("dungeon", (UUID) null,
                    "example script release failed | error=" + exception.getMessage());
        }
    }

    private String describe(PolyglotException exception) {
        String message = exception.getMessage();
        String text = message == null || message.isBlank() ? exception.toString() : message;
        SourceSection section = exception.getSourceLocation();
        if (section != null) {
            String locationText = section.toString();
            if (locationText != null && !locationText.isBlank()) {
                return text + " (" + locationText + ")";
            }
        }
        return text;
    }

    @Override
    public void close() {
        for (DungeonInstance instance : registry.all()) {
            closeSession(instance);
        }
        sessions.clear();
        apiTriggers.clear();
        apiSources.clear();
        ScheduledExecutorService currentWatchdog = watchdog;
        watchdog = null;
        Engine currentEngine = engine;
        engine = null;
        engineAvailable = false;
        started = false;
        if (currentEngine != null) {
            currentEngine.close();
        }
        if (currentWatchdog != null) {
            currentWatchdog.shutdownNow();
        }
    }
}
