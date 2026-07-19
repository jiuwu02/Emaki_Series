package emaki.jiuwu.craft.attribute.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.attribute.model.PdcAttributePayload;

class AttributeSnapshotCollectorTest {

    @Test
    void rejectsLoreAndPdcTogetherWhenItemSlotDoesNotMatch() {
        Map<String, Double> loreValues = Map.of(
                "health", 12D,
                "attack_damage", 7D,
                "defense", 5D
        );
        Map<String, Double> pdcValues = Map.of(
                "health", 12D,
                "attack_damage", 7D,
                "defense", 5D
        );
        PdcAttributeService.ItemSlotGate slotGate = PdcAttributeService.resolveItemSlotGate(
                List.of(PdcAttributePayload.of(
                        "emakiitem",
                        pdcValues,
                        Map.of("active_slot", "chestplate")
                )),
                "mainhand"
        );

        Map<String, Double> resolved = AttributeSnapshotCollector.resolveEquipmentItemValues(
                loreValues,
                pdcValues,
                true,
                true,
                false,
                slotGate.matched()
        );

        assertEquals(List.of("chestplate"), slotGate.declaredSlots());
        assertFalse(slotGate.matched());
        assertTrue(resolved.isEmpty());
        assertEquals(0D, resolved.getOrDefault("health", 0D));
        assertEquals(0D, resolved.getOrDefault("attack_damage", 0D));
        assertEquals(0D, resolved.getOrDefault("defense", 0D));
    }

    @Test
    void preservesLooseLoreAndPdcAdditionWhenItemSlotMatches() {
        Map<String, Double> loreValues = Map.of(
                "health", 12D,
                "attack_damage", 7D,
                "defense", 5D
        );
        Map<String, Double> pdcValues = Map.of(
                "health", 12D,
                "attack_damage", 7D,
                "defense", 5D
        );
        PdcAttributeService.ItemSlotGate slotGate = PdcAttributeService.resolveItemSlotGate(
                List.of(PdcAttributePayload.of(
                        "emakiitem",
                        pdcValues,
                        Map.of("active_slot", "chestplate")
                )),
                "chest"
        );

        Map<String, Double> resolved = AttributeSnapshotCollector.resolveEquipmentItemValues(
                loreValues,
                pdcValues,
                true,
                true,
                false,
                slotGate.matched()
        );

        assertEquals(List.of("chestplate"), slotGate.declaredSlots());
        assertTrue(slotGate.matched());
        assertEquals(24D, resolved.get("health"));
        assertEquals(14D, resolved.get("attack_damage"));
        assertEquals(10D, resolved.get("defense"));
    }
}
