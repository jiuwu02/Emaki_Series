package emaki.jiuwu.craft.skills.loader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import emaki.jiuwu.craft.skills.model.CostOperation;
import emaki.jiuwu.craft.skills.model.ResourceCostType;
import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.skills.model.SkillResourceCost;

public final class SkillDefinitionLoader extends YamlDirectoryLoader<SkillDefinition> {

    public SkillDefinitionLoader(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    protected String directoryName() {
        return "skills";
    }

    @Override
    protected String typeName() {
        return "skill";
    }

    @Override
    protected SkillDefinition parse(File file, YamlSection configuration) {
        String fallbackId = baseName(file);
        if (configuration == null) {
            issue("loader.invalid_config", Map.of("type", typeName(), "file", file == null ? "-" : file.getName()));
            return null;
        }
        String id = Texts.lower(configuration.getString("id", fallbackId));
        if (Texts.isBlank(id)) {
            onBlankId(file);
            return null;
        }
        boolean enabled = configuration.getBoolean("enabled", true);
        if (!enabled) {
            return null;
        }

        List<SkillResourceCost> resourceCosts = parseResourceCosts(configuration.getMapList("resource_costs"));

        return new SkillDefinition(
                id,
                configuration.getString("display_name", id),
                configuration.getStringList("description"),
                configuration.getString("icon_source", ""),
                configuration.getString("mythic_skill", ""),
                configuration.getInt("cooldown_ticks", 0),
                configuration.getInt("global_cooldown_ticks", 0),
                resourceCosts,
                configuration.getStringList("lore_aliases"),
                configuration.getString("pdc_skill_id", id),
                configuration.getString("ui_category", "default"),
                configuration.getInt("sort_order", 0),
                enabled
        );
    }

    @Override
    protected String idOf(SkillDefinition value) {
        return value.id();
    }

    private List<SkillResourceCost> parseResourceCosts(List<Map<?, ?>> mapList) {
        if (mapList == null || mapList.isEmpty()) {
            return List.of();
        }
        List<SkillResourceCost> costs = new ArrayList<>();
        for (Map<?, ?> map : mapList) {
            SkillResourceCost cost = parseResourceCost(map);
            if (cost != null) {
                costs.add(cost);
            }
        }
        return costs;
    }

    private SkillResourceCost parseResourceCost(Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        ResourceCostType type = ResourceCostType.fromString(Texts.toStringSafe(map.get("type")));
        if (type == null) {
            return null;
        }
        String targetId = Texts.toStringSafe(map.get("target_id")).trim();
        double amount = parseDouble(map.get("amount"), 0D);
        CostOperation operation = CostOperation.fromString(Texts.toStringSafe(map.get("operation")));
        String failureMessage = Texts.toStringSafe(map.get("failure_message")).trim();
        return new SkillResourceCost(type, targetId, amount, operation, failureMessage);
    }

    private double parseDouble(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(Texts.toStringSafe(value).trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private String baseName(File file) {
        String name = file == null ? "" : file.getName();
        int dot = name.lastIndexOf('.');
        return Texts.lower(dot >= 0 ? name.substring(0, dot) : name);
    }
}
