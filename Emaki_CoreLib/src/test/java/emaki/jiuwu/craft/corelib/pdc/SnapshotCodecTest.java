package emaki.jiuwu.craft.corelib.pdc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SnapshotCodecTest {

    private record SampleSnapshot(String id, double value, List<String> tags) {
        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", id);
            map.put("value", value);
            map.put("tags", tags);
            return map;
        }

        static SampleSnapshot fromMap(Map<String, Object> map) {
            return new SampleSnapshot(
                ConfigNodes.string(map, "id", ""),
                Numbers.tryParseDouble(ConfigNodes.get(map, "value"), 0D),
                ConfigNodes.asObjectList(ConfigNodes.get(map, "tags")).stream().map(String::valueOf).toList()
            );
        }
    }

    @Test
    void yamlCodecRoundTripsNestedData() {
        SnapshotCodec<SampleSnapshot> codec = SnapshotCodec.yaml(SampleSnapshot::toMap, SampleSnapshot::fromMap);
        SampleSnapshot original = new SampleSnapshot("demo", 12.5, List.of("one", "two"));

        String payload = codec.encode(original);
        assertNotNull(payload);

        SampleSnapshot restored = codec.decode(payload);
        assertNotNull(restored);
        assertEquals(original.id(), restored.id());
        assertEquals(original.value(), restored.value(), 0.0001D);
        assertEquals(original.tags(), restored.tags());
    }
}
