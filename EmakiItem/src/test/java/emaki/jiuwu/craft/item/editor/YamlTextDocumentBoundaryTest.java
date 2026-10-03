package emaki.jiuwu.craft.item.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class YamlTextDocumentBoundaryTest {

    @Test
    void emptyDocumentIsUsable() {
        YamlTextDocument document = YamlTextDocument.parse("");
        document.set("value", "id");
        assertEquals("value", document.value("id"));
    }

    @Test
    void commentOnlyDocumentKeepsCommentsWhileCreatingNodes() {
        YamlTextDocument document = YamlTextDocument.parse("# only a comment\n");
        document.set("value", "id");
        assertTrue(document.text().contains("# only a comment"));
        assertEquals("value", document.value("id"));
    }

    @Test
    void outOfRangeListItemIsRejected() {
        YamlTextDocument document = YamlTextDocument.parse("items:\n  - a\n  - b\n");
        assertThrows(IndexOutOfBoundsException.class, () -> document.setListItem("x", 5, "items"));
        assertThrows(IndexOutOfBoundsException.class, () -> document.removeListItem(-1, "items"));
    }

    @Test
    void removingMissingNodeIsNoOp() {
        YamlTextDocument document = YamlTextDocument.parse("id: keep\n");
        document.remove("nope");
        assertEquals("keep", document.value("id"));
    }

    @Test
    void deepIndexPathReadsAndWritesInsideListItems() {
        YamlTextDocument document = YamlTextDocument.parse(
                "effects:\n  - type: variables\n    variables:\n      physical_attack: 12\n");
        assertEquals(12, document.value("effects", "0", "variables", "physical_attack"));
        document.set(99, "effects", "0", "variables", "physical_attack");
        assertEquals(99, document.value("effects", "0", "variables", "physical_attack"));
    }

    @Test
    void removingMapKeyKeepsSiblings() {
        YamlTextDocument document = YamlTextDocument.parse("item:\n  components:\n    a: 1\n    b: 2\n");
        document.remove("item", "components", "a");
        assertNull(document.value("item", "components", "a"));
        assertEquals(2, document.value("item", "components", "b"));
    }

    @Test
    void removingDeepListItemDropsOnlyThatEntry() {
        YamlTextDocument document = YamlTextDocument.parse(
                "effects:\n  - type: a\n  - type: b\n  - type: c\n");
        document.remove("effects", "1");
        assertEquals(2, document.sequenceSize("effects"));
        assertEquals("a", ((Map<?, ?>) document.sequence("effects").get(0)).get("type"));
        assertEquals("c", ((Map<?, ?>) document.sequence("effects").get(1)).get("type"));
    }

    @Test
    void repeatedEditingNeverCorruptsTheDocument() {
        YamlTextDocument document = YamlTextDocument.parse(
                "id: start\n\n# trailing comment\nitem:\n  source: minecraft-stone\n\nactions:\n  - orig\n");
        for (int round = 0; round < 50; round++) {
            document.set("v" + round, "id");
            document.set(round, "item", "components", "minecraft:max_stack_size");
            document.setListItem("line" + round, 0, "actions");
        }
        assertEquals("v49", document.value("id"));
        assertEquals(1, document.sequenceSize("actions"));
        assertTrue(document.text().contains("# trailing comment"));
        assertFalse(document.text().contains("v48"));
    }

    @Test
    void listTargetSupportsMapStyleEntries() {
        YamlTextDocument document = YamlTextDocument.parse("enchantments:\n  minecraft:sharpness: 5\n");
        assertEquals(5, document.value("enchantments", "minecraft:sharpness"));
        document.set(7, "enchantments", "minecraft:sharpness");
        assertEquals(7, document.value("enchantments", "minecraft:sharpness"));
    }

    @Test
    void sequenceSizeIgnoresMappingTargets() {
        YamlTextDocument document = YamlTextDocument.parse("enchantments:\n  minecraft:sharpness: 5\n");
        assertEquals(0, document.sequenceSize("enchantments"));
        assertTrue(document.sequence("enchantments").isEmpty());
    }
}
