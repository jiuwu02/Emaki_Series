package emaki.jiuwu.craft.attribute.script.js;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.script.js.JavaScriptDamagePipelineRegistry;
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
    private final JavaScriptDamagePipelineRegistry damagePipelineRegistry;
    private final java.util.Set<String> registeredProviders = new java.util.LinkedHashSet<>();

    public JavaScriptAttributeExtensionLoader(EmakiAttributePlugin plugin,
            JavaScriptService javaScriptService,
            ScriptConfig scriptConfig,
            JavaScriptDamageHookRegistry damageHookRegistry,
            JavaScriptDamagePipelineRegistry damagePipelineRegistry) {
        this.plugin = plugin;
        this.javaScriptService = javaScriptService;
        this.scriptConfig = scriptConfig == null ? ScriptConfig.defaults() : scriptConfig;
        this.damageHookRegistry = damageHookRegistry;
        this.damagePipelineRegistry = damagePipelineRegistry;
    }

    public int reload() {
        close();
        if (plugin == null || javaScriptService == null || !javaScriptService.enabled()) {
            return 0;
        }
        int loaded = 0;
        for (String scriptPath : scanScripts()) {
            JavaScriptAttributeRegistrationApi api = new JavaScriptAttributeRegistrationApi(plugin, javaScriptService, scriptConfig, damageHookRegistry, damagePipelineRegistry, scriptPath);
            ScriptExecutionResult result = javaScriptService.invoke(new ScriptInvocationRequest(
                    plugin,
                    null,
                    scriptPath,
                    "register",
                    List.of(),
                    Map.of("extension", "attribute", "script", scriptPath),
                    scriptConfig.clampTimeoutMillis(scriptConfig.engine().defaultTimeoutMillis()),
                    false,
                    Map.of("attribute", api)
            ));
            if (result != null && result.success() && !result.skipped()) {
                registeredProviders.addAll(api.registeredProviders());
                loaded++;
            } else {
                String message = result == null ? plugin.messageService().message("console.js_attribute_extension_no_result") : result.message();
                plugin.messageService().warning("console.js_attribute_extension_load_failed", Map.of(
                        "script", scriptPath,
                        "error", Texts.toStringSafe(message)
                ));
            }
        }
        if (plugin.attributeService() != null) {
            plugin.attributeService().refreshCaches();
            plugin.attributeService().resyncAllPlayers();
        }
        if (loaded > 0) {
            plugin.messageService().info("console.js_attribute_extensions_loaded", Map.of(
                    "count", String.valueOf(loaded),
                    "damage_hooks", String.valueOf(damageHookRegistry == null ? 0 : damageHookRegistry.size()),
                    "damage_pipelines", String.valueOf(damagePipelineRegistry == null ? 0 : damagePipelineRegistry.size())
            ));
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
        if (plugin != null && plugin.damageTypeRegistry() != null) {
            plugin.damageTypeRegistry().clearRuntime();
        }
        if (damageHookRegistry != null) {
            damageHookRegistry.clear();
        }
        if (damagePipelineRegistry != null) {
            damagePipelineRegistry.clear();
        }
    }

    private List<String> scanScripts() {
        Path root = plugin.coreLib().dataPath(scriptConfig.paths().root()).resolve(EXTENSION_ROOT);
        if (!Files.exists(root)) {
            try {
                Files.createDirectories(root);
            } catch (IOException exception) {
                plugin.messageService().warning("console.js_attribute_extension_directory_create_failed", Map.of(
                        "error", Texts.toStringSafe(exception.getMessage())
                ));
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
            plugin.messageService().warning("console.js_attribute_extension_scan_failed", Map.of(
                    "error", Texts.toStringSafe(exception.getMessage())
            ));
            return List.of();
        }
    }
}
