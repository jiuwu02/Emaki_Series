package emaki.jiuwu.craft.corelib.script.bridge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;

final class ScriptExampleReleaser {

    private static final List<String> EXAMPLE_RESOURCES = List.of(
            "scripts/lib/example_lib.js",
            "scripts/actions/example_stage.js",
            "scripts/expressions/example_function.js",
            "scripts/placeholders/example_placeholder.js",
            "scripts/conditions/example_condition.js"
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
            plugin.getLogger().warning("创建脚本目录失败 " + scriptsDirectory + ": "
                    + exception.getMessage());
            return;
        }
        for (String resource : EXAMPLE_RESOURCES) {
            Path target = scriptsDirectory.resolve(resource.substring("scripts/".length()));
            try {
                YamlFiles.copyResourceIfMissing(plugin, resource, target.toFile());
            } catch (IOException exception) {
                plugin.getLogger().warning("释放示例脚本失败 " + resource + ": "
                        + exception.getMessage());
            }
        }
    }
}
