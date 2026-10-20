package emaki.jiuwu.craft.dungeon.script;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.dungeon.flow.FlowControllers;
import emaki.jiuwu.craft.dungeon.flow.FlowRuntime;
import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;
import emaki.jiuwu.craft.dungeon.instance.EventIngress.IngressEvent;
import emaki.jiuwu.craft.dungeon.instance.WorldCommand;
import emaki.jiuwu.craft.dungeon.trigger.TriggerType;

public final class DungeonScriptSession implements AutoCloseable {

    private static final String LANGUAGE_ID = "js";
    private static final String PREAMBLE = """
            (() => {
              const waiters = new Map();
              let seq = 0;
              const parse = (raw) => {
                try { return JSON.parse(raw); } catch (e) { return { raw: String(raw) }; }
              };
              globalThis.onTrigger = (event, fn) => {
                if (typeof event !== 'string' || typeof fn !== 'function') {
                  throw new Error('onTrigger(event, handler)');
                }
                const wrapped = async (name, raw) => {
                  try {
                    const payload = parse(raw);
                    return await fn(payload);
                  } catch (e) {
                    dungeon.__reportError(String(name), String((e && e.stack) || e));
                  }
                };
                dungeon.__registerHandler(event, wrapped);
              };
              globalThis.waitFor = (event) => new Promise((resolve) => {
                if (typeof event !== 'string') {
                  throw new Error('waitFor(event)');
                }
                const id = 'wait_' + (++seq);
                if (!waiters.has(event)) {
                  waiters.set(event, []);
                }
                waiters.get(event).push({ id: id, resolve: resolve });
              });
              globalThis.__resolveWaiters = (name, raw) => {
                const list = waiters.get(name);
                if (!list || list.length === 0) {
                  return;
                }
                waiters.delete(name);
                const payload = parse(raw);
                for (const waiter of list) {
                  waiter.resolve(payload);
                }
              };
            })();
            """;

    private final DungeonInstance instance;
    private final FlowRuntime runtime;
    private final FlowControllers controllers;
    private final MessageService messages;
    private final DebugLogger debug;
    private final ScheduledExecutorService watchdog;
    private final Engine engine;
    private final long timeoutMs;
    private final boolean failOnError;
    private final String scriptName;
    private final emaki.jiuwu.craft.dungeon.audit.AuditService audit;
    private final EntryRegistry entries = new EntryRegistry();
    private final Map<String, CopyOnWriteArrayList<Value>> handlers = new ConcurrentHashMap<>();

    private volatile Context context;
    private volatile boolean disabled;
    private volatile boolean closed;
    private volatile String lastError = "";

    DungeonScriptSession(DungeonInstance instance,
            FlowRuntime runtime,
            FlowControllers controllers,
            Engine engine,
            MessageService messages,
            DebugLogger debug,
            ScheduledExecutorService watchdog,
            long timeoutMs,
            boolean failOnError,
            String scriptName,
            emaki.jiuwu.craft.dungeon.audit.AuditService audit) {
        this.instance = instance;
        this.runtime = runtime;
        this.controllers = controllers;
        this.engine = engine;
        this.messages = messages;
        this.debug = debug;
        this.watchdog = watchdog;
        this.timeoutMs = timeoutMs;
        this.failOnError = failOnError;
        this.scriptName = scriptName;
        this.audit = audit;
    }

    boolean load(String source) {
        try {
            Context built = Context.newBuilder(LANGUAGE_ID)
                    .engine(engine)
                    .allowHostAccess(HostAccess.EXPLICIT)
                    .allowHostClassLookup(className -> false)
                    .option("engine.WarnInterpreterOnly", "false")
                    .build();
            context = built;
            built.getBindings(LANGUAGE_ID).putMember("dungeon",
                    new ScriptHostObject(this, runtime, controllers, instance));
            built.eval(Source.newBuilder(LANGUAGE_ID, PREAMBLE, "emd_script_preamble").build());
            built.eval(Source.newBuilder(LANGUAGE_ID, source, scriptName).build());
            return true;
        } catch (PolyglotException exception) {
            lastError = describe(exception);
            return false;
        } catch (IOException | RuntimeException exception) {
            lastError = String.valueOf(exception.getMessage());
            return false;
        }
    }

    public String lastError() {
        return lastError;
    }

    public DungeonInstance instance() {
        return instance;
    }

    public FlowRuntime runtime() {
        return runtime;
    }

    public FlowControllers controllers() {
        return controllers;
    }

    public EntryRegistry entries() {
        return entries;
    }

    public String scriptName() {
        return scriptName;
    }

    public boolean isDisabled() {
        return disabled;
    }

    void registerHandler(String event, Value handler) {
        TriggerType.valueOf(event);
        handlers.computeIfAbsent(event, key -> new CopyOnWriteArrayList<>()).add(handler);
    }

    void onScriptError(String trigger, String detail) {
        fail(trigger == null ? "-" : trigger, detail);
    }

    void onTakeover() {
        debug.log("dungeon", (UUID) null, "script_takeover", Map.of(
                "instance", instance.instanceId(),
                "dungeon", instance.dungeonId()));
    }

    void onEntryRegistered(String entryId) {
        debug.log("dungeon", (UUID) null, "script_entry_registered", Map.of(
                "instance", instance.instanceId(),
                "entry", entryId));
    }

    void onEntriesCleared() {
        debug.log("dungeon", (UUID) null, "script_entry_cleared", Map.of(
                "instance", instance.instanceId(),
                "count", String.valueOf(entries.size())));
    }

    public void onIngress(IngressEvent event) {
        if (event == null || event.trigger() == null) {
            return;
        }
        if (emaki.jiuwu.craft.dungeon.flow.EventIngressKeys.SCRIPT_TRIGGER.equals(event.trigger())) {
            Map<String, String> context = Map.of();
            Object raw = event.payload().get("ctx");
            if (raw instanceof Map<?, ?> map) {
                Map<String, String> parsed = new java.util.LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        parsed.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                    }
                }
                context = parsed;
            }
            dispatchTrigger(event.sourceId(), context);
            return;
        }
        TriggerType legacy;
        try {
            legacy = TriggerType.valueOf(event.trigger());
        } catch (RuntimeException exception) {
            return;
        }
        Map<String, String> context = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : event.payload().entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                context.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        if (event.sourceId() != null && !event.sourceId().isBlank()) {
            context.putIfAbsent("player", event.sourceId());
        }
        dispatchTrigger(legacy.name(), context);
    }

    void dispatchTrigger(String triggerName, Map<String, String> context) {
        if (closed || disabled || triggerName == null || context == null) {
            return;
        }
        String json = toJson(triggerName, context);
        runSegment(triggerName, () -> callGuest("__resolveWaiters", triggerName, json));
        CopyOnWriteArrayList<Value> registered = handlers.get(triggerName);
        if (registered == null || registered.isEmpty() || disabled) {
            return;
        }
        for (Value handler : registered) {
            if (disabled || closed) {
                break;
            }
            runSegment(triggerName, () -> handler.execute(triggerName, json));
        }
    }

    private void callGuest(String function, String triggerName, String json) {
        Context current = context;
        if (current == null) {
            return;
        }
        Value target = current.getBindings(LANGUAGE_ID).getMember(function);
        if (target != null && target.canExecute()) {
            target.execute(triggerName, json);
        }
    }

    private void runSegment(String triggerName, Runnable body) {
        Context current = context;
        if (current == null || disabled || closed) {
            return;
        }
        AtomicBoolean interrupted = new AtomicBoolean(false);
        ScheduledFuture<?> killer = null;
        try {
            killer = watchdog.schedule(() -> {
                interrupted.set(true);
                try {
                    current.interrupt(java.time.Duration.ofMillis(100));
                } catch (Exception ignored) {
                }
            }, timeoutMs, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException ignored) {
        }
        try {
            body.run();
        } catch (PolyglotException exception) {
            if (interrupted.get()) {
                fail(triggerName, "timeout " + timeoutMs + "ms");
            } else {
                fail(triggerName, describe(exception));
            }
        } catch (RuntimeException exception) {
            if (interrupted.get() || Thread.currentThread().isInterrupted()) {
                fail(triggerName, "timeout " + timeoutMs + "ms");
            } else {
                fail(triggerName, String.valueOf(exception.getMessage()));
            }
        } finally {
            if (killer != null) {
                killer.cancel(false);
            }
        }
    }

    private void fail(String triggerName, String detail) {
        if (disabled || closed) {
            return;
        }
        disabled = true;
        runtime.setScriptTakeover(false);
        messages.warning("console.script_stopped", Map.of(
                "instance", instance.instanceId(),
                "script", scriptName,
                "trigger", triggerName == null ? "-" : triggerName,
                "error", detail == null || detail.isBlank() ? "-" : detail));
        debug.log("dungeon", (UUID) null, "script_stopped", Map.of(
                "instance", instance.instanceId(),
                "script", scriptName));
        if (audit != null) {
            audit.write("script", Map.of(
                    "event", "stopped",
                    "instance", instance.instanceId(),
                    "dungeon", instance.dungeonId(),
                    "script", scriptName,
                    "trigger", triggerName == null ? "-" : triggerName,
                    "error", detail == null || detail.isBlank() ? "-" : detail));
        }
        instance.commands().submit(new WorldCommand() {
            @Override
            public int blockCost() {
                return 0;
            }

            @Override
            public void execute() {
                org.bukkit.World world = org.bukkit.Bukkit.getWorld(instance.instanceId());
                if (world == null) {
                    return;
                }
                for (org.bukkit.entity.Player member : world.getPlayers()) {
                    messages.send(member, "script.stopped_chat", Map.of());
                }
            }
        });
        if (failOnError && controllers != null) {
            controllers.triggerFail(runtime, "script_error");
        }
    }

    private String describe(PolyglotException exception) {
        String message = exception.getMessage();
        String text = message == null || message.isBlank() ? exception.toString() : message;
        org.graalvm.polyglot.SourceSection location = exception.getSourceLocation();
        if (location != null) {
            String locationText = location.toString();
            if (locationText != null && !locationText.isBlank()) {
                return text + " (" + locationText + ")";
            }
        }
        return text;
    }

    private static String toJson(String trigger, Map<String, String> context) {
        StringBuilder builder = new StringBuilder(64);
        builder.append("{\"trigger\":").append(quote(trigger == null ? "" : trigger));
        for (Map.Entry<String, String> entry : context.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            builder.append(',').append(quote(entry.getKey())).append(':').append(quote(entry.getValue()));
        }
        return builder.append('}').toString();
    }

    private static String quote(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 2).append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20) {
                        builder.append(String.format("\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }
        return builder.append('"').toString();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        if (entries.size() > 0) {
            onEntriesCleared();
        }
        closed = true;
        entries.clear();
        handlers.clear();
        Context current = context;
        context = null;
        if (current != null) {
            try {
                current.close();
            } catch (RuntimeException ignored) {
            }
        }
    }

    UUID anyPlayer() {
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(instance.instanceId());
        if (world == null) {
            return null;
        }
        for (org.bukkit.entity.Player member : world.getPlayers()) {
            return member.getUniqueId();
        }
        return null;
    }

    int playerCount() {
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(instance.instanceId());
        return world == null ? 0 : world.getPlayers().size();
    }
}
