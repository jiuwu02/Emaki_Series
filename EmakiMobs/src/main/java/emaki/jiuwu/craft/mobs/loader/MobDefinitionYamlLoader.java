package emaki.jiuwu.craft.mobs.loader;

import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;
import emaki.jiuwu.craft.corelib.api.animation.AnimationKeyframe;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.mobs.selector.TargetLockConfig;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MobDefinitionYamlLoader extends YamlDirectoryLoader<MobSpec> {

    private static final String KEY_EA_ATTRIBUTES = "ea_attributes";
    private static final String KEY_ACTIONS = "actions";
    private static final String LEGACY_KEY_ATTRIBUTES = "attributes";
    private static final String LEGACY_KEY_SKILLS = "skills";

    public MobDefinitionYamlLoader(JavaPlugin plugin) {
        super(plugin);
    }

    public List<String> deprecations() {
        List<String> result = new ArrayList<>();
        for (LoadedYamlEntry<MobSpec> entry : entries().values()) {
            YamlSection config = entry.configuration();
            if (config == null) {
                continue;
            }
            appendDeprecation(result, config, entry.file(), KEY_EA_ATTRIBUTES, LEGACY_KEY_ATTRIBUTES);
            appendDeprecation(result, config, entry.file(), KEY_ACTIONS, LEGACY_KEY_SKILLS);
        }
        return List.copyOf(result);
    }

    private void appendDeprecation(List<String> sink,
                                   YamlSection config,
                                   File file,
                                   String currentKey,
                                   String legacyKey) {
        if (config.getSection(currentKey) != null || config.getSection(legacyKey) == null) {
            return;
        }
        sink.add(localized("loader.deprecated_key", Map.of(
                "file", file == null ? "?" : file.getName(),
                "old_key", legacyKey,
                "new_key", currentKey)));
    }

    @Override
    protected String directoryName() {
        return "mobs";
    }

    @Override
    protected String typeName() {
        return localized("loader.type.mob");
    }

    @Override
    protected MobSpec parse(File file, YamlSection config) {
        String id = config.getString("id");
        if (id == null || id.isBlank()) {
            issue("loader.invalid_blank_id", Map.of("type", typeName(), "file", file.getName()));
            return null;
        }
        String typeName = config.getString("type", id);
        EntityType entityType;
        try {
            entityType = EntityType.valueOf(typeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            issue("loader.unknown_entity_type",
                    Map.of("type", typeName, "file", file.getName()));
            return null;
        }
        String displayName = config.getString("display_name");
        int experience = config.getInt("experience", 0);
        Map<String, Object> components = extractSection(config, "components");
        Map<String, Double> eaAttributes = extractDoubleSection(
                config, resolveKey(config, file, KEY_EA_ATTRIBUTES, LEGACY_KEY_ATTRIBUTES));
        Map<String, List<String>> actions = extractListSection(
                config, resolveKey(config, file, KEY_ACTIONS, LEGACY_KEY_SKILLS));
        boolean typeOverride = isEntityTypeName(id);
        ThreatConfig threatConfig = parseThreatConfig(config);
        BossBarConfig bossBarConfig = parseBossBarConfig(config);
        MobModelConfig modelConfig = parseModelConfig(config, file);
        String targetSelector = normalizeOptionalId(config.getString("target_selector"));
        TargetLockConfig targetLockConfig = parseTargetLockConfig(config);
        return new MobSpec(id, entityType, displayName, components, eaAttributes, actions, experience,
                typeOverride, threatConfig, bossBarConfig, modelConfig, targetSelector, targetLockConfig);
    }

    private String resolveKey(YamlSection config, File file, String currentKey, String legacyKey) {
        if (config.getSection(currentKey) != null || config.getSection(legacyKey) == null) {
            return currentKey;
        }
        plugin.getLogger().warning("Mob file '" + file.getName() + "' uses deprecated key '"
                + legacyKey + "'; rename it to '" + currentKey
                + "'. The legacy key will be removed in the next major version.");
        return legacyKey;
    }

    @Override
    protected String idOf(MobSpec value) {
        return value.id();
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
    private ThreatConfig parseThreatConfig(YamlSection config) {
        YamlSection section = config.getSection("threat");
        if (section == null) return null;
        boolean enabled = section.getBoolean("enabled", true);
        double maxRange = section.getDouble("max_range", 64.0);
        YamlSection weightsSection = section.getSection("weights");
        double damageW = weightsSection != null ? weightsSection.getDouble("damage", 1.0) : 1.0;
        double healingW = weightsSection != null ? weightsSection.getDouble("healing", 0.5) : 0.5;
        YamlSection decaySection = section.getSection("decay");
        double decayRate = decaySection != null ? decaySection.getDouble("rate", 0.05) : 0.05;
        boolean outOfRange = decaySection == null || decaySection.getBoolean("out_of_range", true);
        return new ThreatConfig(enabled,
                new ThreatConfig.ThreatWeightsConfig(damageW, healingW),
                new ThreatConfig.ThreatDecayConfig(decayRate, outOfRange),
                maxRange);
    }

    private String normalizeOptionalId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private TargetLockConfig parseTargetLockConfig(YamlSection config) {
        YamlSection section = config.getSection("lock_target");
        if (section == null) {
            return TargetLockConfig.disabled();
        }
        boolean enabled = section.getBoolean("enabled", false);
        int intervalTicks = section.getInt("interval_ticks", 20);
        return new TargetLockConfig(enabled, intervalTicks);
    }

    @Nullable
    private BossBarConfig parseBossBarConfig(YamlSection config) {
        YamlSection section = config.getSection("boss_bar");
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

    @Nullable
    private MobModelConfig parseModelConfig(YamlSection config, File file) {
        YamlSection section = config.getSection("model");
        if (section == null) {
            return null;
        }
        String blueprint = normalizeOptionalId(section.getString("blueprint"));
        if (blueprint == null) {
            issue("loader.model_missing_blueprint", Map.of("file", file.getName()));
            return null;
        }
        String api = normalizeOptionalId(section.getString("api"));
        double scale = section.getDouble("scale", 1.0);
        Map<String, String> animations = new HashMap<>();
        YamlSection animationsSection = section.getSection("animations");
        if (animationsSection != null) {
            for (String key : animationsSection.getKeys(false)) {
                String value = animationsSection.getString(key);
                if (value != null && !value.isBlank()) {
                    animations.put(key.toLowerCase(Locale.ROOT), value.trim());
                }
            }
        }
        Map<String, MobModelConfig.KeyframeTimeline> keyframes = new HashMap<>();
        YamlSection keyframesSection = section.getSection("keyframes");
        if (keyframesSection != null) {
            for (String key : keyframesSection.getKeys(false)) {
                MobModelConfig.KeyframeTimeline timeline = parseKeyframeTimeline(
                        keyframesSection.getSection(key), key, file);
                if (timeline != null) {
                    keyframes.put(key.toLowerCase(Locale.ROOT), timeline);
                }
            }
        }
        MobModelConfig.LodBounds lod = parseLodBounds(section.getSection("lod"));
        return new MobModelConfig(blueprint, api, scale, Map.copyOf(animations), Map.copyOf(keyframes), lod);
    }

    @Nullable
    private MobModelConfig.KeyframeTimeline parseKeyframeTimeline(@Nullable YamlSection section,
            String animationId, File file) {
        if (section == null) {
            return null;
        }
        int duration = section.getInt("duration_ticks", 20);
        if (duration < 1) {
            issue("loader.model_invalid_duration",
                    Map.of("file", file.getName(), "animation", animationId, "duration", duration));
            return null;
        }
        boolean loop = section.getBoolean("loop", false);
        String priority = section.getString("priority", "ambient");
        String conflict = section.getString("conflict", "replace");
        List<AnimationKeyframe> frames = new ArrayList<>();
        for (Map<?, ?> entry : section.getMapList("frames")) {
            AnimationKeyframe frame = parseKeyframe(entry, animationId, file);
            if (frame != null) {
                frames.add(frame);
            }
        }
        return new MobModelConfig.KeyframeTimeline(duration, loop, priority, conflict, List.copyOf(frames));
    }

    @Nullable
    private AnimationKeyframe parseKeyframe(Map<?, ?> entry, String animationId, File file) {
        Object tickValue = entry.get("tick");
        if (!(tickValue instanceof Number tick)) {
            issue("loader.model_keyframe_missing_tick",
                    Map.of("file", file.getName(), "animation", animationId));
            return null;
        }
        Object lineValue = entry.get("action");
        String actionLine = lineValue == null ? "" : String.valueOf(lineValue);
        Object labelValue = entry.get("label");
        String label = labelValue == null ? "" : String.valueOf(labelValue);
        return AnimationKeyframe.of(tick.intValue(), actionLine, label);
    }

    @Nullable
    private MobModelConfig.LodBounds parseLodBounds(@Nullable YamlSection section) {
        if (section == null) {
            return null;
        }
        double near = section.getDouble("near", 16.0);
        double mid = section.getDouble("mid", 32.0);
        double far = section.getDouble("far", 48.0);
        return new MobModelConfig.LodBounds(near, mid, far);
    }

    private Map<String, Object> extractSection(YamlSection config, String sectionKey) {
        Map<String, Object> result = new HashMap<>();
        YamlSection section = config.getSection(sectionKey);
        if (section != null) {
            for (String key : section.getKeys(false)) {
                result.put(key, section.get(key));
            }
        }
        return result;
    }

    private Map<String, Double> extractDoubleSection(YamlSection config, String sectionKey) {
        Map<String, Double> result = new HashMap<>();
        YamlSection section = config.getSection(sectionKey);
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

    private Map<String, List<String>> extractListSection(YamlSection config, String sectionKey) {
        Map<String, List<String>> result = new HashMap<>();
        YamlSection section = config.getSection(sectionKey);
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
