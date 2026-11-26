package emaki.jiuwu.craft.dungeon.editor;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.debug.FailureRecorder;

public final class GuiResourceReleaser {

    private static final String ROOT = "gui";

    private GuiResourceReleaser() {
    }

    public static int release(JavaPlugin plugin) {
        if (plugin == null) {
            return 0;
        }
        File directory = new File(plugin.getDataFolder(), ROOT);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            return 0;
        }
        List<String> resources = YamlFiles.listResourcePaths(plugin, ROOT);
        int written = 0;
        for (String resource : resources) {
            String name = resource.substring(resource.lastIndexOf('/') + 1);
            if (name.isBlank()) {
                continue;
            }
            File target = new File(directory, name);
            try {
                if (YamlFiles.copyResourceIfMissing(plugin, resource, target)) {
                    written++;
                }
            } catch (IOException exception) {
                FailureRecorder.recordIgnoredFailure("EmakiDungeon", "GuiResourceReleaser.java#0",
                        exception);
            }
        }
        return written;
    }
}
