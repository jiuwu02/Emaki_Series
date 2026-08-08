package emaki.jiuwu.craft.station.dismantle;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Simple id-keyed registry for loaded dismantle stations.
 *
 * <p>A reload replaces the full index atomically. Nothing mutates in place, so any caller that
 * captured a {@link DismantleStationDefinition} reference before a reload keeps a consistent view
 * for the duration of its use.
 */
public final class DismantleStationRegistry {

    private static final DismantleStationRegistry EMPTY = new DismantleStationRegistry(Map.of());

    private final Map<String, DismantleStationDefinition> stations;

    private DismantleStationRegistry(Map<String, DismantleStationDefinition> stations) {
        this.stations = stations;
    }

    /** {@return an empty registry} */
    public static DismantleStationRegistry empty() {
        return EMPTY;
    }

    /**
     * Builds a registry from the given definitions.
     *
     * @param definitions the loaded definitions; {@code null} or empty yields an empty registry
     * @return the populated registry
     */
    public static DismantleStationRegistry build(Collection<DismantleStationDefinition> definitions) {
        if (definitions == null || definitions.isEmpty()) {
            return EMPTY;
        }
        Map<String, DismantleStationDefinition> map = new LinkedHashMap<>();
        for (DismantleStationDefinition def : definitions) {
            if (def != null) {
                map.put(def.id(), def);
            }
        }
        return new DismantleStationRegistry(Map.copyOf(map));
    }

    /**
     * Looks up one dismantle station by id.
     *
     * @param stationId the station id; matched case-insensitively
     * @return the station, or {@code null} when unknown
     */
    public DismantleStationDefinition find(String stationId) {
        return stationId == null ? null : stations.get(normalize(stationId));
    }

    /** {@return every registered station in load order} */
    public List<DismantleStationDefinition> all() {
        return List.copyOf(stations.values());
    }

    /** {@return how many stations are loaded} */
    public int size() {
        return stations.size();
    }

    private static String normalize(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT);
    }
}
