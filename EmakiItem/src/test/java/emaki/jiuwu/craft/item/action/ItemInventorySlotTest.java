package emaki.jiuwu.craft.item.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ItemInventorySlotTest {

    @Test
    void parsesNamedSlotAliasesCaseInsensitively() {
        assertSlot("mainhand", "MAIN_HAND");
        assertSlot("mainhand", "selected");
        assertSlot("offhand", "off_hand");
        assertSlot("helmet", "armor_head");
        assertSlot("chestplate", "chest");
        assertSlot("leggings", "armor_legs");
        assertSlot("boots", "feet");
    }

    @Test
    void parsesFullPlayerInventoryIndexes() {
        assertSlot("slot_0", "0");
        assertSlot("slot_35", "inventory_35");
        assertSlot("slot_36", "slot_36");
        assertSlot("slot_40", "40");
    }

    @Test
    void restrictsHotbarAndInventoryBounds() {
        assertSlot("slot_8", "hotbar_8");
        assertNull(ItemInventorySlot.parse("hotbar_9"));
        assertNull(ItemInventorySlot.parse("slot_41"));
        assertNull(ItemInventorySlot.parse("-1"));
        assertNull(ItemInventorySlot.parse("unknown"));
        assertNull(ItemInventorySlot.parse(" "));
    }

    private static void assertSlot(String expectedId, String raw) {
        ItemInventorySlot slot = ItemInventorySlot.parse(raw);
        assertNotNull(slot);
        assertEquals(expectedId, slot.id());
    }
}
