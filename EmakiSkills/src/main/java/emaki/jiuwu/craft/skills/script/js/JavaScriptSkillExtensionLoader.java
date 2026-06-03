package emaki.jiuwu.craft.skills.script.js;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.script.JavaScriptService;
import emaki.jiuwu.craft.corelib.script.ScriptConfig;
import emaki.jiuwu.craft.corelib.script.ScriptExecutionResult;
import emaki.jiuwu.craft.corelib.script.ScriptInvocationRequest;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.skills.EmakiSkillsPlugin;
import emaki.jiuwu.craft.skills.api.SkillScriptActionRegistry;

public final class JavaScriptSkillExtensionLoader implements AutoCloseable {

    private static final String EXTENSION_ROOT = "extensions/skills";

    private final EmakiSkillsPlugin plugin;
    private final SkillScriptActionRegistry registry;
    private final JavaScriptService javaScriptService;
    private final ScriptConfig scriptConfig;
    private final List<String> registeredIds = new ArrayList<>();

    public JavaScriptSkillExtensionLoader(EmakiSkillsPlugin plugin,
            SkillScriptActionRegistry registry,
            JavaScriptService javaScriptService,
            ScriptConfig scriptConfig) {
        this.plugin = plugin;
        this.registry = registry;
        this.javaScriptService = javaScriptService;
        this.scriptConfig = scriptConfig == null ? ScriptConfig.defaults() : scriptConfig;
    }

    public int reload() {
        close();
        if (plugin == null || registry == null || javaScriptService == null || !javaScriptService.enabled()) {
            return 0;
        }
        List<String> scripts = scanScripts();
        int loaded = 0;
        for (String scriptPath : scripts) {
            JavaScriptSkillRegistrationApi registrationApi = new JavaScriptSkillRegistrationApi(
                    plugin,
                    registry,
                    javaScriptService,
                    scriptConfig,
                    scriptPath
            );
            ScriptExecutionResult result = javaScriptService.invoke(new ScriptInvocationRequest(
                    plugin,
                    null,
                    scriptPath,
                    "register",
                    List.of(registrationApi),
                    Map.of("extension", "skills", "script", scriptPath),
                    scriptConfig.clampTimeoutMillis(scriptConfig.engine().defaultTimeoutMillis()),
                    false
            ));
            if (result != null && result.success() && !result.skipped()) {
                registeredIds.addAll(registrationApi.registeredIds());
                loaded++;
            } else {
                String message = result == null ? "no result" : result.message();
                plugin.getLogger().warning("Failed to load JavaScript skill extension " + scriptPath + ": " + message);
            }
        }
        if (loaded > 0) {
            plugin.getLogger().info("Loaded " + loaded + " JavaScript skill extension script(s), actions=" + registeredIds.size());
        }
        return loaded;
    }

    @Override
    public void close() {
        if (registry != null) {
            for (String id : List.copyOf(registeredIds)) {
                registry.unregister(id);
            }
        }
        registeredIds.clear();
    }

    private List<String> scanScripts() {
        Path root = plugin.coreLib().dataPath(scriptConfig.paths().root()).resolve(EXTENSION_ROOT);
        if (!Files.exists(root)) {
            try {
                Files.createDirectories(root);
            } catch (IOException exception) {
                plugin.getLogger().warning("Failed to create JavaScript skill extension directory: " + exception.getMessage());
            }
            return List.of();
        }
        try (java.util.stream.Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".js"))
                    .sorted(Comparator.comparing(Path::toString))
                    .map(path -> toLogicalPath(root, path))
                    .filter(Texts::isNotBlank)
                    .toList();
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to scan JavaScript skill extensions: " + exception.getMessage());
            return List.of();
        }
    }

    private String toLogicalPath(Path root, Path path) {
        Path scriptRoot = plugin.coreLib().dataPath(scriptConfig.paths().root());
        return scriptRoot.relativize(path).toString().replace('\\', '/');
    }
}
