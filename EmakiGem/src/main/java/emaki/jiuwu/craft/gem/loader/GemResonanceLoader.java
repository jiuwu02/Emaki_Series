package emaki.jiuwu.craft.gem.loader;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.model.GemResonanceDefinition;
import emaki.jiuwu.craft.gem.model.ResonanceChain;
import emaki.jiuwu.craft.gem.model.ResonanceEffects;
import emaki.jiuwu.craft.gem.model.ResonancePatternEntry;

public final class GemResonanceLoader extends YamlDirectoryLoader<GemResonanceDefinition> {

    public GemResonanceLoader(EmakiGemPlugin plugin) {
        super(plugin);
    }

    @Override
    protected String directoryName() {
        return "resonances";
    }

    @Override
    protected String typeName() {
        return localized("loader.type.resonance");
    }

    @Override
    protected GemResonanceDefinition parse(File file, YamlSection configuration) {
        if (configuration == null) {
            issue("loader.invalid_config", Map.of("type", typeName(), "file", file == null ? "-" : file.getName()));
            return null;
        }
        String id = Texts.lower(configuration.getString("id"));
        if (Texts.isBlank(id)) {
            onBlankId(file);
            return null;
        }
        String displayName = configuration.getString("display_name", id);
        int priority = Numbers.tryParseInt(configuration.get("priority"), 0);
        String exclusiveGroup = configuration.getString("exclusive_group", "");
        ResonanceChain chain = parseChain(configuration.getSection("chain"));
        if (chain == null || chain.pattern().isEmpty()) {
            issue("loader.invalid_config", Map.of("type", typeName(), "file", file.getName()));
            return null;
        }
        ResonanceEffects effects = parseEffects(configuration.getSection("effects"));
        return new GemResonanceDefinition(id, displayName, priority, exclusiveGroup, chain, effects);
    }

    @Override
    protected String idOf(GemResonanceDefinition value) {
        return value.id();
    }

    private ResonanceChain parseChain(YamlSection section) {
        if (section == null) {
            return null;
        }
        String mode = section.getString("mode", "unordered");
        List<ResonancePatternEntry> pattern = new ArrayList<>();
        for (Map<?, ?> entryMap : section.getMapList("pattern")) {
            ResonancePatternEntry entry = parsePatternEntry(entryMap);
            if (entry != null) {
                pattern.add(entry);
            }
        }
        return new ResonanceChain(mode, pattern);
    }

    private ResonancePatternEntry parsePatternEntry(Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        String id = ConfigNodes.string(map, "id", "");
        String type = ConfigNodes.string(map, "type", "");
        int minLevel = Numbers.tryParseInt(ConfigNodes.get(map, "min_level"), 0);
        return new ResonancePatternEntry(id, type, minLevel);
    }

    private ResonanceEffects parseEffects(YamlSection section) {
        if (section == null) {
            return new ResonanceEffects(null, null, null, null, null);
        }
        List<String> actions = section.getStringList("actions");
        Map<String, Double> stats = parseStatMap(section);
        List<String> skills = section.getStringList("es_skills");
        Object nameActions = section.get("name_actions");
        Object loreActions = section.get("lore_actions");
        return new ResonanceEffects(actions, stats, skills, nameActions, loreActions);
    }

    private Map<String, Double> parseStatMap(YamlSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, Object> attributes = parseObjectMap(section.getSection("ea_attributes"));
        return attributes.isEmpty() ? Map.of() : resolveRawValues(attributes, Map.of());
    }

    private Map<String, Double> resolveRawValues(Map<String, Object> rawValues, Map<String, ?> context) {
        Map<String, Double> resolved = new LinkedHashMap<>();
        if (rawValues == null || rawValues.isEmpty()) {
            return resolved;
        }
        Map<String, Object> evalContext = new LinkedHashMap<>();
        if (context != null) {
            evalContext.putAll(context);
        }
        for (Map.Entry<String, Object> entry : rawValues.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String key = Texts.lower(entry.getKey());
            double value = resolveNumericValue(entry.getValue(), evalContext);
            resolved.put(key, value);
            evalContext.put(key, value);
        }
        return resolved;
    }

    private double resolveNumericValue(Object raw, Map<String, ?> variables) {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        return ExpressionEngine.evaluateRandomConfig(raw, variables);
    }

    private Map<String, Object> parseObjectMap(YamlSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = ConfigNodes.toPlainData(section.get(key));
            if (value != null) {
                stats.put(Texts.lower(key), value);
            }
        }
        return stats;
    }
}
