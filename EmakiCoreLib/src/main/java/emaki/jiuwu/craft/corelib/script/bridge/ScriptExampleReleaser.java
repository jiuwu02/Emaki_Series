package emaki.jiuwu.craft.corelib.script.bridge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.log.LogTags;
import emaki.jiuwu.craft.corelib.log.LogTag;
import java.util.Map;
import java.util.logging.Level;

final class ScriptExampleReleaser {

    private static final List<String> EXAMPLE_RESOURCES = List.of(
            "scripts/lib/example_lib.js",
            "scripts/actions/example_stage.js",
            "scripts/actions/example_combat_stage.js",
            "scripts/expressions/example_function.js",
            "scripts/expressions/example_clamp.js",
            "scripts/placeholders/example_placeholder.js",
            "scripts/placeholders/example_team.js",
            "scripts/conditions/example_condition.js",
            "scripts/conditions/example_level_gate.js"
    );

    private ScriptExampleReleaser() {
    }

    static void releaseIfNeeded(@NotNull JavaPlugin plugin, @NotNull Path scriptsDirectory) {
        if (Files.isDirectory(scriptsDirectory)) {
            return;
        }
        try {
            Files.createDirectories(scriptsDirectory);
        } catch (IOException exception) {
            LogTags.send(plugin, LogTag.SCRIPT, Level.WARNING, "script.create_directory_failed", Map.of("path", scriptsDirectory, "error", exception.getMessage()));
            return;
        }
        for (String resource : EXAMPLE_RESOURCES) {
            Path target = scriptsDirectory.resolve(resource.substring("scripts/".length()));
            try {
                YamlFiles.copyResourceIfMissing(plugin, resource, target.toFile());
            } catch (IOException exception) {
                LogTags.send(plugin, LogTag.SCRIPT, Level.WARNING, "script.release_example_failed", Map.of("resource", resource, "error", exception.getMessage()));
            }
        }
    }
}
