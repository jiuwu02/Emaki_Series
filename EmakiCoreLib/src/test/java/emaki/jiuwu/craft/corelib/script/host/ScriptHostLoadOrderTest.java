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

@DisplayName("脚本宿主扫描与加载顺序")
class ScriptHostLoadOrderTest {

    @TempDir
    Path dataDirectory;

    @Test
    @DisplayName("lib 最先、根级次之、子目录按名称序、文件按文件名序，且全部文件共享同一 Context")
    void loadsInLibRootThenSubDirectoryOrderWithSharedContext() throws IOException {
        Path scripts = dataDirectory.resolve("scripts");
        Files.createDirectories(scripts.resolve("lib"));
        Files.createDirectories(scripts.resolve("actions"));
        Files.createDirectories(scripts.resolve("ztools"));
        Files.writeString(scripts.resolve("lib").resolve("z.js"),
                "collector.add('lib-z'); globalThis.libValue = 'L';");
        Files.writeString(scripts.resolve("a.js"),
                "collector.add('root-a'); globalThis.rootValue = 'R';");
        Files.writeString(scripts.resolve("b.js"),
                "collector.add('root-b:' + libValue + rootValue);");
        Files.writeString(scripts.resolve("actions").resolve("b.js"),
                "collector.add('actions-b:' + rootValue);");
        Files.writeString(scripts.resolve("actions").resolve("a.js"),
                "collector.add('actions-a');");
        Files.writeString(scripts.resolve("ztools").resolve("c.js"),
                "collector.add('ztools-c');");

        List<String> trace = new ArrayList<>();
        ScriptHost host = new ScriptHost(null, scripts,
                new ScriptHostSettings(true, 500, false), Map.of("collector", trace));
        try {
            ScriptLoadReport report = host.load();

            assertEquals(6, report.filesLoaded());
            assertEquals(0, report.filesSkipped());
            assertTrue(report.errors().isEmpty());
            assertEquals(List.of(
                    "lib-z",
                    "root-a",
                    "root-b:LR",
                    "actions-a",
                    "actions-b:R",
                    "ztools-c"
            ), trace);
        } finally {
            host.close();
        }
    }

    @Test
    @DisplayName("scripts 目录不存在时 load 返回空报告")
    void missingScriptsDirectoryYieldsEmptyReport() {
        ScriptHost host = new ScriptHost(null, dataDirectory.resolve("absent"),
                ScriptHostSettings.defaults(), Map.of());
        try {
            ScriptLoadReport report = host.load();

            assertEquals(0, report.filesLoaded());
            assertEquals(0, report.filesSkipped());
            assertTrue(report.errors().isEmpty());
        } finally {
            host.close();
        }
    }
}
