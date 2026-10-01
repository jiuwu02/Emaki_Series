package emaki.jiuwu.craft.corelib.script.host;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("脚本宿主超时中断")
class ScriptHostTimeoutTest {

    @TempDir
    Path dataDirectory;

    @Test
    @DisplayName("死循环文件被看门狗中断并按超时记录，Context 随后仍可用于回调")
    void infiniteLoopFileTimesOutAndHostRemainsUsable() throws IOException, ScriptCallbackException {
        Path scripts = dataDirectory.resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("bad.js"), "while (true) { }");
        Files.writeString(scripts.resolve("ok.js"), "capture.register(function (v) { return v * 2; });");

        Capture capture = new Capture();
        ScriptHost host = new ScriptHost(null, scripts,
                new ScriptHostSettings(true, 500, false), Map.of("capture", capture));
        try {
            ScriptLoadReport report = host.load();

            assertEquals(1, report.filesLoaded());
            assertEquals(1, report.filesSkipped());
            assertEquals(1, report.errors().size());
            assertEquals("bad.js", report.errors().getFirst().file());
            assertTrue(report.errors().getFirst().message().contains("timed out"),
                    "message=" + report.errors().getFirst().message());

            assertNotNull(capture.function());
            Value result = host.callbackRunner().run(capture.function(), 21);
            assertEquals(42, result.asInt());
        } finally {
            host.close();
        }
    }

    public static final class Capture {

        private Value function;

        public void register(Value function) {
            this.function = function;
        }

        public Value function() {
            return function;
        }
    }
}
