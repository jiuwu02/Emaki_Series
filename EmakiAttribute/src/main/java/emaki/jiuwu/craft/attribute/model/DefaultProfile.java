package emaki.jiuwu.craft.attribute.model;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public record DefaultProfile(String id,
                             int priority,
                             Map<String, ResourceDefinition> resources,
                             Map<String, Double> attributeDefaults,
                             String description) {

    public DefaultProfile {
        id = normalizeId(id);
        resources = resources == null ? Map.of() : Map.copyOf(resources);
        attributeDefaults = attributeDefaults == null ? Map.of() : Map.copyOf(attributeDefaults);
        description = Texts.toStringSafe(description).trim();
    }

    public static DefaultProfile fromMap(Object raw) {
        if (raw == null) {
            return null;
        }
        Map<String, ResourceDefinition> resources = new LinkedHashMap<>();
        Object resourcesRaw = ConfigNodes.get(raw, "resources");
        for (Map.Entry<String, Object> entry : ConfigNodes.entries(resourcesRaw).entrySet()) {
            ResourceDefinition definition = ResourceDefinition.fromMap(entry.getKey(), entry.getValue());
            if (definition != null) {
                resources.put(definition.id(), definition);
            }
        }
        Map<String, Double> attributes = new LinkedHashMap<>();
        Object attributesRaw = ConfigNodes.get(raw, "attributes");
        for (Map.Entry<String, Object> entry : ConfigNodes.entries(attributesRaw).entrySet()) {
            attributes.put(entry.getKey().toLowerCase(Locale.ROOT), Numbers.tryParseDouble(entry.getValue(), 0D));
        }
        return new DefaultProfile(
            ConfigNodes.string(raw, "id", "global"),
            Numbers.tryParseInt(ConfigNodes.get(raw, "priority"), 0),
            resources,
            attributes,
            ConfigNodes.string(raw, "description", null)
        );
    }

    private static String normalizeId(String value) {
        return Texts.toStringSafe(value).trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
