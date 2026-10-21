package emaki.jiuwu.craft.dummy.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import emaki.jiuwu.craft.dummy.config.AppConfig;
import emaki.jiuwu.craft.dummy.log.DummyLogger;
import emaki.jiuwu.craft.dummy.log.LogLevel;
import emaki.jiuwu.craft.dummy.session.DummyManager;
import emaki.jiuwu.craft.dummy.transport.DummyTransport;
import emaki.jiuwu.craft.dummy.transport.TransportCallbacks;
import emaki.jiuwu.craft.dummy.transport.TransportEndpoint;
import emaki.jiuwu.craft.dummy.transport.TransportLink;

class ScenarioEngineGlobTest {

    private static final class NullTransport implements DummyTransport {
        @Override
        public TransportLink open(String dummyName, TransportEndpoint endpoint,
                TransportCallbacks callbacks) {
            throw new IllegalStateException("transport must not be used in engine tests");
        }

        @Override
        public void shutdown() {
        }
    }

    @TempDir
    Path serverRoot;

    private DummyLogger logger;

    private ScenarioEngine createEngine() {
        logger = new DummyLogger(serverRoot.resolve("plugins/EmakiDummy/logs/dummy"), 14, false);
        logger.open();
        DummyManager manager = new DummyManager(null, AppConfig::defaults, logger,
                new NullTransport());
        return new ScenarioEngine(AppConfig::defaults, manager, logger, serverRoot);
    }

    @Test
    void globToRegexEscapesLiteralDotsAndKeepsWildcards() {
        assertEquals("audit\\-.*\\.log", ScenarioEngine.globToRegex("audit-*.log"));
        assertEquals("a\\-b\\+c\\.log", ScenarioEngine.globToRegex("a-b+c.log"));
        assertEquals("name\\..log", ScenarioEngine.globToRegex("name.?log"));
    }

    @Test
    void logContainsReadsNewestGlobMatch() throws IOException {
        Path logs = serverRoot.resolve("plugins/EmakiDungeon/logs");
        Files.createDirectories(logs);
        Path older = logs.resolve("audit-2020-01-01.log");
        Path newer = logs.resolve("audit-2026-10-21.log");
        Files.writeString(older, "stale payload", StandardCharsets.UTF_8);
        Files.writeString(newer, "fresh payload event=complete", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(older, FileTime.fromMillis(1_000_000L));
        Files.setLastModifiedTime(newer, FileTime.fromMillis(9_000_000_000L));

        ScenarioEngine engine = createEngine();
        assertTrue(engine.logContains("plugins/EmakiDungeon/logs/audit-*.log", "event=complete"));
        assertFalse(engine.logContains("plugins/EmakiDungeon/logs/audit-*.log", "missing-token"));
        assertFalse(engine.logContains("plugins/EmakiDungeon/logs/none-*.log", "event=complete"));
        logger.close();
    }

    @Test
    void auditContainsScansConfiguredGlobs() throws IOException {
        Path logs = serverRoot.resolve("plugins/EmakiDungeon/logs");
        Files.createDirectories(logs);
        Files.writeString(logs.resolve("audit-2026-10-21.log"),
                "[settle] event=complete instance=i1", StandardCharsets.UTF_8);

        ScenarioEngine engine = createEngine();
        assertTrue(engine.auditContains("event=complete"));
        assertFalse(engine.auditContains("event=never"));
        logger.close();
    }

    @Test
    void absoluteGlobOutsideServerRootStillResolves() throws IOException {
        Path outside = Files.createTempDirectory("emdum-glob");
        Path target = outside.resolve("audit-2026-10-21.log");
        Files.writeString(target, "outside payload", StandardCharsets.UTF_8);

        ScenarioEngine engine = createEngine();
        assertTrue(engine.logContains(target.toAbsolutePath().toString(), "outside payload"));
        String missingGlob = outside.toString().replace('\\', '/') + "/missing-*.log";
        assertFalse(engine.logContains(missingGlob, "x"));
        logger.close();
    }

    @Test
    void eventObservationAccumulatesDetailsForMatching() {
        ScenarioEngine engine = createEngine();
        engine.onObservedEvent("DungeonEnterEvent", "player=dummy_a instance=i1");
        engine.onObservedEvent("DungeonEnterEvent", "player=dummy_b instance=i2");
        engine.onObservedEvent("DungeonExitEvent", "player=dummy_a instance=i1");

        assertEquals(2.0D, engine.eventCount("DungeonEnterEvent"));
        assertTrue(engine.eventMatched("DungeonEnterEvent", "dummy_b", 0));
        assertFalse(engine.eventMatched("DungeonEnterEvent", "dummy_a", 1));
        assertTrue(engine.eventMatched("DungeonExitEvent", null, 0));
        assertEquals(1.0D, engine.eventCount("DungeonExitEvent"));
        logger.close();
    }

    @Test
    void recvContainsMatchesConfiguredActorAndLevelOnly() {
        ScenarioEngine engine = createEngine();
        logger.log(LogLevel.RECV, "dummy_b", "probe text");
        logger.log(LogLevel.ACTION, "dummy_a", "probe text");

        assertTrue(engine.recvContains("dummy_b", "probe text"));
        assertFalse(engine.recvContains("dummy_a", "probe text"));
        assertFalse(engine.recvContains("dummy_b", "absent text"));
        logger.close();
    }
}
