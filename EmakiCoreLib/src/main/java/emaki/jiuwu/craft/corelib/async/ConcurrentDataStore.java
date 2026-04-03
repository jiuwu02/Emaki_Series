package emaki.jiuwu.craft.corelib.async;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class ConcurrentDataStore<T> {

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final List<BiConsumer<Long, T>> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong version = new AtomicLong();
    private T value;

    public ConcurrentDataStore(T initialValue) {
        this.value = initialValue;
    }

    public <R> R read(Function<T, R> reader) {
        Objects.requireNonNull(reader, "reader");
        lock.readLock().lock();
        try {
            return reader.apply(value);
        } finally {
            lock.readLock().unlock();
        }
    }

    public <R> R write(Function<T, R> writer) {
        Objects.requireNonNull(writer, "writer");
        long updatedVersion;
        R result;
        T currentValue;
        lock.writeLock().lock();
        try {
            result = writer.apply(value);
            currentValue = value;
            updatedVersion = version.incrementAndGet();
        } finally {
            lock.writeLock().unlock();
        }
        notifyListeners(updatedVersion, currentValue);
        return result;
    }

    public VersionedValue<T> readVersioned() {
        lock.readLock().lock();
        try {
            return new VersionedValue<>(version.get(), value);
        } finally {
            lock.readLock().unlock();
        }
    }

    public T update(UnaryOperator<T> updater) {
        Objects.requireNonNull(updater, "updater");
        long updatedVersion;
        T updatedValue;
        lock.writeLock().lock();
        try {
            value = updater.apply(value);
            updatedValue = value;
            updatedVersion = version.incrementAndGet();
        } finally {
            lock.writeLock().unlock();
        }
        notifyListeners(updatedVersion, updatedValue);
        return updatedValue;
    }

    public boolean compareAndSet(long expectedVersion, UnaryOperator<T> updater) {
        Objects.requireNonNull(updater, "updater");
        long updatedVersion;
        T updatedValue;
        lock.writeLock().lock();
        try {
            if (version.get() != expectedVersion) {
                return false;
            }
            value = updater.apply(value);
            updatedValue = value;
            updatedVersion = version.incrementAndGet();
        } finally {
            lock.writeLock().unlock();
        }
        notifyListeners(updatedVersion, updatedValue);
        return true;
    }

    public void set(T value) {
        update(ignored -> value);
    }

    public long version() {
        return version.get();
    }

    public void addListener(BiConsumer<Long, T> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(BiConsumer<Long, T> listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    private void notifyListeners(long updatedVersion, T currentValue) {
        for (BiConsumer<Long, T> listener : listeners) {
            listener.accept(updatedVersion, currentValue);
        }
    }

    public record VersionedValue<T>(long version, T value) {

    }
}
