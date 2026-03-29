package emaki.jiuwu.craft.corelib.assembly;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EmakiItemLayerSnapshotTest {

    @Test
    void codecRoundTripPreservesStructuredLayerData() {
        EmakiItemLayerSnapshot snapshot = new EmakiItemLayerSnapshot(
            "forge",
            1,
            Map.of(
                "recipe_id", "flame_sword",
                "materials", List.of(
                    Map.of("material_id", "iron_essence", "amount", 3),
                    Map.of("material_id", "flame_crystal", "amount", 1)
                )
            ),
            List.of(
                new EmakiStatContribution("physical_damage", 14D, "iron_essence#0", 0),
                new EmakiStatContribution("fire_damage", 10D, "flame_crystal#1", 1)
            ),
            List.of(
                new EmakiPresentationEntry("name_append", "", "<red> [烈焰]", 0, "flame_crystal"),
                new EmakiPresentationEntry("stat_line", "锻造属性:", "<gray>物理伤害: {physical_damage}", 1, "physical_damage")
            )
        );

        String encoded = EmakiItemLayerSnapshot.CODEC.encode(snapshot);
        EmakiItemLayerSnapshot decoded = EmakiItemLayerSnapshot.CODEC.decode(encoded);

        assertNotNull(decoded);
        assertEquals("forge", decoded.namespaceId());
        assertEquals(1, decoded.schemaVersion());
        assertEquals("flame_sword", decoded.audit().get("recipe_id"));
        assertEquals(2, decoded.stats().size());
        assertEquals("physical_damage", decoded.stats().get(0).statId());
        assertEquals(14D, decoded.stats().get(0).amount());
        assertEquals(2, decoded.presentation().size());
        assertEquals("name_append", decoded.presentation().get(0).type());
        assertEquals("锻造属性:", decoded.presentation().get(1).anchor());
    }
}
