package emaki.jiuwu.craft.dummy.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import emaki.jiuwu.craft.dummy.config.AppConfig;
import emaki.jiuwu.craft.dummy.log.DummyLogger;
import emaki.jiuwu.craft.dummy.session.DummyManager;
import emaki.jiuwu.craft.dummy.transport.DummyTransport;
import emaki.jiuwu.craft.dummy.transport.TransportCallbacks;
import emaki.jiuwu.craft.dummy.transport.TransportEndpoint;
import emaki.jiuwu.craft.dummy.transport.TransportLink;

class ScenarioRunnerTest {

    private static final class ScriptedContext implements ExpressionContext {
        double onlineCount = 1.0D;

        @Override
        public double onlineCount() {
            return onlineCount;
        }

        @Override
        public double dummyOnline(String name) {
            return onlineCount > 0 ? 1.0D : 0.0D;
        }

        @Override
        public double eventCount(String type) {
            return 0.0D;
        }

        @Override
        public boolean auditContains(String text) {
            return false;
        }

        @Override
        public boolean recvContains(String name, String text) {
            return false;
        }

        @Override
        public double inventoryCount(String name) {
            return -1.0D;
        }
    }

    private static final class ScriptedRuntime implements ScenarioRuntime {
        long now = 1_000_000L;
        boolean logMatch;
        boolean eventMatch;

        @Override
        public long currentTimeMillis() {
            return now;
        }

        @Override
        public double eventCount(String type) {
            return 0.0D;
        }

        @Override
        public boolean eventMatched(String type, String where, double sinceCount) {
            return eventMatch;
        }

        @Override
        public boolean logContains(String fileGlob, String contains) {
            return logMatch;
        }

        @Override
        public boolean auditContains(String text) {
            return false;
        }

        @Override
        public void ensureWatching(String type) {
        }

        void advance(long millis) {
            now += millis;
        }
    }

    private static final class NullTransport implements DummyTransport {
        @Override
        public TransportLink open(String dummyName, TransportEndpoint endpoint,
                TransportCallbacks callbacks) {
            throw new IllegalStateException("transport must not be used in runner tests");
        }

        @Override
        public void shutdown() {
        }
    }

    @TempDir
    Path tempDir;

    private DummyLogger logger;
    private DummyManager manager;
    private final ScriptedContext context = new ScriptedContext();
    private final ScriptedRuntime runtime = new ScriptedRuntime();

    private ScenarioRunner createRunner(String id, String yaml) throws ScenarioLoadException {
        logger = new DummyLogger(tempDir, 14, false);
        logger.open();
        manager = new DummyManager(null, AppConfig::defaults, logger, new NullTransport());
        ScenarioDefinition definition = ScenarioLoader.parse(yaml, id);
        return new ScenarioRunner(definition, manager, logger, context, runtime);
    }

    private static void tickUntilDone(ScenarioRunner runner, int maxTicks) {
        int ticks = 0;
        while (!runner.isDone() && ticks < maxTicks) {
            runner.tick();
            ticks++;
        }
    }

    private String logText() throws IOException {
        Path file = tempDir.resolve(LocalDate.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".log");
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    @Test
    void allStepsPassProducePassSummary() throws Exception {
        String yaml = """
                players:
                  - dummy_a
                timeout_seconds: 60
                steps:
                  - action: sleep
                    seconds: 0
                  - action: expect
                    expr: "online_count == 1"
                    timeout: 5
                  - action: assert
                    expr: "1 + 1 == 2"
                """;
        ScenarioRunner runner = createRunner("all_pass", yaml);
        runtime.advance(10);
        tickUntilDone(runner, 20);
        runtime.advance(10);
        runner.tick();

        assertTrue(runner.isDone());
        assertEquals("PASS", runner.result());
        assertEquals(3, runner.passedCount());
        assertEquals(0, runner.failedCount());

        String log = logText();
        assertTrue(log.contains("scenario=all_pass result=PASS passed=3 failed=0"));
        logger.close();
    }

    @Test
    void failedAssertStopsScenarioImmediately() throws Exception {
        String yaml = """
                players:
                  - dummy_a
                timeout_seconds: 60
                steps:
                  - action: assert
                    expr: "1 == 2"
                  - action: sleep
                    seconds: 1
                """;
        ScenarioRunner runner = createRunner("assert_stop", yaml);
        tickUntilDone(runner, 10);

        assertTrue(runner.isDone());
        assertEquals("FAIL", runner.result());
        assertEquals(1, runner.failedCount());
        assertFalse(logText().contains("action=sleep"));
        logger.close();
    }

    @Test
    void continueOnFailKeepsSteppingButMarksResultFail() throws Exception {
        String yaml = """
                players:
                  - dummy_a
                timeout_seconds: 60
                steps:
                  - action: assert
                    expr: "1 == 2"
                    on_fail: continue
                  - action: expect
                    expr: "online_count == 1"
                    timeout: 5
                """;
        ScenarioRunner runner = createRunner("continue_fail", yaml);
        tickUntilDone(runner, 10);

        assertTrue(runner.isDone());
        assertEquals("FAIL", runner.result());
        assertEquals(1, runner.passedCount());
        assertEquals(1, runner.failedCount());
        logger.close();
    }

    @Test
    void eventWaitTimeoutReportsFailureDetail() throws Exception {
        String yaml = """
                players:
                  - dummy_a
                timeout_seconds: 60
                steps:
                  - action: wait_event
                    type: DungeonEnterEvent
                    timeout: 1
                """;
        ScenarioRunner runner = createRunner("event_timeout", yaml);
        runner.tick();
        runtime.advance(1500);
        tickUntilDone(runner, 5);

        assertTrue(runner.isDone());
        assertEquals("FAIL", runner.result());
        assertTrue(logText().contains("reason=timeout wait=event"));
        assertTrue(logText().contains("type=DungeonEnterEvent"));
        logger.close();
    }

    @Test
    void expectTimeoutReportsLastActualValue() throws Exception {
        String yaml = """
                players:
                  - dummy_a
                timeout_seconds: 60
                steps:
                  - action: expect
                    expr: "online_count == 2"
                    timeout: 1
                """;
        context.onlineCount = 1.0D;
        ScenarioRunner runner = createRunner("expect_timeout", yaml);
        runner.tick();
        runtime.advance(1500);
        tickUntilDone(runner, 5);

        assertTrue(runner.isDone());
        assertEquals("FAIL", runner.result());
        assertTrue(logText().contains("last=actual=false"), logText());
        logger.close();
    }

    @Test
    void waitLogPassesWhenRuntimeReportsMatch() throws Exception {
        String yaml = """
                players:
                  - dummy_a
                timeout_seconds: 60
                steps:
                  - action: wait_log
                    file: "plugins/EmakiDungeon/logs/audit-*.log"
                    contains: "event=complete"
                    timeout: 5
                """;
        ScenarioRunner runner = createRunner("log_wait", yaml);
        runner.tick();
        runtime.logMatch = true;
        tickUntilDone(runner, 5);

        assertTrue(runner.isDone());
        assertEquals("PASS", runner.result());
        assertEquals(1, runner.passedCount());
        logger.close();
    }

    @Test
    void manualStopEndsWithFailResult() throws Exception {
        String yaml = """
                players:
                  - dummy_a
                timeout_seconds: 60
                steps:
                  - action: wait_event
                    type: DungeonEnterEvent
                    timeout: 30
                """;
        ScenarioRunner runner = createRunner("manual_stop", yaml);
        runner.tick();
        runner.requestStop();
        runner.tick();

        assertTrue(runner.isDone());
        assertEquals("FAIL", runner.result());
        assertTrue(logText().contains("stop_requested"));
        logger.close();
    }

    @Test
    void finishLeavesDeclaredPlayersOutOfManager() throws Exception {
        String yaml = """
                players:
                  - dummy_a
                steps:
                  - action: sleep
                    seconds: 0
                """;
        ScenarioRunner runner = createRunner("cleanup", yaml);
        tickUntilDone(runner, 10);
        assertTrue(runner.isDone());
        assertNull(manager.find("dummy_a"));
        logger.close();
    }
}
