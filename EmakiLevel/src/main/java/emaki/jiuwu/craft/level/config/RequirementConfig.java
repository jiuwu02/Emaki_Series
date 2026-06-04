package emaki.jiuwu.craft.level.config;

import java.util.LinkedHashMap;
import java.util.Map;

import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

public record RequirementConfig(RequirementGroup global, Map<String, RequirementGroup> groups) {

    public RequirementConfig {
        global = global == null ? new RequirementGroup("floor(100 + 25 * %target_level%)", Map.of()) : global;
        groups = groups == null ? Map.of() : Map.copyOf(groups);
    }

    public static RequirementConfig parse(YamlSection section) {
        if (section == null || section.isEmpty()) {
            return new RequirementConfig(new RequirementGroup("floor(100 + 25 * %target_level% + 8 * pow(%target_level%, 2))", Map.of()), Map.of());
        }
        RequirementGroup global = RequirementGroup.parse(section.getSection("global"));
        Map<String, RequirementGroup> groups = new LinkedHashMap<>();
        YamlSection groupSection = section.getSection("groups");
        if (groupSection != null) {
            for (String key : groupSection.getKeys(false)) {
                groups.put(Texts.normalizeId(key), RequirementGroup.parse(groupSection.getSection(key)));
            }
        }
        return new RequirementConfig(global, groups);
    }

    public record RequirementGroup(String formula, Map<Integer, Double> values) {

        public RequirementGroup {
            formula = Texts.toStringSafe(formula);
            values = values == null ? Map.of() : Map.copyOf(values);
        }

        static RequirementGroup parse(YamlSection section) {
            if (section == null) {
                return new RequirementGroup("", Map.of());
            }
            Map<Integer, Double> values = new LinkedHashMap<>();
            YamlSection valueSection = section.getSection("values");
            if (valueSection != null) {
                for (String key : valueSection.getKeys(false)) {
                    try {
                        values.put(Integer.parseInt(key), Double.parseDouble(Texts.toStringSafe(valueSection.get(key))));
                    } catch (NumberFormatException _) {
                    }
                }
            }
            return new RequirementGroup(section.getString("formula", ""), values);
        }
    }
}
