package emaki.jiuwu.craft.attribute.loader;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.model.AttributeDefinition;
import emaki.jiuwu.craft.attribute.model.AttributeSemanticDefinition;
import emaki.jiuwu.craft.attribute.model.AttributeTargetType;
import emaki.jiuwu.craft.attribute.model.AttributeValueKind;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;

public final class AttributeBalanceRegistry {

    private final EmakiAttributePlugin plugin;
    private final AttributeRegistry attributeRegistry;
    private final Map<String, AttributeSemanticDefinition> semantics = new LinkedHashMap<>();
    private final Map<String, Double> weights = new LinkedHashMap<>();
    private YamlConfiguration configuration = new YamlConfiguration();

    public AttributeBalanceRegistry(EmakiAttributePlugin plugin, AttributeRegistry attributeRegistry) {
        this.plugin = plugin;
        this.attributeRegistry = attributeRegistry;
    }

    public int load() {
        semantics.clear();
        weights.clear();
        File file = plugin.dataPath("defaults", "attribute_balance.yml").toFile();
        try {
            YamlFiles.syncVersionedResource(plugin, file, "defaults/attribute_balance.yml", "schema_version");
            if (!file.exists()) {
                YamlFiles.copyResourceIfMissing(plugin, "defaults/attribute_balance.yml", file);
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to write default attribute balance file: " + exception.getMessage());
        }
        configuration = YamlFiles.load(file);
        parseSemantics(configuration.get("attributes"));
        parseWeights(configuration.get("weights"));
        applyFallbacks();
        return semantics.size();
    }

    public AttributeSemanticDefinition semantic(String id) {
        if (Texts.isBlank(id)) {
            return null;
        }
        return semantics.get(normalizeId(id));
    }

    public double weightOf(String id, double fallback) {
        if (Texts.isBlank(id)) {
            return fallback;
        }
        Double weight = weights.get(normalizeId(id));
        return weight == null ? fallback : weight;
    }

    public Map<String, AttributeSemanticDefinition> semantics() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(semantics));
    }

    public Map<String, Double> weights() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(weights));
    }

    public YamlConfiguration configuration() {
        return configuration;
    }

    private void parseSemantics(Object raw) {
        for (Map.Entry<String, Object> entry : ConfigNodes.entries(raw).entrySet()) {
            String id = normalizeId(entry.getKey());
            if (Texts.isBlank(id)) {
                continue;
            }
            Object semanticRaw = entry.getValue();
            String group = ConfigNodes.string(semanticRaw, "group", inferGroup(id));
            String role = ConfigNodes.string(semanticRaw, "role", inferRole(id));
            String summary = firstNonBlank(
                ConfigNodes.string(semanticRaw, "summary", null),
                ConfigNodes.string(semanticRaw, "description", null)
            );
            double weight = Numbers.tryParseDouble(ConfigNodes.get(semanticRaw, "weight"), 1D);
            AttributeSemanticDefinition definition = new AttributeSemanticDefinition(id, group, role, summary, weight);
            semantics.put(id, definition);
            weights.putIfAbsent(id, definition.weight());
        }
    }

    private void parseWeights(Object raw) {
        for (Map.Entry<String, Object> entry : ConfigNodes.entries(raw).entrySet()) {
            String id = normalizeId(entry.getKey());
            if (Texts.isBlank(id)) {
                continue;
            }
            Double weight = Numbers.tryParseDouble(entry.getValue(), null);
            if (weight != null) {
                weights.put(id, weight);
            }
        }
    }

    private void applyFallbacks() {
        if (attributeRegistry == null || attributeRegistry.all().isEmpty()) {
            return;
        }
        for (AttributeDefinition definition : attributeRegistry.all().values()) {
            if (definition == null) {
                continue;
            }
            String id = definition.id();
            AttributeSemanticDefinition semantic = semantics.get(id);
            double weight = weights.containsKey(id)
                ? weights.get(id)
                : (semantic != null ? semantic.weight() : definition.attributePower());
            weights.put(id, weight);
            if (semantic == null) {
                semantics.put(
                    id,
                    new AttributeSemanticDefinition(
                        id,
                        inferGroup(definition),
                        inferRole(definition),
                        firstNonBlank(definition.description(), definition.displayName()),
                        weight
                    )
                );
            } else if (Double.compare(semantic.weight(), weight) != 0) {
                semantics.put(id, new AttributeSemanticDefinition(id, semantic.group(), semantic.role(), semantic.summary(), weight));
            }
        }
    }

    private String inferGroup(String id) {
        AttributeDefinition definition = attributeRegistry == null ? null : attributeRegistry.resolve(id);
        if (definition == null) {
            return "utility.generic";
        }
        return inferGroup(definition);
    }

    private String inferGroup(AttributeDefinition definition) {
        if (definition == null) {
            return "utility.generic";
        }
        String targetId = normalizeId(definition.targetId());
        String category = switch (definition.targetType()) {
            case DAMAGE -> "offense";
            case RESOURCE -> "resource";
            case SKILL -> "skill";
            case GENERIC -> "utility";
        };
        String domain = Texts.isBlank(targetId) ? definition.valueKind().name().toLowerCase(Locale.ROOT) : targetId;
        return category + "." + domain;
    }

    private String inferRole(String id) {
        AttributeDefinition definition = attributeRegistry == null ? null : attributeRegistry.resolve(id);
        if (definition == null) {
            return "utility";
        }
        return inferRole(definition);
    }

    private String inferRole(AttributeDefinition definition) {
        if (definition == null) {
            return "utility";
        }
        AttributeValueKind kind = definition.valueKind();
        return switch (kind) {
            case FLAT -> "flat";
            case PERCENT -> "percent";
            case CHANCE -> "chance";
            case REGEN -> "regen";
            case RESOURCE -> "resource";
            case SKILL -> "skill";
            case DERIVED -> "derived";
        };
    }

    private static String firstNonBlank(String left, String right) {
        return Texts.isBlank(left) ? right : left;
    }

    private static String normalizeId(String value) {
        return Texts.toStringSafe(value).trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
