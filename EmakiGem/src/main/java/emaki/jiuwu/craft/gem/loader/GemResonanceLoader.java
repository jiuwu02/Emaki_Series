package emaki.jiuwu.craft.gem.loader;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.model.GemResonanceDefinition;
import emaki.jiuwu.craft.gem.model.ResonanceChain;
import emaki.jiuwu.craft.gem.model.ResonanceEffects;
import emaki.jiuwu.craft.gem.model.ResonanceLoreSection;
import emaki.jiuwu.craft.gem.model.ResonanceNameModification;
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
        return "resonance";
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
        ResonanceChain chain = parseChain(configuration.getSection("chain"));
        if (chain == null || chain.pattern().isEmpty()) {
            issue("loader.invalid_config", Map.of("type", typeName(), "file", file.getName()));
            return null;
        }
        ResonanceEffects effects = parseEffects(configuration.getSection("effects"));
        return new GemResonanceDefinition(id, displayName, chain, effects);
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
        return new ResonancePatternEntry(id, type);
    }

    private ResonanceEffects parseEffects(YamlSection section) {
        if (section == null) {
            return new ResonanceEffects(null, null, null, null, null);
        }
        List<String> actions = section.getStringList("actions");
        Map<String, Double> stats = parseStatMap(section.getSection("variables"));
        List<String> skills = section.getStringList("skills");
        ResonanceNameModification nameModification = parseNameActions(section.getMapList("name_actions"));
        ResonanceLoreSection loreSection = parseLoreSection(section.getSection("lore_section"));
        return new ResonanceEffects(actions, stats, skills, nameModification, loreSection);
    }

    private Map<String, Double> parseStatMap(YamlSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, Double> stats = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Double value = Numbers.tryParseDouble(section.get(key), null);
            if (value != null) {
                stats.put(Texts.lower(key), value);
            }
        }
        return stats;
    }

    private ResonanceNameModification parseNameActions(List<Map<?, ?>> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        Map<?, ?> first = list.get(0);
        String action = ConfigNodes.string(first, "action", "");
        String value = ConfigNodes.string(first, "value", "");
        if (Texts.isBlank(value)) {
            return null;
        }
        String position = action.contains("suffix") ? "suffix" : "prefix";
        return new ResonanceNameModification(position, value);
    }

    private ResonanceLoreSection parseLoreSection(YamlSection section) {
        if (section == null) {
            return null;
        }
        String sectionId = section.getString("section_id", "gem_resonance");
        int order = section.getInt("order", 90);
        List<String> lines = section.getStringList("lines");
        if (lines.isEmpty()) {
            return null;
        }
        return new ResonanceLoreSection(sectionId, order, lines);
    }
}
