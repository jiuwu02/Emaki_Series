package emaki.jiuwu.craft.corelib.api.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class YamlFilesMissingKeysTest {

    @Test
    void countMissingKeysCountsAbsentLeafKeys() {
        YamlSection runtime = YamlFiles.load("""
                a: 1
                b:
                  c: 2
                """);
        YamlSection defaults = YamlFiles.load("""
                a: 1
                b:
                  c: 2
                d: 3
                e:
                  f: 4
                """);
        assertEquals(2, YamlFiles.countMissingKeys(runtime, defaults));
    }

    @Test
    void countMissingKeysCountsLeavesOfAbsentSection() {
        YamlSection runtime = YamlFiles.load("a: 1");
        YamlSection defaults = YamlFiles.load("""
                a: 1
                nested:
                  x: 1
                  y: 2
                """);
        assertEquals(2, YamlFiles.countMissingKeys(runtime, defaults));
    }

    @Test
    void countMissingKeysReturnsZeroWhenCompleteOrEmpty() {
        YamlSection complete = YamlFiles.load("""
                a: 1
                b:
                  c: 2
                """);
        assertEquals(0, YamlFiles.countMissingKeys(complete, complete));
        assertEquals(0, YamlFiles.countMissingKeys(complete, YamlFiles.load("")));
        assertEquals(0, YamlFiles.countMissingKeys(null, complete));
        assertEquals(0, YamlFiles.countMissingKeys(complete, null));
    }

    @Test
    void countMissingKeysKeepsExistingValuesUntouched() {
        String payload = "kept: original";
        YamlSection runtime = YamlFiles.load(payload);
        YamlSection defaults = YamlFiles.load("kept: overridden\nadded: 1");
        assertEquals(1, YamlFiles.countMissingKeys(runtime, defaults));
        assertEquals("original", runtime.getString("kept", ""));
    }

    @Test
    void mergeMissingKeysWithoutPluginReturnsZero(@TempDir Path tempDir) throws Exception {
        File target = tempDir.resolve("config.yml").toFile();
        assertDoesNotThrow(() -> Files.writeString(target.toPath(), "a: 1"));
        assertEquals(0, YamlFiles.mergeMissingKeys(null, target, "config.yml"));
    }
}
