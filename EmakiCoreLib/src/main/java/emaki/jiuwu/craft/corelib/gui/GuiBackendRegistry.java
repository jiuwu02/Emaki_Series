package emaki.jiuwu.craft.corelib.gui;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import emaki.jiuwu.craft.corelib.service.MessageService;














public final class GuiBackendRegistry {


    public static final String BUKKIT = "bukkit";

    private final MessageService messageService;
    private final BukkitGuiBackend bukkitBackend = new BukkitGuiBackend();
    private final Map<String, GuiBackend> backends = new ConcurrentHashMap<>();

    private final Set<String> registrationOrder = java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<>());
    private final Set<String> warnedNames = ConcurrentHashMap.newKeySet();

    private volatile String configuredName = BUKKIT;

    public GuiBackendRegistry(MessageService messageService) {
        this.messageService = messageService;
        register(BUKKIT, bukkitBackend);
    }





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


        warnedNames.remove(key);
    }





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

        }
    }

    private static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
