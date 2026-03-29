package emaki.jiuwu.craft.corelib.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ItemSourceUtilTest {

    @Test
    void parseSupportsMmoItemsShortHands() {
        ItemSource shorthand = ItemSourceUtil.parseShorthand("mi-sword:cutlass");
        ItemSource longhand = ItemSourceUtil.parseShorthand("mmoitems-armor:ember_plate");

        assertNotNull(shorthand);
        assertEquals(ItemSourceType.MMOITEMS, shorthand.getType());
        assertEquals("sword:cutlass", shorthand.getIdentifier());

        assertNotNull(longhand);
        assertEquals(ItemSourceType.MMOITEMS, longhand.getType());
        assertEquals("armor:ember_plate", longhand.getIdentifier());
    }

    @Test
    void toShorthandUsesCanonicalMmoItemsPrefix() {
        String shorthand = ItemSourceUtil.toShorthand(new ItemSource(ItemSourceType.MMOITEMS, "sword:cutlass"));

        assertEquals("mmoitems-sword:cutlass", shorthand);
    }
}
