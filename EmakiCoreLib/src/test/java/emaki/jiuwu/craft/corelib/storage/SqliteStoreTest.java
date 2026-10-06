package emaki.jiuwu.craft.corelib.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import emaki.jiuwu.craft.corelib.async.AsyncFileService;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;

class SqliteStoreTest {

    @TempDir
    Path tempDir;

    private AsyncTaskScheduler scheduler;
    private AsyncFileService fileService;

    @BeforeEach
    void setUp() {
        scheduler = new AsyncTaskScheduler(2, 30_000L, "storage-sqlite-test");
        fileService = new AsyncFileService(scheduler);
    }

    @AfterEach
    void tearDown() {
        fileService.closeAndDrain(5L, TimeUnit.SECONDS);
        scheduler.shutdownGracefully(5L, TimeUnit.SECONDS);
    }

    @Test
    void roundTripPreservesEveryEntry() throws Exception {
        Path file = tempDir.resolve("players.db");
        SqliteStore store = new SqliteStore(fileService.openScope("sqlite-round-trip"), file);
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("alpha", "第一个玩家".getBytes(StandardCharsets.UTF_8));
        entries.put("beta", new byte[] {1, 2, 3, 4, 5});

        store.save(entries).get(10L, TimeUnit.SECONDS);
        Map<String, byte[]> loaded = store.load().get(10L, TimeUnit.SECONDS);

        assertEntriesEquals(entries, loaded);
    }

    @Test
    void overwriteReplacesStoredEntries() throws Exception {
        Path file = tempDir.resolve("overwrite.db");
        SqliteStore store = new SqliteStore(fileService.openScope("sqlite-overwrite"), file);

        store.save(Map.of("alpha", bytes("a"), "beta", bytes("b"))).get(10L, TimeUnit.SECONDS);
        store.save(Map.of("alpha", bytes("c"))).get(10L, TimeUnit.SECONDS);

        assertEntriesEquals(Map.of("alpha", bytes("c")), store.load().get(10L, TimeUnit.SECONDS));
    }

    @Test
    void nonSqliteFileIsRejected() throws Exception {
        Path file = tempDir.resolve("garbage.db");
        Files.writeString(file, "not a database");
        SqliteStore store = new SqliteStore(fileService.openScope("sqlite-garbage"), file);

        ExecutionException exception = assertThrows(ExecutionException.class,
                () -> store.load().get(10L, TimeUnit.SECONDS));
        assertTrue(exception.getCause() instanceof StorageException);
    }

    @Test
    void unsupportedVersionIsRejected() throws Exception {
        Path file = tempDir.resolve("versioned.db");
        SqliteStore store = new SqliteStore(fileService.openScope("sqlite-version"), file);
        store.save(Map.of("alpha", bytes("a"))).get(10L, TimeUnit.SECONDS);

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE store_meta SET meta_value = '99' WHERE meta_key = 'format_version'");
        }

        ExecutionException exception = assertThrows(ExecutionException.class,
                () -> store.load().get(10L, TimeUnit.SECONDS));
        assertTrue(exception.getCause() instanceof StorageException);
    }

    private static void assertEntriesEquals(Map<String, byte[]> expected, Map<String, byte[]> actual) {
        assertEquals(expected.keySet(), actual.keySet());
        for (String key : expected.keySet()) {
            assertArrayEquals(expected.get(key), actual.get(key), key);
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
