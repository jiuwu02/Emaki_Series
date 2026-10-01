package emaki.jiuwu.craft.corelib.script.host;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("ScriptCallbackRunner 回调执行")
class ScriptCallbackRunnerTest {

    @TempDir
    Path dataDirectory;

    @Test
    @DisplayName("正常返回、超时、异常三分支符合契约，且超时后 Context 仍可用")
    void normalTimeoutAndErrorBranches() throws IOException, ScriptCallbackException {
        Path scripts = dataDirectory.resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("ok.js"),
                "capture.registerDouble(function (v) { return v * 2; });");
        Files.writeString(scripts.resolve("loop.js"),
                "capture.registerLoop(function () { while (true) { } });");
        Files.writeString(scripts.resolve("boom.js"),
                "capture.registerBoom(function () { throw new Error('boom'); });");

        Capture capture = new Capture();
        ScriptHost host = new ScriptHost(null, scripts,
                new ScriptHostSettings(true, 500, false), Map.of("capture", capture));
        try {
            ScriptLoadReport report = host.load();

            assertEquals(3, report.filesLoaded());
            assertTrue(report.errors().isEmpty());

            ScriptCallbackRunner runner = host.callbackRunner();

            assertEquals(42, runner.run(capture.doubleFunction(), 21).asInt());

            ScriptCallbackException timeout = assertThrows(ScriptCallbackException.class,
                    () -> runner.run(capture.loopFunction()));
            assertTrue(timeout.isTimeout(),
                    "isTimeout=" + timeout.isTimeout() + " isInterrupted=" + timeout.isInterrupted()
                            + " cause=" + timeout.getCause());
            assertFalse(timeout.isInterrupted());

            assertEquals(6, runner.run(capture.doubleFunction(), 3).asInt());

            ScriptCallbackException failure = assertThrows(ScriptCallbackException.class,
                    () -> runner.run(capture.boomFunction()));
            assertFalse(failure.isTimeout());
            assertFalse(failure.isInterrupted());
            assertInstanceOf(PolyglotException.class, failure.getCause());
            assertTrue(failure.getCause().getMessage().contains("boom"));
        } finally {
            host.close();
        }
    }

    @Test
    @DisplayName("Java Map/List 深转换为可属性访问对象，null 转换为 JS null")
    void convertsArgumentsForPropertyAccess() throws IOException, ScriptCallbackException {
        Path scripts = dataDirectory.resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("conv.js"),
                "capture.registerConv(function (v) { return v.name + '-' + v.nested.id + '-' + v.tags.length; });");
        Files.writeString(scripts.resolve("nullcheck.js"),
                "capture.registerNull(function (v) { return v === null; });");

        Capture capture = new Capture();
        ScriptHost host = new ScriptHost(null, scripts,
                new ScriptHostSettings(true, 500, false), Map.of("capture", capture));
        try {
            ScriptLoadReport report = host.load();

            assertEquals(2, report.filesLoaded());
            assertTrue(report.errors().isEmpty());

            ScriptCallbackRunner runner = host.callbackRunner();

            Map<String, Object> argument = new HashMap<>();
            argument.put("name", "sword");
            argument.put("nested", Map.of("id", "x"));
            argument.put("tags", List.of("a", "b"));

            assertEquals("sword-x-2", runner.run(capture.convFunction(), argument).asString());
            assertTrue(runner.run(capture.nullFunction(), (Object) null).asBoolean());
        } finally {
            host.close();
        }
    }

    public static final class Capture {

        private Value doubleFunction;
        private Value loopFunction;
        private Value boomFunction;
        private Value convFunction;
        private Value nullFunction;

        public void registerDouble(Value function) {
            this.doubleFunction = function;
        }

        public void registerLoop(Value function) {
            this.loopFunction = function;
        }

        public void registerBoom(Value function) {
            this.boomFunction = function;
        }

        public void registerConv(Value function) {
            this.convFunction = function;
        }

        public void registerNull(Value function) {
            this.nullFunction = function;
        }

        public Value doubleFunction() {
            return doubleFunction;
        }

        public Value loopFunction() {
            return loopFunction;
        }

        public Value boomFunction() {
            return boomFunction;
        }

        public Value convFunction() {
            return convFunction;
        }

        public Value nullFunction() {
            return nullFunction;
        }
    }
}
