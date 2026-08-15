package emaki.jiuwu.craft.station.dismantle;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DismantleStationRegistry {

    private static final DismantleStationRegistry EMPTY = new DismantleStationRegistry(Map.of());

    private final Map<String, DismantleStationDefinition> stations;

    private DismantleStationRegistry(Map<String, DismantleStationDefinition> stations) {
        this.stations = stations;
    }

    public static DismantleStationRegistry empty() {
        return EMPTY;
    }

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

    public DismantleStationDefinition find(String stationId) {
        return stationId == null ? null : stations.get(normalize(stationId));
    }

    public List<DismantleStationDefinition> all() {
        return List.copyOf(stations.values());
    }

    public int size() {
        return stations.size();
    }

    private static String normalize(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT);
    }
}
