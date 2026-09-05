package emaki.jiuwu.craft.gem.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;

class GemDefinitionParserTest {

    @Test
    void usesLegacyTopLevelItemSourcesAsConstructionFallback() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", "legacy_gem");
        values.put("item_sources", List.of("minecraft-redstone"));

        GemDefinition definition = GemDefinitionParser.parse(new MapYamlSection(values));

        assertNotNull(definition);
        ItemSourceRef expected = ItemSourceUtil.parse("minecraft-redstone");
        assertEquals(expected, definition.baseItemSource());
        assertEquals(List.of(expected), definition.recognition().sources());
    }

    @Test
    void canonicalConstructionSourceTakesPrecedenceOverLegacyRecognitionSources() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", "canonical_gem");
        values.put("base_item_source", "minecraft-diamond");
        values.put("item_sources", List.of("minecraft-redstone"));

        GemDefinition definition = GemDefinitionParser.parse(new MapYamlSection(values));

        assertNotNull(definition);
        assertEquals(ItemSourceUtil.parse("minecraft-diamond"), definition.baseItemSource());
        assertEquals(List.of(ItemSourceUtil.parse("minecraft-redstone")), definition.recognition().sources());
    }
}
