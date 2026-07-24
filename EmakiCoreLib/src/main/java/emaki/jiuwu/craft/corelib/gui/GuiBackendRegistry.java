package emaki.jiuwu.craft.corelib.gui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import emaki.jiuwu.craft.corelib.service.MessageService;














public final class GuiBackendRegistry {


    public static final String BUKKIT = "bukkit";

    private final MessageService messageService;
    private final BukkitGuiBackend bukkitBackend = new BukkitGuiBackend();
    private final Map<String, GuiBackend> backends = new ConcurrentHashMap<>();

    private final Set<String> registrationOrder = java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<>());
    private final Set<String> warnedNames = ConcurrentHashMap.newKeySet();
    private final AtomicReference<CompletableFuture<Void>> shutdownFuture = new AtomicReference<>();

    private volatile String configuredName = BUKKIT;

    public GuiBackendRegistry(MessageService messageService) {
        this.messageService = messageService;
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


        warnedNames.remove(key);
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
        if (messageService == null || !warnedNames.add(dedupeKey)) {
            return;
        }
        if (value == null) {
            messageService.warning(messageKey);
        } else {
            messageService.warning(messageKey, Map.of("value", value));
        }
    }

    private CompletableFuture<Void> safeShutdownAsync(GuiBackend backend) {
        try {
            CompletionStage<Void> stage = backend.shutdownAsync();
            if (stage == null) {
                return CompletableFuture.completedFuture(null);
            }
            CompletableFuture<Void> completion = new CompletableFuture<>();
            stage.whenComplete((ignored, throwable) -> completion.complete(null));
            return completion;
        } catch (Throwable ignored) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
