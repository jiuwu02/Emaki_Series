package emaki.jiuwu.craft.corelib.collection;

import java.util.UUID;

public final class UuidLongMap extends AbstractUuidKeyedMap {

    public static final long ABSENT = Long.MIN_VALUE;

    @FunctionalInterface
    public interface EntryConsumer {

        void accept(long mostSignificantBits, long leastSignificantBits, long value);
    }

    public UuidLongMap() {
        super(DEFAULT_SEGMENTS);
    }

    public UuidLongMap(int segmentCount) {
        super(segmentCount);
    }

    public int size() {
        return totalSize();
    }

    public boolean isEmpty() {
        return empty();
    }

    public void clear() {
        reset();
    }

    public boolean containsKey(UUID key) {
        long most = key.getMostSignificantBits();
        long least = key.getLeastSignificantBits();
        Segment segment = segmentFor(most, least);
        segment.lock.lock();
        try {
            return locate(segment, most, least) >= 0;
        } finally {
            segment.lock.unlock();
        }
    }

    public long getOrDefault(UUID key, long defaultValue) {
        long most = key.getMostSignificantBits();
        long least = key.getLeastSignificantBits();
        Segment segment = segmentFor(most, least);
        segment.lock.lock();
        try {
            return rawOrDefault(segment, most, least, defaultValue);
        } finally {
            segment.lock.unlock();
        }
    }

    public long put(UUID key, long value) {
        long most = key.getMostSignificantBits();
        long least = key.getLeastSignificantBits();
        Segment segment = segmentFor(most, least);
        segment.lock.lock();
        try {
            int index = locate(segment, most, least);
            if (index < 0) {
                index = slotFor(segment, most, least);
                segment.values[index] = value;
                return ABSENT;
            }
            long previous = segment.values[index];
            segment.values[index] = value;
            return previous;
        } finally {
            segment.lock.unlock();
        }
    }

    public long remove(UUID key) {
        long most = key.getMostSignificantBits();
        long least = key.getLeastSignificantBits();
        Segment segment = segmentFor(most, least);
        segment.lock.lock();
        try {
            return removeRaw(segment, most, least, ABSENT);
        } finally {
            segment.lock.unlock();
        }
    }

    public void forEach(EntryConsumer consumer) {
        for (Segment segment : segments) {
            segment.lock.lock();
            try {
                byte[] states = segment.states;
                for (int i = 0; i < states.length; i++) {
                    if (states[i] == STATE_USED) {
                        consumer.accept(segment.keysMost[i], segment.keysLeast[i], segment.values[i]);
                    }
                }
            } finally {
                segment.lock.unlock();
            }
        }
    }
}
