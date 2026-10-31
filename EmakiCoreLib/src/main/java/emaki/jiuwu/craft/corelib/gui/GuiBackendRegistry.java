package emaki.jiuwu.craft.corelib.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.async.AsyncFailures;
import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.cache.CacheManager;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.service.MessageService;

public final class GuiBackendRegistry {

    public static final String BUKKIT = "bukkit";

    private static final int WARNED_NAMES_LIMIT = 256;

    private final MessageService messageService;
    private final Plugin owner;
    private final ExecutionDispatcher dispatcher;
    private final ThreadOwnership threadOwnership;
    private final BukkitGuiBackend bukkitBackend = new BukkitGuiBackend();
    private final Map<String, GuiBackend> backends = new ConcurrentHashMap<>();

    private final Set<String> registrationOrder = Collections.synchronizedSet(new LinkedHashSet<>());
    private final CacheManager<String, Boolean> warnedNames = new CacheManager<>(WARNED_NAMES_LIMIT, 0L);
    private final AtomicReference<CompletableFuture<Void>> shutdownFuture = new AtomicReference<>();

    private volatile String configuredName = BUKKIT;

    public GuiBackendRegistry(
            MessageService messageService,
            Plugin owner,
            ExecutionDispatcher dispatcher,
            ThreadOwnership threadOwnership) {
        this.messageService = messageService;
        this.owner = owner;
        this.dispatcher = dispatcher;
        this.threadOwnership = threadOwnership;
        register(BUKKIT, bukkitBackend);
    }

    public synchronized void register(String name, GuiBackend backend) {
        if (name == null || backend == null) {
            return;
        }
        if (shutdownFuture.get() != null) {
            safeShutdownAsync(backend);
            return;
        }
        String key = normalize(name);
        GuiBackend previous = backends.put(key, backend);
        registrationOrder.add(key);
        if (previous != null && previous != backend) {
            safeShutdownAsync(previous);
        }

        warnedNames.invalidate(key);
    }

    public synchronized void unregister(String name) {
        if (name == null) {
            return;
        }
        String key = normalize(name);
        if (BUKKIT.equals(key)) {
            return;
        }
        GuiBackend removed = backends.remove(key);
        registrationOrder.remove(key);
        if (removed != null) {
            safeShutdownAsync(removed);
        }
    }

    public void setConfiguredName(String name) {
        String normalized = name == null ? BUKKIT : normalize(name);
        if (!normalized.equals(this.configuredName)) {
            warnedNames.clear();
        }
        this.configuredName = normalized;
    }

    public String configuredName() {
        return configuredName;
    }

    public GuiBackend activeBackend() {
        String name = configuredName;
        if (BUKKIT.equals(name)) {
            return bukkitBackend;
        }
        if ("auto".equals(name)) {
            GuiBackend preferred = firstNonBukkit();
            return preferred == null ? bukkitBackend : preferred;
        }
        if ("packet".equals(name)) {
            GuiBackend backend = backends.get("packet");
            if (backend != null) {
                return backend;
            }
            warnOnce(name, "gui.backend.packet_backend_missing", null);
            return bukkitBackend;
        }
        warnOnce(name, "gui.backend.unknown_value", name);
        return bukkitBackend;
    }

    public void shutdownAll() {
        shutdownAllAsync();
    }

    public synchronized CompletionStage<Void> shutdownAllAsync() {
        CompletableFuture<Void> existing = shutdownFuture.get();
        if (existing != null) {
            return existing;
        }
        CompletableFuture<Void> created = new CompletableFuture<>();
        shutdownFuture.set(created);
        List<GuiBackend> snapshot = new ArrayList<>(new LinkedHashSet<>(backends.values()));
        backends.clear();
        registrationOrder.clear();
        warnedNames.clear();
        CompletableFuture<?>[] futures = snapshot.stream()
                .map(this::safeShutdownAsync)
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(futures).whenComplete((ignored, throwable) -> {
            if (throwable == null) {
                created.complete(null);
            } else {
                created.completeExceptionally(throwable);
            }
        });
        return created;
    }

    private GuiBackend firstNonBukkit() {
        synchronized (registrationOrder) {
            for (String key : registrationOrder) {
                if (!BUKKIT.equals(key)) {
                    GuiBackend backend = backends.get(key);
                    if (backend != null) {
                        return backend;
                    }
                }
            }
        }
        return null;
    }

    private void warnOnce(String dedupeKey, String messageKey, String value) {
        if (messageService == null || warnedNames.get(dedupeKey) != null) {
            return;
        }
        warnedNames.put(dedupeKey, Boolean.TRUE);
        if (value == null) {
            messageService.warning(messageKey);
        } else {
            messageService.warning(messageKey, Map.of("value", value));
        }
    }

    private CompletableFuture<Void> safeShutdownAsync(GuiBackend backend) {
        if (!requiresOwnerThread(backend) || isOwnerThread()) {
            return dispatchShutdown(backend);
        }
        return dispatchToOwner(backend);
    }

    private boolean requiresOwnerThread(GuiBackend backend) {
        try {
            return backend.shutdownRequiresOwnerThread();
        } catch (Throwable ignored) {
            return true;
        }
    }

    private boolean isOwnerThread() {
        if (threadOwnership != null) {
            try {
                return threadOwnership.isGlobalOwned();
            } catch (Throwable ignored) {
                return false;
            }
        }
        return false;
    }

    private CompletableFuture<Void> dispatchToOwner(GuiBackend backend) {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        if (dispatcher == null || owner == null) {
            warnShutdownDeferred(backend, "缺少 owner 调度器");
            completion.complete(null);
            return completion;
        }
        TaskToken token;
        try {
            token = dispatcher.runGlobal(owner, () -> dispatchShutdown(backend).whenComplete((ignored, throwable) -> {
                if (throwable == null) {
                    completion.complete(null);
                } else {
                    completion.completeExceptionally(throwable);
                }
            }));
        } catch (Throwable throwable) {
            warnShutdownDeferred(backend, AsyncFailures.describe(throwable));
            completion.complete(null);
            return completion;
        }
        if (token == null || token.cancelled()) {
            warnShutdownDeferred(backend, "owner 调度被拒绝");
            completion.complete(null);
        }
        return completion;
    }

    private CompletableFuture<Void> dispatchShutdown(GuiBackend backend) {
        CompletionStage<Void> stage;
        try {
            stage = backend.shutdownAsync();
        } catch (Throwable throwable) {
            warnShutdownFailed(backend, throwable);
            return CompletableFuture.completedFuture(null);
        }
        if (stage == null) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> completion = new CompletableFuture<>();
        stage.whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                warnShutdownFailed(backend, throwable);
            }
            completion.complete(null);
        });
        return completion;
    }

    private void warnShutdownFailed(GuiBackend backend, Throwable throwable) {
        warn("console.gui_backend_shutdown_failed", Map.of(
                "backend", backendName(backend),
                "error", AsyncFailures.describe(throwable)));
    }

    private void warnShutdownDeferred(GuiBackend backend, String reason) {
        warn("console.gui_backend_shutdown_deferred", Map.of(
                "backend", backendName(backend),
                "reason", reason));
    }

    private void warn(String messageKey, Map<String, String> replacements) {
        if (messageService == null) {
            return;
        }
        try {
            messageService.warning(messageKey, replacements);
        } catch (RuntimeException ignored) {
            return;
        }
    }

    private static String backendName(GuiBackend backend) {
        try {
            String name = backend.name();
            return name == null ? "<unknown>" : name;
        } catch (Throwable ignored) {
            return "<unknown>";
        }
    }

    private static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
