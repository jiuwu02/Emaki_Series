package emaki.jiuwu.craft.corelib.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
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

class BinaryFileStoreTest {

    @TempDir
    Path tempDir;

    private AsyncTaskScheduler scheduler;
    private AsyncFileService fileService;

    @BeforeEach
    void setUp() {
        scheduler = new AsyncTaskScheduler(2, 30_000L, "storage-binary-test");
        fileService = new AsyncFileService(scheduler);
    }

    @AfterEach
    void tearDown() {
        fileService.closeAndDrain(5L, TimeUnit.SECONDS);
        scheduler.shutdownGracefully(5L, TimeUnit.SECONDS);
    }

    @Test
    void roundTripPreservesEveryEntry() throws Exception {
        Path file = tempDir.resolve("players.bin");
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("alpha", "第一个玩家".getBytes(StandardCharsets.UTF_8));
        entries.put("beta", repeat(4096, (byte) 'y'));
        entries.put("gamma", new byte[0]);
        BinaryFileStore store = new BinaryFileStore(fileService.openScope("binary-round-trip"), file);

        store.save(entries).get(10L, TimeUnit.SECONDS);
        Map<String, byte[]> loaded = store.load().get(10L, TimeUnit.SECONDS);

        assertEquals(entries.keySet(), loaded.keySet());
        for (String key : entries.keySet()) {
            assertArrayEquals(entries.get(key), loaded.get(key), key);
        }
    }

    @Test
    void entriesAreCompressed() throws Exception {
        Path file = tempDir.resolve("compressed.bin");
        Map<String, byte[]> entries = Map.of("payload", repeat(16384, (byte) 'z'));
        BinaryFileStore store = new BinaryFileStore(fileService.openScope("binary-compress"), file);

        store.save(entries).get(10L, TimeUnit.SECONDS);
        store.waitForIdle().get(10L, TimeUnit.SECONDS);

        assertTrue(Files.size(file) < entries.get("payload").length / 4,
                "压缩后体积应显著小于原始体积");
    }

    @Test
    void corruptedPayloadIsRejected() throws Exception {
        Path file = tempDir.resolve("corrupt.bin");
        Map<String, byte[]> entries = Map.of("payload", repeat(2048, (byte) 7));
        BinaryFileStore store = new BinaryFileStore(fileService.openScope("binary-corrupt"), file);

        store.save(entries).get(10L, TimeUnit.SECONDS);
        store.waitForIdle().get(10L, TimeUnit.SECONDS);
        byte[] bytes = Files.readAllBytes(file);
        bytes[bytes.length - 1] ^= 0x7F;
        Files.write(file, bytes);

        ExecutionException exception = assertThrows(ExecutionException.class,
                () -> store.load().get(10L, TimeUnit.SECONDS));
        assertTrue(exception.getCause() instanceof StorageException);
    }

    @Test
    void unsupportedVersionIsRejected() throws Exception {
        Path file = tempDir.resolve("versioned.bin");
        byte[] bytes = ByteBuffer.allocate(12)
                .putInt(StoreHeader.MAGIC)
                .putInt(StoreHeader.VERSION + 1)
                .putInt(0)
                .array();
        Files.write(file, bytes);
        BinaryFileStore store = new BinaryFileStore(fileService.openScope("binary-version"), file);

        ExecutionException exception = assertThrows(ExecutionException.class,
                () -> store.load().get(10L, TimeUnit.SECONDS));
        assertTrue(exception.getCause() instanceof StorageException);
    }

    @Test
    void invalidMagicIsRejected() throws Exception {
        Path file = tempDir.resolve("magic.bin");
        byte[] bytes = ByteBuffer.allocate(12)
                .putInt(0x01020304)
                .putInt(StoreHeader.VERSION)
                .putInt(0)
                .array();
        Files.write(file, bytes);
        BinaryFileStore store = new BinaryFileStore(fileService.openScope("binary-magic"), file);

        ExecutionException exception = assertThrows(ExecutionException.class,
                () -> store.load().get(10L, TimeUnit.SECONDS));
        assertTrue(exception.getCause() instanceof StorageException);
    }

    private static byte[] repeat(int length, byte value) {
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, value);
        return bytes;
    }
}
