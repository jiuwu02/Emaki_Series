package emaki.jiuwu.craft.dummy.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DummyLoggerTest {

    private static final DateTimeFormatter FILE_DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final Pattern LINE_PATTERN = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3} "
                    + "\\[(ACTION|RECV|EVENT|SCENARIO|ASSERT|ERROR)] "
                    + "\\[([A-Za-z0-9_\\-]+)] .*");

    @TempDir
    Path tempDir;

    private static String today() {
        return LocalDate.now().format(FILE_DAY);
    }

    @Test
    void everyLevelWritesTimestampedPrefixedLine() throws IOException {
        DummyLogger logger = new DummyLogger(tempDir, 14, false);
        logger.open();
        for (LogLevel level : LogLevel.values()) {
            logger.log(level, "dummy_a", "payload_" + level.token());
        }
        logger.close();

        Path file = tempDir.resolve(today() + ".log");
        assertTrue(Files.isRegularFile(file));
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(LogLevel.values().length, lines.size());
        for (int index = 0; index < lines.size(); index++) {
            var matcher = LINE_PATTERN.matcher(lines.get(index));
            assertTrue(matcher.matches(), lines.get(index));
            assertEquals(LogLevel.values()[index].token(), matcher.group(1));
            assertEquals("dummy_a", matcher.group(2));
            assertTrue(lines.get(index).contains("payload_" + LogLevel.values()[index].token()));
        }
    }

    @Test
    void blankActorFallsBackToSystemToken() throws IOException {
        DummyLogger logger = new DummyLogger(tempDir, 14, false);
        logger.open();
        logger.log(LogLevel.ACTION, "", "no-actor");
        logger.close();

        Path file = tempDir.resolve(today() + ".log");
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertTrue(lines.get(0).contains("[system]"));
    }

    @Test
    void recentBufferSupportsActorScopedMatchingAndReset() throws IOException {
        DummyLogger logger = new DummyLogger(tempDir, 14, false);
        logger.open();
        logger.log(LogLevel.RECV, "dummy_b", "hello-isolation");
        logger.log(LogLevel.RECV, "dummy_a", "other-isolation");

        assertTrue(logger.recentContains("hello-isolation"));
        assertTrue(logger.recentMatch("RECV", "dummy_b", "hello-isolation"));
        assertFalse(logger.recentMatch("RECV", "dummy_b", "other-isolation"));
        assertFalse(logger.recentMatch("EVENT", "dummy_b", "hello-isolation"));

        logger.clearRecent();
        assertFalse(logger.recentContains("hello-isolation"));
        logger.close();
    }

    @Test
    void retentionDeletesFilesOlderThanWindow() throws IOException {
        Path expired = tempDir.resolve("2000-01-01.log");
        Files.writeString(expired, "old", StandardCharsets.UTF_8);
        DummyLogger logger = new DummyLogger(tempDir, 14, false);
        int removed = logger.open();
        logger.close();

        assertTrue(removed >= 1);
        assertFalse(Files.exists(expired));
    }

    @Test
    void perDummyFilesMirrorMainLog() throws IOException {
        DummyLogger logger = new DummyLogger(tempDir, 14, true);
        logger.open();
        logger.log(LogLevel.ACTION, "dummy_c", "mirror-line");
        logger.log(LogLevel.ACTION, "system", "not-mirrored");
        logger.close();

        Path mirrored = tempDir.resolve("dummy_c.log");
        assertTrue(Files.isRegularFile(mirrored));
        List<String> lines = Files.readAllLines(mirrored, StandardCharsets.UTF_8);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("mirror-line"));
        assertFalse(Files.exists(tempDir.resolve("system.log")));
    }
}
