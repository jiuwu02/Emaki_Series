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
import emaki.jiuwu.craft.corelib.text.Texts;

public final class JavaScriptActionExtensionLoader implements AutoCloseable {

    private static final String EXTENSION_ROOT = "extensions/global";

    private final Plugin plugin;
    private final ActionRegistry registry;
    private final PlaceholderRegistry placeholderRegistry;
    private final JavaScriptService javaScriptService;
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
            ScriptConfig scriptConfig,
            Path scriptRoot) {
        this.plugin = plugin;
        this.registry = registry;
        this.placeholderRegistry = placeholderRegistry;
        this.javaScriptService = javaScriptService;
        this.scriptConfig = scriptConfig == null ? ScriptConfig.defaults() : scriptConfig;
        this.scriptRoot = scriptRoot;
    }

    public int reload() {
        close();
        if (plugin == null || registry == null || javaScriptService == null || !javaScriptService.enabled() || scriptRoot == null) {
            return 0;
        }
        eventRegistry = new JavaScriptEventRegistry(plugin, javaScriptService, scriptConfig);
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
                loaded++;
            } else {
                String message = result == null ? "no result" : result.message();
                plugin.getLogger().warning("Failed to load JavaScript action extension " + scriptPath + ": " + message);
            }
        }
        if (loaded > 0) {
            plugin.getLogger().info("Loaded " + loaded + " JavaScript global action extension script(s), actions=" + registeredIds.size()
                    + ", placeholders=" + registeredPlaceholders.size()
                    + ", events=" + (eventRegistry == null ? 0 : eventRegistry.subscriptions().size()));
        }
        return loaded;
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

    private List<String> scanScripts() {
        Path root = scriptRoot.resolve(EXTENSION_ROOT);
        if (!Files.exists(root)) {
            try {
                Files.createDirectories(root);
            } catch (IOException exception) {
                plugin.getLogger().warning("Failed to create JavaScript global action extension directory: " + exception.getMessage());
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
            plugin.getLogger().warning("Failed to scan JavaScript global action extensions: " + exception.getMessage());
            return List.of();
        }
    }
}
