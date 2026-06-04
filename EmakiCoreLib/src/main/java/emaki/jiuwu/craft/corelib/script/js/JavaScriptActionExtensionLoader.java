package emaki.jiuwu.craft.corelib.script.js;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.action.ActionRegistry;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRegistry;
import emaki.jiuwu.craft.corelib.script.JavaScriptService;
import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.script.ScriptExecutionResult;
import emaki.jiuwu.craft.corelib.script.ScriptInvocationRequest;
import emaki.jiuwu.craft.corelib.script.js.event.JavaScriptEventRegistry;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class JavaScriptActionExtensionLoader implements AutoCloseable {

    private static final String EXTENSION_ROOT = "extensions/global";

    private final Plugin plugin;
    private final ActionRegistry registry;
    private final PlaceholderRegistry placeholderRegistry;
    private final JavaScriptService javaScriptService;
    private final MessageService messageService;
    private final ScriptConfig scriptConfig;
    private final Path scriptRoot;
    private final List<String> registeredIds = new ArrayList<>();
    private final List<String> registeredSources = new ArrayList<>();
    private final List<JavaScriptPlaceholderResolver> registeredPlaceholders = new ArrayList<>();
    private final List<String> loadedExtensionScripts = new ArrayList<>();
    private final List<Map<String, Object>> recentErrors = new ArrayList<>();
    private JavaScriptEventRegistry eventRegistry;

    public JavaScriptActionExtensionLoader(Plugin plugin,
            ActionRegistry registry,
            PlaceholderRegistry placeholderRegistry,
            JavaScriptService javaScriptService,
            MessageService messageService,
            ScriptConfig scriptConfig,
            Path scriptRoot) {
        this.plugin = plugin;
        this.registry = registry;
        this.placeholderRegistry = placeholderRegistry;
        this.javaScriptService = javaScriptService;
        this.messageService = messageService;
        this.scriptConfig = scriptConfig == null ? ScriptConfig.defaults() : scriptConfig;
        this.scriptRoot = scriptRoot;
    }

    public int reload() {
        close();
        loadedExtensionScripts.clear();
        recentErrors.clear();
        if (plugin == null || registry == null || javaScriptService == null || !javaScriptService.enabled() || scriptRoot == null) {
            return 0;
        }
        eventRegistry = new JavaScriptEventRegistry(plugin, javaScriptService, messageService, scriptConfig);
        plugin.getServer().getPluginManager().registerEvents(eventRegistry, plugin);
        List<String> scripts = scanScripts();
        int loaded = 0;
        for (String scriptPath : scripts) {
            JavaScriptActionRegistrationApi api = new JavaScriptActionRegistrationApi(
                    plugin,
                    registry,
                    placeholderRegistry,
                    eventRegistry,
                    javaScriptService,
                    messageService,
                    scriptConfig,
                    scriptPath
            );
            ScriptExecutionResult result = javaScriptService.invoke(new ScriptInvocationRequest(
                    plugin,
                    null,
                    scriptPath,
                    "register",
                    List.of(api),
                    Map.of("extension", "global", "script", scriptPath),
                    scriptConfig.clampTimeoutMillis(scriptConfig.engine().defaultTimeoutMillis()),
                    false
            ));
            if (result != null && result.success() && !result.skipped()) {
                registeredIds.addAll(api.registeredIds());
                registeredSources.add(scriptPath);
                registeredPlaceholders.addAll(api.registeredPlaceholders());
                loadedExtensionScripts.add(scriptPath);
                loaded++;
            } else {
                String message = result == null ? message("console.js_extension_no_result") : result.message();
                recordError(scriptPath, "register", message);
                warning("console.js_extension_load_failed", Map.of(
                        "script", scriptPath,
                        "error", Texts.toStringSafe(message)
                ));
            }
        }
        if (loaded > 0) {
            info("console.js_extensions_loaded", Map.of(
                    "count", String.valueOf(loaded),
                    "actions", String.valueOf(registeredIds.size()),
                    "placeholders", String.valueOf(registeredPlaceholders.size()),
                    "events", String.valueOf(eventRegistry == null ? 0 : eventRegistry.subscriptions().size())
            ));
        }
        return loaded;
    }

    public Map<String, Object> statusSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("enabled", javaScriptService != null && javaScriptService.enabled());
        snapshot.put("scriptRoot", scriptRoot == null ? "" : scriptRoot.toString());
        snapshot.put("loadedScripts", javaScriptService == null ? List.of() : javaScriptService.loadedScripts());
        snapshot.put("globalExtensionScripts", List.copyOf(loadedExtensionScripts));
        snapshot.put("actions", actionSnapshots());
        snapshot.put("placeholders", placeholderSnapshots());
        snapshot.put("events", eventSnapshots());
        snapshot.put("recentErrors", List.copyOf(recentErrors));
        snapshot.put("attribute", Map.of(
                "available", plugin != null && plugin.getServer().getPluginManager().isPluginEnabled("EmakiAttribute"),
                "status", "not_exposed",
                "message", "Attribute JavaScript provider/hook status is not exposed by EmakiAttribute yet."
        ));
        return snapshot;
    }

    @Override
    public void close() {
        if (eventRegistry != null) {
            HandlerList.unregisterAll(eventRegistry);
            eventRegistry.close();
            eventRegistry = null;
        }
        if (placeholderRegistry != null) {
            for (JavaScriptPlaceholderResolver resolver : List.copyOf(registeredPlaceholders)) {
                placeholderRegistry.unregister(resolver);
            }
        }
        if (registry != null) {
            for (String source : List.copyOf(registeredSources)) {
                registry.unregisterAllBySource(source);
            }
            for (String id : List.copyOf(registeredIds)) {
                registry.unregister(id);
            }
        }
        registeredPlaceholders.clear();
        registeredSources.clear();
        registeredIds.clear();
    }

    private List<Map<String, Object>> actionSnapshots() {
        if (registry == null) {
            return List.of();
        }
        List<Map<String, Object>> actions = new ArrayList<>();
        for (String id : registeredIds) {
            emaki.jiuwu.craft.corelib.action.Action action = registry.get(id);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", id);
            item.put("registered", action != null);
            item.put("category", action == null ? "" : action.category());
            item.put("description", action == null ? "" : action.description());
            item.put("source", registry.sourceOf(id));
            item.put("owner", registry.ownerKeyOf(id));
            actions.add(item);
        }
        return List.copyOf(actions);
    }

    private List<Map<String, Object>> placeholderSnapshots() {
        List<Map<String, Object>> placeholders = new ArrayList<>();
        for (JavaScriptPlaceholderResolver resolver : registeredPlaceholders) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", resolver.id());
            item.put("token", "%" + resolver.id() + "%");
            placeholders.add(item);
        }
        return List.copyOf(placeholders);
    }

    private List<Map<String, Object>> eventSnapshots() {
        if (eventRegistry == null) {
            return List.of();
        }
        List<Map<String, Object>> events = new ArrayList<>();
        for (emaki.jiuwu.craft.corelib.script.js.event.JavaScriptEventSubscription subscription : eventRegistry.subscriptions()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", subscription.id());
            item.put("event", subscription.eventType());
            item.put("priority", subscription.priority().name());
            item.put("ignoreCancelled", subscription.ignoreCancelled());
            item.put("script", subscription.scriptPath());
            item.put("function", subscription.functionName());
            item.put("timeoutMillis", subscription.timeoutMillis());
            events.add(item);
        }
        return List.copyOf(events);
    }

    private void recordError(String scriptPath, String phase, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("script", scriptPath == null ? "" : scriptPath);
        error.put("phase", phase == null ? "" : phase);
        error.put("message", message == null ? "" : message);
        error.put("time", System.currentTimeMillis());
        recentErrors.add(error);
        while (recentErrors.size() > 20) {
            recentErrors.remove(0);
        }
    }

    private List<String> scanScripts() {
        Path root = scriptRoot.resolve(EXTENSION_ROOT);
        if (!Files.exists(root)) {
            try {
                Files.createDirectories(root);
            } catch (IOException exception) {
                warning("console.js_extension_directory_create_failed", Map.of(
                        "error", Texts.toStringSafe(exception.getMessage())
                ));
            }
            return List.of();
        }
        try (java.util.stream.Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".js"))
                    .sorted(Comparator.comparing(Path::toString))
                    .map(path -> scriptRoot.relativize(path).toString().replace('\\', '/'))
                    .filter(Texts::isNotBlank)
                    .toList();
        } catch (IOException exception) {
            warning("console.js_extension_scan_failed", Map.of(
                    "error", Texts.toStringSafe(exception.getMessage())
            ));
            return List.of();
        }
    }

    private String message(String key) {
        return messageService == null ? key : messageService.message(key);
    }

    private void info(String key, Map<String, ?> replacements) {
        if (messageService != null) {
            messageService.info(key, replacements);
        }
    }

    private void warning(String key, Map<String, ?> replacements) {
        if (messageService != null) {
            messageService.warning(key, replacements);
        }
    }
}
