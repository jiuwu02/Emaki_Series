package emaki.jiuwu.craft.cooking.service;

import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.service.StationStateStore.StationStorageBackend;
import emaki.jiuwu.craft.cooking.service.StationStateStore.StoredState;




final class StationStateArbiter {

    private final StationStateVersionLedger versionLedger = new StationStateVersionLedger();

    long beginSave(StationCoordinates coordinates) {
        return versionLedger.beginSave(coordinates);
    }

    long beginDelete(StationCoordinates coordinates) {
        return versionLedger.beginDelete(coordinates);
    }

    boolean isCurrentSave(StationCoordinates coordinates, long version) {
        return versionLedger.isCurrentSave(coordinates, version);
    }

    boolean isCurrentDelete(StationCoordinates coordinates, long version) {
        return versionLedger.isCurrentDelete(coordinates, version);
    }

    boolean isTombstoned(StationCoordinates coordinates) {
        return versionLedger.isTombstoned(coordinates);
    }

    long currentVersion(StationCoordinates coordinates) {
        return versionLedger.currentVersion(coordinates);
    }

    StationStateVersionLedger.Mutation currentMutation(StationCoordinates coordinates) {
        return versionLedger.currentMutation(coordinates);
    }

    boolean abandonMutation(StationCoordinates coordinates, long version, StationStateVersionLedger.Mutation previous) {
        return versionLedger.abandonMutation(coordinates, version, previous);
    }

    void observe(StationCoordinates coordinates, long version, boolean tombstone) {
        versionLedger.observe(coordinates, version, tombstone);
    }

    StoredState latestState(StoredState... candidates) {
        StoredState selected = null;
        if (candidates == null) {
            return null;
        }
        for (StoredState candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            if (selected == null
                    || candidate.version() > selected.version()
                    || (candidate.version() == selected.version() && candidate.tombstone() && !selected.tombstone())
                    || (candidate.version() == selected.version()
                    && candidate.tombstone() == selected.tombstone()
                    && candidate.backend() == StationStorageBackend.BLOCK_PDC
                    && selected.backend() != StationStorageBackend.BLOCK_PDC)) {
                selected = candidate;
            }
        }
        return selected;
    }

    boolean inMemoryWins(StationStateVersionLedger.Mutation inMemory, StoredState persisted) {
        if (inMemory == null) {
            return false;
        }
        boolean ahead = persisted == null
                || inMemory.version() > persisted.version()
                || (inMemory.version() == persisted.version() && inMemory.tombstone());
        if (!ahead) {
            return false;
        }
        return inMemory.tombstone() || persisted == null || inMemory.version() > persisted.version();
    }

    boolean canArchiveYaml(StationCoordinates coordinates, long mutationVersion, boolean requireCurrentSave) {
        if (coordinates == null) {
            return false;
        }
        if (requireCurrentSave) {
            return versionLedger.isCurrentSave(coordinates, mutationVersion);
        }
        return !versionLedger.isTombstoned(coordinates) && versionLedger.currentVersion(coordinates) == mutationVersion;
    }
}
