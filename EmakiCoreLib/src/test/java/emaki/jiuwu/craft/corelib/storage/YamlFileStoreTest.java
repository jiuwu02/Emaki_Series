package emaki.jiuwu.craft.corelib.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import emaki.jiuwu.craft.corelib.async.AsyncFileService;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;

class YamlFileStoreTest {

    @TempDir
    Path tempDir;

    private AsyncTaskScheduler scheduler;
    private AsyncFileService fileService;

    @BeforeEach
    void setUp() {
        scheduler = new AsyncTaskScheduler(2, 30_000L, "storage-yaml-test");
        fileService = new AsyncFileService(scheduler);
    }

    @AfterEach
    void tearDown() {
        fileService.closeAndDrain(5L, TimeUnit.SECONDS);
        scheduler.shutdownGracefully(5L, TimeUnit.SECONDS);
    }

    @Test
    void roundTripPreservesStructure() throws Exception {
        Path file = tempDir.resolve("player.yml");
        YamlFileStore store = new YamlFileStore(fileService.openScope("yaml-round-trip"), file);
        Map<String, Object> document = document();

        store.save(document).get(10L, TimeUnit.SECONDS);
        Map<String, Object> loaded = store.load().get(10L, TimeUnit.SECONDS);

        assertEquals(document, loaded);
    }

    @Test
    void resaveKeepsBytesIdentical() throws Exception {
        Path file = tempDir.resolve("stable.yml");
        YamlFileStore store = new YamlFileStore(fileService.openScope("yaml-stable"), file);

        store.save(document()).get(10L, TimeUnit.SECONDS);
        store.waitForIdle().get(10L, TimeUnit.SECONDS);
        byte[] first = Files.readAllBytes(file);

        Map<String, Object> loaded = store.load().get(10L, TimeUnit.SECONDS);
        store.save(loaded).get(10L, TimeUnit.SECONDS);
        store.waitForIdle().get(10L, TimeUnit.SECONDS);

        assertArrayEquals(first, Files.readAllBytes(file));
    }

    @Test
    void missingFileLoadsEmpty() throws Exception {
        Path file = tempDir.resolve("absent.yml");
        YamlFileStore store = new YamlFileStore(fileService.openScope("yaml-absent"), file);

        assertEquals(Map.of(), store.load().get(10L, TimeUnit.SECONDS));
    }

    private static Map<String, Object> document() {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("version", 2);
        Map<String, Object> slots = new LinkedHashMap<>();
        slots.put("38", "material: DIAMOND");
        slots.put("39", "material: EMERALD");
        document.put("slots", slots);
        document.put("unlocked", List.of("alpha", "beta"));
        document.put("owner", "0f6a3c1e-0000-0000-0000-000000000001");
        return document;
    }
}
