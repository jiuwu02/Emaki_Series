package emaki.jiuwu.craft.corelib.collection;

import java.util.concurrent.locks.ReentrantLock;

abstract class AbstractUuidKeyedMap {

    static final byte STATE_EMPTY = 0;
    static final byte STATE_USED = 1;
    static final byte STATE_REMOVED = 2;

    static final int DEFAULT_SEGMENTS = 4;
    static final int INITIAL_CAPACITY = 8;
    static final float LOAD_FACTOR = 0.6F;

    static final class Segment {
        final ReentrantLock lock = new ReentrantLock();
        long[] keysMost = new long[INITIAL_CAPACITY];
        long[] keysLeast = new long[INITIAL_CAPACITY];
        long[] values = new long[INITIAL_CAPACITY];
        byte[] states = new byte[INITIAL_CAPACITY];
        int size;
        int removed;
        int resizeAt = resizeThreshold(INITIAL_CAPACITY);
    }

    final Segment[] segments;
    private final int segmentMask;

    AbstractUuidKeyedMap(int segmentCount) {
        int count = 1;
        while (count < segmentCount) {
            count <<= 1;
        }
        this.segmentMask = count - 1;
        this.segments = new Segment[count];
        for (int i = 0; i < count; i++) {
            this.segments[i] = new Segment();
        }
    }

    final Segment segmentFor(long most, long least) {
        return segments[spread(most ^ least) & segmentMask];
    }

    final int totalSize() {
        int total = 0;
        for (Segment segment : segments) {
            segment.lock.lock();
            try {
                total += segment.size;
            } finally {
                segment.lock.unlock();
            }
        }
        return total;
    }

    final boolean empty() {
        for (Segment segment : segments) {
            if (segment.size != 0) {
                return false;
            }
        }
        return true;
    }

    final void reset() {
        for (Segment segment : segments) {
            segment.lock.lock();
            try {
                segment.keysMost = new long[INITIAL_CAPACITY];
                segment.keysLeast = new long[INITIAL_CAPACITY];
                segment.values = new long[INITIAL_CAPACITY];
                segment.states = new byte[INITIAL_CAPACITY];
                segment.size = 0;
                segment.removed = 0;
                segment.resizeAt = resizeThreshold(INITIAL_CAPACITY);
            } finally {
                segment.lock.unlock();
            }
        }
    }

    static int resizeThreshold(int capacity) {
        return (int) (capacity * LOAD_FACTOR);
    }

    static int spread(long value) {
        long hash = value;
        hash ^= hash >>> 33;
        hash *= 0xFF51AFD7ED558CCDL;
        hash ^= hash >>> 33;
        hash *= 0xC4CEB9FE1A85EC53L;
        hash ^= hash >>> 33;
        return (int) hash;
    }

    static int locate(Segment segment, long most, long least) {
        int mask = segment.states.length - 1;
        int index = spread(most ^ least) & mask;
        for (;;) {
            byte state = segment.states[index];
            if (state == STATE_EMPTY) {
                return -1;
            }
            if (state == STATE_USED
                    && segment.keysMost[index] == most
                    && segment.keysLeast[index] == least) {
                return index;
            }
            index = (index + 1) & mask;
        }
    }

    static int slotFor(Segment segment, long most, long least) {
        if (segment.size + segment.removed + 1 > segment.resizeAt) {
            rehash(segment);
        }
        int mask = segment.states.length - 1;
        int index = spread(most ^ least) & mask;
        int free = -1;
        for (;;) {
            byte state = segment.states[index];
            if (state == STATE_EMPTY) {
                break;
            }
            if (state == STATE_REMOVED) {
                if (free < 0) {
                    free = index;
                }
            } else if (segment.keysMost[index] == most && segment.keysLeast[index] == least) {
                return index;
            }
            index = (index + 1) & mask;
        }
        int slot = free < 0 ? index : free;
        if (segment.states[slot] == STATE_REMOVED) {
            segment.removed--;
        }
        segment.states[slot] = STATE_USED;
        segment.keysMost[slot] = most;
        segment.keysLeast[slot] = least;
        segment.values[slot] = 0L;
        segment.size++;
        return slot;
    }

    static long rawOrDefault(Segment segment, long most, long least, long absent) {
        int index = locate(segment, most, least);
        return index < 0 ? absent : segment.values[index];
    }

    static long removeRaw(Segment segment, long most, long least, long absent) {
        int index = locate(segment, most, least);
        if (index < 0) {
            return absent;
        }
        long previous = segment.values[index];
        segment.states[index] = STATE_REMOVED;
        segment.values[index] = 0L;
        segment.size--;
        segment.removed++;
        return previous;
    }

    static void rehash(Segment segment) {
        long[] oldMost = segment.keysMost;
        long[] oldLeast = segment.keysLeast;
        long[] oldValues = segment.values;
        byte[] oldStates = segment.states;
        int capacity = oldStates.length;
        while (segment.size > resizeThreshold(capacity)) {
            capacity <<= 1;
        }
        long[] newMost = new long[capacity];
        long[] newLeast = new long[capacity];
        long[] newValues = new long[capacity];
        byte[] newStates = new byte[capacity];
        int mask = capacity - 1;
        for (int i = 0; i < oldStates.length; i++) {
            if (oldStates[i] != STATE_USED) {
                continue;
            }
            long most = oldMost[i];
            long least = oldLeast[i];
            int index = spread(most ^ least) & mask;
            while (newStates[index] != STATE_EMPTY) {
                index = (index + 1) & mask;
            }
            newStates[index] = STATE_USED;
            newMost[index] = most;
            newLeast[index] = least;
            newValues[index] = oldValues[i];
        }
        segment.keysMost = newMost;
        segment.keysLeast = newLeast;
        segment.values = newValues;
        segment.states = newStates;
        segment.removed = 0;
        segment.resizeAt = resizeThreshold(capacity);
    }
}
