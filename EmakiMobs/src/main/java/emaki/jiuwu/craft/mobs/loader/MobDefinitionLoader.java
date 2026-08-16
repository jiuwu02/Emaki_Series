package emaki.jiuwu.craft.mobs.loader;

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MobDefinitionLoader {

    private final Plugin plugin;

    public MobDefinitionLoader(Plugin plugin) {
        this.plugin = plugin;
    }

    public Map<String, MobSpec> loadAll() {
        Map<String, MobSpec> result = new HashMap<>();
        File mobsDir = new File(plugin.getDataFolder(), "mobs");
        if (!mobsDir.isDirectory()) {
            return result;
        }
        File[] files = mobsDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return result;
        }
        for (File file : files) {
            MobSpec spec = parseFile(file);
            if (spec != null) {
                result.put(spec.id(), spec);
            }
        }
        return result;
    }

    private MobSpec parseFile(File file) {
        var config = YamlConfiguration.loadConfiguration(file);
        String id = config.getString("id");
        if (id == null || id.isBlank()) {
            plugin.getLogger().warning("Mob file '" + file.getName() + "' missing 'id' field, skipping.");
            return null;
        }
        String typeName = config.getString("type", id);
        EntityType entityType;
        try {
            entityType = EntityType.valueOf(typeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning(
                    "Unknown entity type '" + typeName + "' in '" + file.getName() + "', skipping.");
            return null;
        }
        String displayName = config.getString("display_name");
        int experience = config.getInt("experience", 0);
        Map<String, Object> components = extractSection(config, "components");
        Map<String, Double> attributes = extractDoubleSection(config, "attributes");
        Map<String, List<String>> skills = extractListSection(config, "skills");
        boolean typeOverride = isEntityTypeName(id);
        ThreatConfig threatConfig = parseThreatConfig(config);
        BossBarConfig bossBarConfig = parseBossBarConfig(config);
        return new MobSpec(id, entityType, displayName, components, attributes, skills, experience,
                typeOverride, threatConfig, bossBarConfig);
    }

    private boolean isEntityTypeName(String id) {
        try {
            EntityType.valueOf(id.toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Nullable
    private ThreatConfig parseThreatConfig(YamlConfiguration config) {
        var section = config.getConfigurationSection("threat");
        if (section == null) return null;
        boolean enabled = section.getBoolean("enabled", true);
        double maxRange = section.getDouble("max_range", 64.0);
        var weightsSection = section.getConfigurationSection("weights");
        double damageW = weightsSection != null ? weightsSection.getDouble("damage", 1.0) : 1.0;
        double healingW = weightsSection != null ? weightsSection.getDouble("healing", 0.5) : 0.5;
        var decaySection = section.getConfigurationSection("decay");
        double decayRate = decaySection != null ? decaySection.getDouble("rate", 0.05) : 0.05;
        boolean outOfRange = decaySection == null || decaySection.getBoolean("out_of_range", true);
        return new ThreatConfig(enabled,
                new ThreatConfig.ThreatWeightsConfig(damageW, healingW),
                new ThreatConfig.ThreatDecayConfig(decayRate, outOfRange),
                maxRange);
    }

    @Nullable
    private BossBarConfig parseBossBarConfig(YamlConfiguration config) {
        var section = config.getConfigurationSection("boss_bar");
        if (section == null) return null;
        String title = section.getString("title", "");
        String colorStr = section.getString("color", "PURPLE");
        String styleStr = section.getString("style", "SOLID");
        double range = section.getDouble("range", 64.0);
        BarColor color;
        try { color = BarColor.valueOf(colorStr.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { color = BarColor.PURPLE; }
        BarStyle style;
        try { style = BarStyle.valueOf(styleStr.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { style = BarStyle.SOLID; }
        return new BossBarConfig(title, color, style, range);
    }

    private Map<String, Object> extractSection(YamlConfiguration config, String sectionKey) {
        Map<String, Object> result = new HashMap<>();
        var section = config.getConfigurationSection(sectionKey);
        if (section != null) {
            for (String key : section.getKeys(false)) {
                result.put(key, section.get(key));
            }
        }
        return result;
    }

    private Map<String, Double> extractDoubleSection(YamlConfiguration config, String sectionKey) {
        Map<String, Double> result = new HashMap<>();
        var section = config.getConfigurationSection(sectionKey);
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Object val = section.get(key);
                if (val instanceof Number num) {
                    result.put(key, num.doubleValue());
                }
            }
        }
        return result;
    }

    /**
     * 解析 YAML 中的技能触发器映射。
     *
     * <pre>
     * skills:
     *   on_death:
     *     - "send_message(...)"
     *   on_damage_give:
     *     - "apply_potion(...)"
     * </pre>
     *
     * @param config     要解析的 YAML 配置
     * @param sectionKey 顶层节点键名（通常为 {@code "skills"}）
     * @return 触发器名 → Action 管道行列表 的映射；无此节点时返回空 Map
     */
    private Map<String, List<String>> extractListSection(YamlConfiguration config, String sectionKey) {
        Map<String, List<String>> result = new HashMap<>();
        var section = config.getConfigurationSection(sectionKey);
        if (section != null) {
            for (String key : section.getKeys(false)) {
                List<String> lines = new ArrayList<>(section.getStringList(key));
                if (!lines.isEmpty()) {
                    result.put(key, lines);
                }
            }
        }
        return result;
    }
}
