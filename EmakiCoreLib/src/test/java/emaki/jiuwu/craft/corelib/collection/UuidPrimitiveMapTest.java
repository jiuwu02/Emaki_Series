package emaki.jiuwu.craft.corelib.collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

class UuidPrimitiveMapTest {

    private static long bits(double value) {
        return Double.doubleToLongBits(value);
    }

    @Test
    void doubleMapMatchesConcurrentHashMap() {
        Random random = new Random(20261007L);
        UuidDoubleMap map = new UuidDoubleMap();
        Map<UUID, Double> reference = new ConcurrentHashMap<>();
        List<UUID> keys = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            keys.add(UUID.randomUUID());
        }
        for (int step = 0; step < 40000; step++) {
            UUID key = keys.get(random.nextInt(keys.size()));
            switch (random.nextInt(4)) {
                case 0, 1 -> {
                    double value = random.nextDouble() * 1000D;
                    Double expected = reference.put(key, value);
                    double actual = map.put(key, value);
                    assertEquals(bits(expected == null ? UuidDoubleMap.ABSENT : expected), bits(actual));
                }
                case 2 -> {
                    Double expected = reference.remove(key);
                    double actual = map.remove(key);
                    assertEquals(bits(expected == null ? UuidDoubleMap.ABSENT : expected), bits(actual));
                }
                default -> {
                    Double expected = reference.get(key);
                    double actual = map.getOrDefault(key, -1D);
                    assertEquals(bits(expected == null ? -1D : expected), bits(actual));
                }
            }
        }
        assertEquals(reference.size(), map.size());
        assertEquals(reference.isEmpty(), map.isEmpty());
        for (UUID key : keys) {
            assertEquals(reference.containsKey(key), map.containsKey(key));
            assertEquals(bits(reference.getOrDefault(key, -1D)), bits(map.getOrDefault(key, -1D)));
        }
    }

    @Test
    void doubleMapReturnsPreviousValues() {
        UuidDoubleMap map = new UuidDoubleMap();
        UUID key = UUID.randomUUID();
        assertEquals(bits(UuidDoubleMap.ABSENT), bits(map.put(key, 4.5D)));
        assertEquals(4.5D, map.getOrDefault(key, -1D));
        assertEquals(bits(4.5D), bits(map.put(key, 9.25D)));
        assertEquals(9.25D, map.getOrDefault(key, -1D));
        assertEquals(bits(9.25D), bits(map.remove(key)));
        assertEquals(bits(UuidDoubleMap.ABSENT), bits(map.remove(key)));
        assertEquals(-1D, map.getOrDefault(key, -1D));
        assertEquals(0, map.size());
    }

    @Test
    void doubleMapTransformsRemovesAndIterates() {
        UuidDoubleMap map = new UuidDoubleMap();
        Map<UUID, Double> expected = new ConcurrentHashMap<>();
        for (int i = 0; i < 300; i++) {
            UUID key = UUID.randomUUID();
            double value = i * 0.25D;
            map.put(key, value);
            expected.put(key, value);
        }
        map.replaceAllValues(value -> value * 0.5D);
        expected.replaceAll((key, value) -> value * 0.5D);
        map.removeIf((most, least, value) -> value < 10D);
        expected.entrySet().removeIf(entry -> entry.getValue() < 10D);

        Map<UUID, Double> visited = new HashMap<>();
        map.forEach((most, least, value) -> visited.put(new UUID(most, least), value));
        assertEquals(expected, visited);
        assertEquals(expected.size(), map.size());
        assertEquals(expected.isEmpty(), map.isEmpty());
    }

    @Test
    void doubleMapExpandsAndCompactsAcrossRemovals() {
        UuidDoubleMap map = new UuidDoubleMap();
        Map<UUID, Double> expected = new HashMap<>();
        List<UUID> keys = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            UUID key = UUID.randomUUID();
            keys.add(key);
            map.put(key, i);
            expected.put(key, (double) i);
        }
        assertEquals(5000, map.size());
        for (int i = 0; i < 5000; i += 2) {
            map.remove(keys.get(i));
            expected.remove(keys.get(i));
        }
        assertEquals(expected.size(), map.size());
        for (int i = 0; i < 5000; i += 2) {
            map.put(keys.get(i), i + 0.5D);
            expected.put(keys.get(i), i + 0.5D);
        }
        assertEquals(expected.size(), map.size());
        for (UUID key : keys) {
            assertEquals(bits(expected.get(key)), bits(map.getOrDefault(key, -1D)));
        }
    }

    @Test
    void doubleMapConcurrentAccumulationIsExact() throws Exception {
        UuidDoubleMap map = new UuidDoubleMap();
        UUID shared = UUID.randomUUID();
        int threads = 8;
        int perThread = 20000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
                start.await();
                for (int i = 0; i < perThread; i++) {
                    map.addTo(shared, 1D);
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();
        assertEquals((double) threads * perThread, map.getOrDefault(shared, -1D));
        assertEquals(1, map.size());
        map.clear();
        assertEquals(0, map.size());
    }

    @Test
    void longMapMatchesConcurrentHashMap() {
        Random random = new Random(20261008L);
        UuidLongMap map = new UuidLongMap();
        Map<UUID, Long> reference = new ConcurrentHashMap<>();
        List<UUID> keys = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            keys.add(UUID.randomUUID());
        }
        long counter = 0L;
        for (int step = 0; step < 40000; step++) {
            UUID key = keys.get(random.nextInt(keys.size()));
            switch (random.nextInt(4)) {
                case 0, 1 -> {
                    long value = counter++;
                    Long expected = reference.put(key, value);
                    assertEquals(expected == null ? UuidLongMap.ABSENT : expected.longValue(), map.put(key, value));
                }
                case 2 -> {
                    Long expected = reference.remove(key);
                    assertEquals(expected == null ? UuidLongMap.ABSENT : expected.longValue(), map.remove(key));
                }
                default -> {
                    Long expected = reference.get(key);
                    assertEquals(expected == null ? -1L : expected.longValue(), map.getOrDefault(key, -1L));
                }
            }
        }
        assertEquals(reference.size(), map.size());
        assertEquals(reference.isEmpty(), map.isEmpty());
    }

    @Test
    void longMapReturnsPreviousValuesAndIterates() {
        UuidLongMap map = new UuidLongMap();
        Map<UUID, Long> expected = new HashMap<>();
        List<UUID> keys = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            UUID key = UUID.randomUUID();
            keys.add(key);
            assertEquals(UuidLongMap.ABSENT, map.put(key, i));
            expected.put(key, (long) i);
        }
        assertEquals(expected.size(), map.size());
        assertEquals(10L, map.put(keys.get(10), 99L));
        for (UUID key : keys) {
            assertTrue(map.containsKey(key));
        }
        assertEquals(500, map.size());

        Map<UUID, Long> visited = new HashMap<>();
        map.forEach((most, least, value) -> visited.put(new UUID(most, least), value));
        expected.put(keys.get(10), 99L);
        assertEquals(expected, visited);

        for (int i = 0; i < 500; i += 3) {
            assertEquals(expected.remove(keys.get(i)).longValue(), map.remove(keys.get(i)));
        }
        assertEquals(expected.size(), map.size());
        assertEquals(UuidLongMap.ABSENT, map.remove(UUID.randomUUID()));
    }

    @Test
    void primitiveMapsHandleConcurrentDistinctKeys() throws Exception {
        UuidDoubleMap doubleMap = new UuidDoubleMap();
        UuidLongMap longMap = new UuidLongMap();
        int threads = 8;
        int perThread = 5000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int slot = t;
            futures.add(pool.submit(() -> {
                start.await();
                for (int i = 0; i < perThread; i++) {
                    UUID key = new UUID(slot, i);
                    doubleMap.addTo(key, i + 1D);
                    longMap.put(key, i + 1L);
                    assertEquals((double) (i + 1), doubleMap.getOrDefault(key, -1D));
                    assertEquals((long) (i + 1), longMap.getOrDefault(key, -1L));
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();
        assertEquals(threads * perThread, doubleMap.size());
        assertEquals(threads * perThread, longMap.size());
        assertEquals((double) perThread, doubleMap.getOrDefault(new UUID(3L, perThread - 1), -1D));
        assertEquals((long) perThread, longMap.getOrDefault(new UUID(5L, perThread - 1), -1L));
        doubleMap.clear();
        longMap.clear();
        assertEquals(0, doubleMap.size());
        assertEquals(0, longMap.size());
    }
}
