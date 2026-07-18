package emaki.jiuwu.craft.corelib.yaml;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BoostedYamlSectionTest {

    @Test
    void setNullRemovesNestedKey() {
        YamlSection root = YamlFiles.load("""
                web_console:
                  runtime_opt_in: true
                  enabled: false
                """);

        assertTrue(root.contains("web_console.runtime_opt_in"));

        root.set("web_console.runtime_opt_in", null);

        assertFalse(root.contains("web_console.runtime_opt_in"));
        assertNull(root.get("web_console.runtime_opt_in"));
        assertTrue(root.contains("web_console.enabled"));
    }
}
