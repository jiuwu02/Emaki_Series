package emaki.jiuwu.craft.corelib.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlFilesTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsNestedYamlStructure() throws IOException {
        File file = tempDir.resolve("nested.yml").toFile();
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> general = new LinkedHashMap<>();
        general.put("prefix", "<gray>[CoreLib]</gray>");
        root.put("general", general);
        Map<String, Object> command = new LinkedHashMap<>();
        Map<String, Object> reload = new LinkedHashMap<>();
        reload.put("success", "<green>ok</green>");
        command.put("reload", reload);
        root.put("command", command);

        YamlFiles.save(file, root);

        assertTrue(file.exists());
        YamlConfiguration configuration = YamlFiles.load(file);
        assertEquals("<gray>[CoreLib]</gray>", configuration.getString("general.prefix"));
        assertEquals("<green>ok</green>", configuration.getString("command.reload.success"));
    }

    @Test
    void mergesMissingNestedValuesWithoutOverwritingExistingData() {
        YamlConfiguration runtime = new YamlConfiguration();
        runtime.set("general.prefix", "<gray>[Custom]</gray>");

        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("general.prefix", "<gray>[Default]</gray>");
        defaults.set("general.footer", "<gray>footer</gray>");
        defaults.set("command.reload.success", "<green>reloaded</green>");

        int merged = YamlFiles.mergeMissingValues(runtime, defaults);

        assertEquals(2, merged);
        assertEquals("<gray>[Custom]</gray>", runtime.getString("general.prefix"));
        assertEquals("<gray>footer</gray>", runtime.getString("general.footer"));
        assertEquals("<green>reloaded</green>", runtime.getString("command.reload.success"));
    }
}
