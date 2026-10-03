package emaki.jiuwu.craft.item.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class YamlTextDocumentTest {

    private static final String SAMPLE = """
            # 物品定义示例
            id: "example_item"

            # 基础物品
            item:
              source: "minecraft-diamond_sword"
              components:
                # 显示名
                custom_name: "<gold>示例</gold>"
                minecraft:max_stack_size: 1

            equip_slot: "main_hand"

            # 效果
            effects:
              - type: "variables"
                variables:
                  physical_attack: 12
              - type: "ea_attribute"
                ea_attributes:
                  physical_attack: 12.0
            """;

    @Test
    void untouchedContentIsPreservedByteForByte() {
        YamlTextDocument document = YamlTextDocument.parse(SAMPLE);
        assertEquals(SAMPLE, document.text());
    }

    @Test
    void setReplacesOnlyTheTargetNodeAndKeepsSiblingComments() {
        YamlTextDocument document = YamlTextDocument.parse(SAMPLE);
        document.set("main_off", "equip_slot");

        assertTrue(document.text().contains("# 物品定义示例"));
        assertTrue(document.text().contains("# 基础物品"));
        assertTrue(document.text().contains("# 显示名"));
        assertTrue(document.text().contains("# 效果"));
        assertTrue(document.text().contains("source: \"minecraft-diamond_sword\""));
        assertTrue(document.text().contains("custom_name: \"<gold>示例</gold>\""));
        assertEquals("main_off", document.value("equip_slot"));
        assertFalse(document.text().contains("main_hand"));
    }

    @Test
    void setSupportsColonBearingComponentKeys() {
        YamlTextDocument document = YamlTextDocument.parse(SAMPLE);
        document.set(2, "item", "components", "minecraft:max_stack_size");

        assertEquals(2, document.value("item", "components", "minecraft:max_stack_size"));
        assertTrue(document.text().contains("minecraft:max_stack_size: 2"));
    }

    @Test
    void setCreatesMissingNestedPathWithoutTouchingSiblings() {
        YamlTextDocument document = YamlTextDocument.parse(SAMPLE);
        document.set(true, "update", "enabled");

        assertEquals(true, document.value("update", "enabled"));
        assertTrue(document.text().contains("source: \"minecraft-diamond_sword\""));
        assertTrue(document.text().contains("# 效果"));
    }

    @Test
    void setAppendsNewTopLevelNodeWhenNothingExists() {
        YamlTextDocument document = YamlTextDocument.parse(SAMPLE);
        document.set("blade", "set", "piece");

        assertEquals("blade", document.value("set", "piece"));
        assertTrue(document.text().contains("# 物品定义示例"));
    }

    @Test
    void setListItemReplacesSingleItemOnly() {
        YamlTextDocument document = YamlTextDocument.parse(SAMPLE);
        Map<String, Object> replacement = new LinkedHashMap<>();
        replacement.put("type", "es_skill");
        document.setListItem(replacement, 1, "effects");

        assertEquals(2, document.sequenceSize("effects"));
        assertEquals("variables", ((Map<?, ?>) document.sequence("effects").get(0)).get("type"));
        assertEquals("es_skill", ((Map<?, ?>) document.sequence("effects").get(1)).get("type"));
    }

    @Test
    void appendAndRemoveListItemKeepRemainingItems() {
        YamlTextDocument document = YamlTextDocument.parse(SAMPLE);
        document.appendListItem(Map.of("type", "accessory_slot"), "effects");
        assertEquals(3, document.sequenceSize("effects"));

        document.removeListItem(0, "effects");
        assertEquals(2, document.sequenceSize("effects"));
        assertEquals("ea_attribute", ((Map<?, ?>) document.sequence("effects").get(0)).get("type"));
    }

    @Test
    void removingLastListItemEmptiesTheSequence() {
        YamlTextDocument document = YamlTextDocument.parse(SAMPLE);
        document.removeListItem(1, "effects");
        document.removeListItem(0, "effects");

        assertEquals(0, document.sequenceSize("effects"));
        assertTrue(document.value("effects") instanceof List<?>);
    }

    @Test
    void moveListItemReordersWithoutLosingEntries() {
        YamlTextDocument document = YamlTextDocument.parse(SAMPLE);
        document.moveListItem(1, 0, "effects");

        assertEquals("ea_attribute", ((Map<?, ?>) document.sequence("effects").get(0)).get("type"));
        assertEquals("variables", ((Map<?, ?>) document.sequence("effects").get(1)).get("type"));
    }

    @Test
    void removeDeletesOnlyTheTargetNode() {
        YamlTextDocument document = YamlTextDocument.parse(SAMPLE);
        document.remove("equip_slot");

        assertFalse(document.has("equip_slot"));
        assertTrue(document.text().contains("# 基础物品"));
        assertTrue(document.text().contains("id: \"example_item\""));
    }

    @Test
    void commentsSurviveRepeatedEdits() {
        YamlTextDocument document = YamlTextDocument.parse(SAMPLE);
        document.set("a", "id");
        document.set(true, "update", "enabled");
        document.set(2, "item", "components", "minecraft:max_stack_size");
        document.appendListItem(Map.of("type", "lore_action"), "effects");

        String text = document.text();
        assertTrue(text.contains("# 物品定义示例"));
        assertTrue(text.contains("# 基础物品"));
        assertTrue(text.contains("# 显示名"));
        assertTrue(text.contains("# 效果"));
    }
}
