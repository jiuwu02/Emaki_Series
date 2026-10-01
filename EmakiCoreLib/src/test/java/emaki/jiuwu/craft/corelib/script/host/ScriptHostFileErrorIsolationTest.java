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

@DisplayName("脚本宿主逐文件错误隔离")
class ScriptHostFileErrorIsolationTest {

    @TempDir
    Path dataDirectory;

    @Test
    @DisplayName("语法错误文件被跳过并记录相对路径与消息，其余文件照常加载")
    void syntaxErrorIsSkippedWhileOtherFilesStillLoad() throws IOException {
        Path scripts = dataDirectory.resolve("scripts");
        Files.createDirectories(scripts.resolve("actions"));
        Files.writeString(scripts.resolve("good1.js"), "collector.add('g1');");
        Files.writeString(scripts.resolve("broken.js"), "function ( { this is not js");
        Files.writeString(scripts.resolve("good2.js"), "collector.add('g2');");
        Files.writeString(scripts.resolve("actions").resolve("bad.js"), "var === ;");

        List<String> trace = new ArrayList<>();
        ScriptHost host = new ScriptHost(null, scripts,
                new ScriptHostSettings(true, 500, false), Map.of("collector", trace));
        try {
            ScriptLoadReport report = host.load();

            assertEquals(2, report.filesLoaded());
            assertEquals(2, report.filesSkipped());
            assertTrue(report.hasErrors());
            assertEquals(2, report.errors().size());
            assertTrue(report.errors().stream().anyMatch(error -> error.file().equals("broken.js")));
            assertTrue(report.errors().stream().anyMatch(error -> error.file().equals("actions/bad.js")));
            assertTrue(report.errors().stream().allMatch(error -> !error.message().isBlank()));
            assertEquals(List.of("g1", "g2"), trace);
        } finally {
            host.close();
        }
    }
}
