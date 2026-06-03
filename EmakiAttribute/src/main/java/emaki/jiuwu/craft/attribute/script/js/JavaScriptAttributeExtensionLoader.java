package emaki.jiuwu.craft.attribute.script.js;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.corelib.script.JavaScriptService;
import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.script.ScriptExecutionResult;
import emaki.jiuwu.craft.corelib.script.ScriptInvocationRequest;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class JavaScriptAttributeExtensionLoader implements AutoCloseable {

    private static final String EXTENSION_ROOT = "extensions/attribute";

    private final EmakiAttributePlugin plugin;
    private final JavaScriptService javaScriptService;
    private final ScriptConfig scriptConfig;
    private final JavaScriptDamageHookRegistry damageHookRegistry;
    private final java.util.Set<String> registeredProviders = new java.util.LinkedHashSet<>();

    public JavaScriptAttributeExtensionLoader(EmakiAttributePlugin plugin,
            JavaScriptService javaScriptService,
            ScriptConfig scriptConfig,
            JavaScriptDamageHookRegistry damageHookRegistry) {
        this.plugin = plugin;
        this.javaScriptService = javaScriptService;
        this.scriptConfig = scriptConfig == null ? ScriptConfig.defaults() : scriptConfig;
        this.damageHookRegistry = damageHookRegistry;
    }

    public int reload() {
        close();
        if (plugin == null || javaScriptService == null || !javaScriptService.enabled()) {
            return 0;
        }
        int loaded = 0;
        for (String scriptPath : scanScripts()) {
            JavaScriptAttributeRegistrationApi api = new JavaScriptAttributeRegistrationApi(plugin, javaScriptService, scriptConfig, damageHookRegistry, scriptPath);
            ScriptExecutionResult result = javaScriptService.invoke(new ScriptInvocationRequest(
                    plugin,
                    null,
                    scriptPath,
                    "register",
                    List.of(api),
                    Map.of("extension", "attribute", "script", scriptPath),
                    scriptConfig.clampTimeoutMillis(scriptConfig.engine().defaultTimeoutMillis()),
                    false
            ));
            if (result != null && result.success() && !result.skipped()) {
                registeredProviders.addAll(api.registeredProviders());
                loaded++;
            } else {
                String message = result == null ? "no result" : result.message();
                plugin.getLogger().warning("Failed to load JavaScript attribute extension " + scriptPath + ": " + message);
            }
        }
        if (plugin.attributeService() != null) {
            plugin.attributeService().refreshCaches();
            plugin.attributeService().resyncAllPlayers();
        }
        if (loaded > 0) {
            plugin.getLogger().info("Loaded " + loaded + " JavaScript attribute extension script(s), damageHooks="
                    + (damageHookRegistry == null ? 0 : damageHookRegistry.size()));
        }
        return loaded;
    }

    @Override
    public void close() {
        if (plugin != null && plugin.attributeService() != null) {
            for (String providerId : java.util.Set.copyOf(registeredProviders)) {
                plugin.attributeService().unregisterContributionProvider(providerId);
            }
        }
        registeredProviders.clear();
        if (plugin != null && plugin.attributeRegistry() != null) {
            plugin.attributeRegistry().clearRuntime();
        }
        if (damageHookRegistry != null) {
            damageHookRegistry.clear();
        }
    }

    private List<String> scanScripts() {
        Path root = plugin.coreLib().dataPath(scriptConfig.paths().root()).resolve(EXTENSION_ROOT);
        if (!Files.exists(root)) {
            try {
                Files.createDirectories(root);
            } catch (IOException exception) {
                plugin.getLogger().warning("Failed to create JavaScript attribute extension directory: " + exception.getMessage());
            }
            return List.of();
        }
        try (java.util.stream.Stream<Path> stream = Files.walk(root)) {
            Path scriptRoot = plugin.coreLib().dataPath(scriptConfig.paths().root());
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".js"))
                    .sorted(Comparator.comparing(Path::toString))
                    .map(path -> scriptRoot.relativize(path).toString().replace('\\', '/'))
                    .filter(Texts::isNotBlank)
                    .toList();
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to scan JavaScript attribute extensions: " + exception.getMessage());
            return List.of();
        }
    }
}
