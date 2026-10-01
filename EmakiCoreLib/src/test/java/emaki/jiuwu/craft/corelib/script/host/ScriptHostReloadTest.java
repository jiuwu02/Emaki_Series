package emaki.jiuwu.craft.corelib.script.host;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("脚本宿主 reload 重建")
class ScriptHostReloadTest {

    @TempDir
    Path dataDirectory;

    @Test
    @DisplayName("reload 关闭并重建 Context，按新内容重新求值且顶层状态重置")
    void reloadRebuildsContextAndReevaluatesContent() throws IOException {
        Path scripts = dataDirectory.resolve("scripts");
        Files.createDirectories(scripts);
        Path counter = scripts.resolve("counter.js");
        Files.writeString(counter,
                "collector.add('v1'); globalThis.count = (globalThis.count || 0) + 1; collector.add('count:' + count);");

        List<String> trace = new ArrayList<>();
        ScriptHost host = new ScriptHost(null, scripts,
                new ScriptHostSettings(true, 500, false), Map.of("collector", trace));
        try {
            ScriptLoadReport first = host.load();

            assertEquals(1, first.filesLoaded());
            assertTrue(first.errors().isEmpty());
            assertEquals(List.of("v1", "count:1"), trace);

            Files.writeString(counter,
                    "collector.add('v2'); globalThis.count = (globalThis.count || 0) + 1; collector.add('count:' + count);");

            ScriptLoadReport second = host.reload();

            assertEquals(1, second.filesLoaded());
            assertTrue(second.errors().isEmpty());
            assertEquals(List.of("v1", "count:1", "v2", "count:1"), trace);
        } finally {
            host.close();
        }
    }
}
