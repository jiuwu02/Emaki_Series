package emaki.jiuwu.craft.corelib.gui;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import emaki.jiuwu.craft.corelib.service.MessageService;

/**
 * CoreLib-wide registry of {@link GuiBackend} implementations.
 *
 * <p>CoreLib itself only ships the built-in {@code bukkit} backend. Optional
 * backend plugins (e.g. EmakiGuiPacket) register their implementation here at
 * runtime, so the packet protocol code no longer lives inside the core. Every
 * plugin's {@link GuiService} shares the single registry instance owned by
 * {@link emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin}.</p>
 *
 * <p>The configured backend name comes from {@code gui.backend} and is resolved
 * lazily through {@link #activeBackend()} so a backend registered after CoreLib
 * has enabled is still picked up the next time a menu opens.</p>
 */
public final class GuiBackendRegistry {

    /** The built-in backend name that is always available. */
    public static final String BUKKIT = "bukkit";

    private final MessageService messageService;
    private final BukkitGuiBackend bukkitBackend = new BukkitGuiBackend();
    private final Map<String, GuiBackend> backends = new ConcurrentHashMap<>();
    /** Preserves registration order so {@code auto} picks a deterministic backend. */
    private final Set<String> registrationOrder = java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<>());
    private final Set<String> warnedNames = ConcurrentHashMap.newKeySet();

    private volatile String configuredName = BUKKIT;

    public GuiBackendRegistry(MessageService messageService) {
        this.messageService = messageService;
        register(BUKKIT, bukkitBackend);
    }

    /**
     * Registers a backend under the given name. Re-registering the same name
     * replaces the previous instance (and shuts the old one down).
     */
    public void register(String name, GuiBackend backend) {
        if (name == null || backend == null) {
            return;
        }
        String key = normalize(name);
        GuiBackend previous = backends.put(key, backend);
        registrationOrder.add(key);
        if (previous != null && previous != backend) {
            safeShutdown(previous);
        }
        // A newly available backend may satisfy a previously failed lookup, so
        // allow the warning to fire again if it later disappears.
        warnedNames.remove(key);
    }

    /**
     * Removes a backend by name and shuts it down. The built-in {@code bukkit}
     * backend cannot be removed.
     */
    public void unregister(String name) {
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
            safeShutdown(removed);
        }
    }

    /**
     * Sets the configured backend name (the {@code gui.backend} value). Resets
     * the warn-once state so a configuration change can warn again.
     */
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

    /**
     * Resolves the active backend for the configured {@code gui.backend} value.
     *
     * <p>Four-state resolution:</p>
     * <ul>
     *   <li>{@code bukkit} — the built-in backend.</li>
     *   <li>{@code packet} — the registered {@code packet} backend, or a
     *       warn-once fallback to bukkit when no such backend is registered
     *       (e.g. EmakiGuiPacket not installed).</li>
     *   <li>{@code auto} — the first registered non-bukkit backend, or a silent
     *       fallback to bukkit when none is present (mirrors the legacy
     *       factory's auto semantics, which never warned).</li>
     *   <li>anything else — a warn-once fallback to bukkit.</li>
     * </ul>
     */
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

    /** Shuts down every registered backend. Invoked when CoreLib disables. */
    public void shutdownAll() {
        for (GuiBackend backend : backends.values()) {
            safeShutdown(backend);
        }
        backends.clear();
        registrationOrder.clear();
        warnedNames.clear();
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

    private void safeShutdown(GuiBackend backend) {
        try {
            backend.shutdown();
        } catch (RuntimeException | LinkageError ignored) {
            // A backend tearing down on server stop must not break disable.
        }
    }

    private static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
