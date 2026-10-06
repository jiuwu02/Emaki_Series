package emaki.jiuwu.craft.corelib.collection;

import java.util.UUID;
import java.util.function.DoubleUnaryOperator;

public final class UuidDoubleMap extends AbstractUuidKeyedMap {

    public static final double ABSENT = Double.NaN;

    @FunctionalInterface
    public interface EntryConsumer {

        void accept(long mostSignificantBits, long leastSignificantBits, double value);
    }

    @FunctionalInterface
    public interface EntryPredicate {

        boolean test(long mostSignificantBits, long leastSignificantBits, double value);
    }

    public UuidDoubleMap() {
        super(DEFAULT_SEGMENTS);
    }

    public UuidDoubleMap(int segmentCount) {
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

    public double getOrDefault(UUID key, double defaultValue) {
        long most = key.getMostSignificantBits();
        long least = key.getLeastSignificantBits();
        Segment segment = segmentFor(most, least);
        segment.lock.lock();
        try {
            int index = locate(segment, most, least);
            return index < 0 ? defaultValue : Double.longBitsToDouble(segment.values[index]);
        } finally {
            segment.lock.unlock();
        }
    }

    public double put(UUID key, double value) {
        long most = key.getMostSignificantBits();
        long least = key.getLeastSignificantBits();
        Segment segment = segmentFor(most, least);
        segment.lock.lock();
        try {
            int index = locate(segment, most, least);
            if (index < 0) {
                index = slotFor(segment, most, least);
                segment.values[index] = Double.doubleToRawLongBits(value);
                return ABSENT;
            }
            double previous = Double.longBitsToDouble(segment.values[index]);
            segment.values[index] = Double.doubleToRawLongBits(value);
            return previous;
        } finally {
            segment.lock.unlock();
        }
    }

    public void addTo(UUID key, double delta) {
        long most = key.getMostSignificantBits();
        long least = key.getLeastSignificantBits();
        Segment segment = segmentFor(most, least);
        segment.lock.lock();
        try {
            int index = locate(segment, most, least);
            if (index < 0) {
                index = slotFor(segment, most, least);
                segment.values[index] = Double.doubleToRawLongBits(delta);
                return;
            }
            double previous = Double.longBitsToDouble(segment.values[index]);
            segment.values[index] = Double.doubleToRawLongBits(previous + delta);
        } finally {
            segment.lock.unlock();
        }
    }

    public double remove(UUID key) {
        long most = key.getMostSignificantBits();
        long least = key.getLeastSignificantBits();
        Segment segment = segmentFor(most, least);
        segment.lock.lock();
        try {
            long previous = removeRaw(segment, most, least, Double.doubleToRawLongBits(ABSENT));
            return Double.longBitsToDouble(previous);
        } finally {
            segment.lock.unlock();
        }
    }

    public void replaceAllValues(DoubleUnaryOperator operator) {
        for (Segment segment : segments) {
            segment.lock.lock();
            try {
                byte[] states = segment.states;
                for (int i = 0; i < states.length; i++) {
                    if (states[i] == STATE_USED) {
                        double previous = Double.longBitsToDouble(segment.values[i]);
                        segment.values[i] = Double.doubleToRawLongBits(operator.applyAsDouble(previous));
                    }
                }
            } finally {
                segment.lock.unlock();
            }
        }
    }

    public void removeIf(EntryPredicate predicate) {
        for (Segment segment : segments) {
            segment.lock.lock();
            try {
                byte[] states = segment.states;
                for (int i = 0; i < states.length; i++) {
                    if (states[i] != STATE_USED) {
                        continue;
                    }
                    double value = Double.longBitsToDouble(segment.values[i]);
                    if (predicate.test(segment.keysMost[i], segment.keysLeast[i], value)) {
                        states[i] = STATE_REMOVED;
                        segment.values[i] = 0L;
                        segment.size--;
                        segment.removed++;
                    }
                }
            } finally {
                segment.lock.unlock();
            }
        }
    }

    public void forEach(EntryConsumer consumer) {
        for (Segment segment : segments) {
            segment.lock.lock();
            try {
                byte[] states = segment.states;
                for (int i = 0; i < states.length; i++) {
                    if (states[i] == STATE_USED) {
                        consumer.accept(segment.keysMost[i], segment.keysLeast[i],
                                Double.longBitsToDouble(segment.values[i]));
                    }
                }
            } finally {
                segment.lock.unlock();
            }
        }
    }
}
