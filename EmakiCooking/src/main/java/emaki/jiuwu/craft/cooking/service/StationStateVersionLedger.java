package emaki.jiuwu.craft.cooking.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import emaki.jiuwu.craft.cooking.model.StationCoordinates;

final class StationStateVersionLedger {

    record Mutation(long version, boolean tombstone) {
    }

    private final AtomicLong sequence = new AtomicLong();
    private final ConcurrentMap<StationCoordinates, Mutation> mutations = new ConcurrentHashMap<>();

    long beginSave(StationCoordinates coordinates) {
        return begin(coordinates, false);
    }

    long beginDelete(StationCoordinates coordinates) {
        return begin(coordinates, true);
    }

    boolean isCurrentSave(StationCoordinates coordinates, long version) {
        Mutation mutation = mutations.get(coordinates);
        return mutation != null && mutation.version() == version && !mutation.tombstone();
    }

    boolean isCurrentDelete(StationCoordinates coordinates, long version) {
        Mutation mutation = mutations.get(coordinates);
        return mutation != null && mutation.version() == version && mutation.tombstone();
    }

    boolean isTombstoned(StationCoordinates coordinates) {
        Mutation mutation = mutations.get(coordinates);
        return mutation != null && mutation.tombstone();
    }

    long currentVersion(StationCoordinates coordinates) {
        Mutation mutation = mutations.get(coordinates);
        return mutation == null ? 0L : mutation.version();
    }

    Mutation currentMutation(StationCoordinates coordinates) {
        return coordinates == null ? null : mutations.get(coordinates);
    }

    boolean abandonMutation(StationCoordinates coordinates, long version, Mutation previous) {
        if (coordinates == null || version < 0L) {
            return false;
        }
        Mutation current = mutations.get(coordinates);
        if (current == null || current.version() != version) {
            return false;
        }
        if (previous == null) {
            return mutations.remove(coordinates, current);
        }
        return mutations.replace(coordinates, current, previous);
    }

    void observe(StationCoordinates coordinates, long version, boolean tombstone) {
        if (coordinates == null || version < 0L) {
            return;
        }
        sequence.accumulateAndGet(version, Math::max);
        mutations.compute(coordinates, (_, current) -> {
            if (current == null
                    || version > current.version()
                    || (version == current.version() && tombstone && !current.tombstone())) {
                return new Mutation(version, tombstone);
            }
            return current;
        });
    }

    private long begin(StationCoordinates coordinates, boolean tombstone) {
        if (coordinates == null) {
            return -1L;
        }
        long version = sequence.updateAndGet(current -> Math.max(current + 1L, System.currentTimeMillis()));
        Mutation mutation = new Mutation(version, tombstone);
        mutations.compute(coordinates, (_, current) -> current == null || version > current.version() ? mutation : current);
        return version;
    }
}
