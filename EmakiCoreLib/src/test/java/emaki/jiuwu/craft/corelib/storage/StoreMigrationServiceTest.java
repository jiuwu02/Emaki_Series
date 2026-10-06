package emaki.jiuwu.craft.corelib.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.async.AsyncFileService;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;

class StoreMigrationServiceTest {

    @TempDir
    Path tempDir;

    private AsyncTaskScheduler scheduler;
    private AsyncFileService fileService;
    private StoreMigrationService service;

    @BeforeEach
    void setUp() {
        scheduler = new AsyncTaskScheduler(2, 30_000L, "storage-migration-test");
        fileService = new AsyncFileService(scheduler);
        service = new StoreMigrationService(fileService.openScope("migration-test"));
    }

    @AfterEach
    void tearDown() {
        fileService.closeAndDrain(5L, TimeUnit.SECONDS);
        scheduler.shutdownGracefully(5L, TimeUnit.SECONDS);
    }

    @Test
    void binaryMigrationBacksUpVerifiesAndRollsBackByteIdentically() throws Exception {
        Path source = writeSource("binary-player.yml");
        byte[] original = Files.readAllBytes(source);

        StoreMigrationService.MigrationResult migration =
                service.migrate(source, StoreFormat.BINARY).get(10L, TimeUnit.SECONDS);
        assertTrue(migration.migrated());
        assertTrue(migration.backupCreated());
        assertTrue(migration.entryCount() > 0);
        assertTrue(Files.exists(migration.target()));
        assertArrayEquals(original, Files.readAllBytes(source));
        assertArrayEquals(original, Files.readAllBytes(migration.backup()));

        StoreMigrationService.VerificationResult verification =
                service.verify(source, StoreFormat.BINARY).get(10L, TimeUnit.SECONDS);
        assertTrue(verification.valid(), () -> String.join("; ", verification.differences()));

        StoreMigrationService.RollbackResult rollback = service.rollback(source).get(10L, TimeUnit.SECONDS);
        assertTrue(rollback.restored());
        assertArrayEquals(original, Files.readAllBytes(source));
        assertFalse(Files.exists(migration.target()));
    }

    @Test
    void sqliteMigrationBacksUpVerifiesAndRollsBackByteIdentically() throws Exception {
        Path source = writeSource("sqlite-player.yml");
        byte[] original = Files.readAllBytes(source);

        StoreMigrationService.MigrationResult migration =
                service.migrate(source, StoreFormat.SQLITE).get(10L, TimeUnit.SECONDS);
        assertTrue(Files.exists(migration.target()));

        StoreMigrationService.VerificationResult verification =
                service.verify(source, StoreFormat.SQLITE).get(10L, TimeUnit.SECONDS);
        assertTrue(verification.valid(), () -> String.join("; ", verification.differences()));

        StoreMigrationService.RollbackResult rollback = service.rollback(source).get(10L, TimeUnit.SECONDS);
        assertTrue(rollback.restored());
        assertArrayEquals(original, Files.readAllBytes(source));
        assertFalse(Files.exists(migration.target()));
    }

    @Test
    void secondMigrationReusesExistingBackup() throws Exception {
        Path source = writeSource("reuse-player.yml");
        byte[] original = Files.readAllBytes(source);

        service.migrate(source, StoreFormat.BINARY).get(10L, TimeUnit.SECONDS);
        Files.writeString(source, "mutated: true\n");
        StoreMigrationService.MigrationResult second =
                service.migrate(source, StoreFormat.BINARY).get(10L, TimeUnit.SECONDS);

        assertFalse(second.backupCreated());
        assertArrayEquals(original, Files.readAllBytes(second.backup()));
    }

    @Test
    void verifyDetectsDivergentTarget() throws Exception {
        Path source = writeSource("divergent-player.yml");
        StoreMigrationService.MigrationResult migration =
                service.migrate(source, StoreFormat.BINARY).get(10L, TimeUnit.SECONDS);

        BinaryFileFormat.write(migration.target(), Map.of("version", "mutated: true\n".getBytes(StandardCharsets.UTF_8)));
        StoreMigrationService.VerificationResult verification =
                service.verify(source, StoreFormat.BINARY).get(10L, TimeUnit.SECONDS);

        assertFalse(verification.valid());
        assertFalse(verification.differences().isEmpty());
    }

    @Test
    void dumpWritesComparableYaml() throws Exception {
        Path source = writeSource("dump-player.yml");
        StoreMigrationService.MigrationResult migration =
                service.migrate(source, StoreFormat.BINARY).get(10L, TimeUnit.SECONDS);
        Path dumpFile = tempDir.resolve("dump-player.dump.yml");

        Path written = service.dump(source, StoreFormat.BINARY, dumpFile).get(10L, TimeUnit.SECONDS);

        assertEquals(dumpFile, written);
        assertTrue(Files.exists(written));
        assertTrue(Files.readString(written).contains("version"));
    }

    @Test
    void migrationPreservesEmptyCollections() throws Exception {
        Path source = tempDir.resolve("empty-collections.yml");
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("empty_map", new LinkedHashMap<String, Object>());
        document.put("empty_list", List.of());
        document.put("scalar", 7);
        YamlFiles.save(source.toFile(), document);

        service.migrate(source, StoreFormat.BINARY).get(10L, TimeUnit.SECONDS);
        StoreMigrationService.VerificationResult verification =
                service.verify(source, StoreFormat.BINARY).get(10L, TimeUnit.SECONDS);

        assertTrue(verification.valid(), () -> String.join("; ", verification.differences()));
    }

    @Test
    void rollbackWithoutBackupIsRejected() throws Exception {
        Path source = writeSource("no-backup-player.yml");

        ExecutionException exception = assertThrows(ExecutionException.class,
                () -> service.rollback(source).get(10L, TimeUnit.SECONDS));
        assertTrue(exception.getCause() instanceof StorageException);
    }

    private Path writeSource(String name) throws Exception {
        Path source = tempDir.resolve(name);
        YamlFiles.save(source.toFile(), document());
        return source;
    }

    private static Map<String, Object> document() {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("version", 2);
        Map<String, Object> slots = new LinkedHashMap<>();
        slots.put("38", "material: DIAMOND");
        document.put("slots", slots);
        document.put("unlocked", List.of("alpha", "beta"));
        document.put("owner", "0f6a3c1e-0000-0000-0000-000000000001");
        return document;
    }
}
