package emaki.jiuwu.craft.attribute.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;

final class TemporaryEntityState {

    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, TemporaryAttributeGroup> groups = new LinkedHashMap<>();

    <T> T write(Function<Map<String, TemporaryAttributeGroup>, T> mutation) {
        lock.lock();
        try {
            return mutation.apply(groups);
        } finally {
            lock.unlock();
        }
    }

    <T> T read(Function<Map<String, TemporaryAttributeGroup>, T> projection) {
        lock.lock();
        try {
            return projection.apply(groups);
        } finally {
            lock.unlock();
        }
    }

    Map<String, TemporaryAttributeGroup> snapshot() {
        return read(Map::copyOf);
    }

    boolean isEmpty() {
        return read(Map::isEmpty);
    }

    <T> T readOrDefault(Function<Map<String, TemporaryAttributeGroup>, T> projection, Supplier<T> empty) {
        lock.lock();
        try {
            return groups.isEmpty() ? empty.get() : projection.apply(groups);
        } finally {
            lock.unlock();
        }
    }
}
