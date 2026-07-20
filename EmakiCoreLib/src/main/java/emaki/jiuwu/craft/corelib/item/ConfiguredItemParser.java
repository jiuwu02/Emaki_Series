package emaki.jiuwu.craft.corelib.item;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.item.ItemComponentPatch;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;


public final class ConfiguredItemParser {

    public ConfiguredItemDefinition parse(YamlSection section) {
        return parse((Object) section);
    }

    public ConfiguredItemDefinition parse(Map<String, ?> values) {
        return parse((Object) values);
    }

    public ConfiguredItemDefinition parse(Object raw) {
        if (raw == null) {
            return new ConfiguredItemDefinition(null, 1, Map.of());
        }
        if (raw instanceof ConfiguredItemDefinition definition) {
            return definition;
        }
        if (raw instanceof String source) {
            return new ConfiguredItemDefinition(Texts.trim(source), 1, Map.of());
        }
        if (!(raw instanceof Map<?, ?>) && !(raw instanceof YamlSection)) {
            throw new IllegalArgumentException("Configured item must be a YAML section, plain map, or source string.");
        }

        String source = normalizeSource(ConfigNodes.get(raw, "source"));
        int amount = Math.max(1, Numbers.tryParseInt(ConfigNodes.get(raw, "amount"), 1));
        Map<String, ItemComponentPatch> patches = parseComponents(ConfigNodes.get(raw, "components"));
        return new ConfiguredItemDefinition(source, amount, patches);
    }

    private Map<String, ItemComponentPatch> parseComponents(Object raw) {
        if (raw == null) {
            return Map.of();
        }
        if (!(raw instanceof Map<?, ?>) && !(raw instanceof YamlSection)) {
            throw new IllegalArgumentException("Configured item components must be a YAML section or plain map.");
        }
        Map<String, ItemComponentPatch> patches = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : ConfigNodes.entries(raw).entrySet()) {
            String key = entry.getKey();
            if (key == null || key.startsWith("$")) {
                continue;
            }
            patches.put(normalizeComponentId(key), ItemComponentPatch.set(ConfigNodes.toPlainData(entry.getValue())));
        }
        applyOperations(patches, ConfigNodes.get(raw, "$unset"), ItemComponentPatch.Operation.UNSET);
        applyOperations(patches, ConfigNodes.get(raw, "$reset"), ItemComponentPatch.Operation.RESET);
        return patches;
    }

    private void applyOperations(Map<String, ItemComponentPatch> patches,
            Object raw,
            ItemComponentPatch.Operation operation) {
        List<String> componentIds = Texts.asStringList(ConfigNodes.toPlainData(raw));
        for (String componentId : componentIds) {
            if (Texts.isBlank(componentId)) {
                continue;
            }
            String normalized = normalizeComponentId(componentId);
            patches.put(normalized, operation == ItemComponentPatch.Operation.UNSET
                    ? ItemComponentPatch.unset()
                    : ItemComponentPatch.reset());
        }
    }

    private String normalizeSource(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String source) {
            return Texts.trim(source);
        }
        ItemSource parsed = ItemSourceUtil.parse(raw);
        return parsed == null ? null : ItemSourceUtil.toShorthand(parsed);
    }

    static String normalizeComponentId(String raw) {
        return new ConfiguredItemDefinition(null, 1, Map.of(raw, ItemComponentPatch.unset()))
                .components()
                .keySet()
                .iterator()
                .next();
    }
}
