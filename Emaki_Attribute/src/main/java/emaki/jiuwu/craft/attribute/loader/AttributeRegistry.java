package emaki.jiuwu.craft.attribute.loader;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.model.AttributeDefinition;
import emaki.jiuwu.craft.attribute.model.AttributeTargetType;
import emaki.jiuwu.craft.attribute.model.AttributeValueKind;
import emaki.jiuwu.craft.attribute.model.LoreFormatDefinition;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.configuration.file.YamlConfiguration;

public final class AttributeRegistry extends DirectoryLoader<AttributeDefinition> {

    private static final List<String> BUNDLED_RESOURCES = List.of(
        "attack_speed.yml",
        "attribute_power.yml",
        "dodge_chance.yml",
        "entity_interaction_range.yml",
        "entity_scale.yml",
        "health.yml",
        "health_regen.yml",
        "lifesteal.yml",
        "lifesteal_resistance.yml",
        "magic_crit_evasion.yml",
        "magic_crit_multiplier_resistance.yml",
        "magic_penetration.yml",
        "mana.yml",
        "mana_regen.yml",
        "movement_speed.yml",
        "percentage_lifesteal.yml",
        "physical_armor_penetration.yml",
        "physical_attack.yml",
        "physical_crit_damage.yml",
        "physical_crit_evasion.yml",
        "physical_crit_multiplier_resistance.yml",
        "physical_crit_rate.yml",
        "physical_damage_bonus.yml",
        "physical_defense.yml",
        "projectile_attack.yml",
        "projectile_crit_damage.yml",
        "projectile_crit_evasion.yml",
        "projectile_crit_multiplier_resistance.yml",
        "projectile_crit_rate.yml",
        "projectile_damage_bonus.yml",
        "projectile_defense.yml",
        "projectile_penetration.yml",
        "real_damage.yml",
        "skill_cdr.yml",
        "skill_crit_damage.yml",
        "skill_crit_rate.yml",
        "skill_damage_bonus.yml",
        "speed.yml",
        "spell_attack.yml",
        "spell_crit_damage.yml",
        "spell_crit_rate.yml",
        "spell_damage_bonus.yml",
        "spell_defense.yml",
        "test_attribute.yml"
    );

    private final Map<String, AttributeDefinition> aliasIndex = new LinkedHashMap<>();
    private final List<PatternEntry> orderedPatterns = new ArrayList<>();
    private static final Pattern NUMERIC_CAPTURE_PATTERN = Pattern.compile("^[+-]?\\d+(?:\\.\\d+)?$");

    private record PatternEntry(AttributeDefinition definition, Pattern pattern, int priority) {
    }

    public AttributeRegistry(EmakiAttributePlugin plugin) {
        super(plugin);
    }

    @Override
    protected String directoryName() {
        return "attributes";
    }

    @Override
    protected String typeName() {
        return plugin.messageService() == null ? "属性" : plugin.messageService().message("label.attribute");
    }

    @Override
    protected AttributeDefinition parse(File file, YamlConfiguration configuration) {
        List<String> patterns = new ArrayList<>();
        patterns.addAll(configuration.getStringList("lore_patterns"));
        String singlePattern = configuration.getString("lore_pattern");
        if (Texts.isNotBlank(singlePattern)) {
            patterns.add(singlePattern);
        }
        AttributeValueKind valueKind = parseEnum(configuration.getString("value_kind", "FLAT"), AttributeValueKind.FLAT);
        AttributeTargetType targetType = parseEnum(configuration.getString("target_type", "GENERIC"), AttributeTargetType.GENERIC);
        return new AttributeDefinition(
            configuration.getString("id"),
            configuration.getString("display_name"),
            configuration.getStringList("aliases"),
            valueKind,
            targetType,
            configuration.getString("target_id"),
            configuration.getDouble("default_value", 0D),
            configuration.contains("min_value") ? configuration.getDouble("min_value") : null,
            configuration.contains("max_value") ? configuration.getDouble("max_value") : null,
            configuration.getBoolean("allow_negative", true),
            configuration.getInt("priority", 0),
            configuration.getString("lore_format_id"),
            patterns,
            configuration.getString("description"),
            configuration.contains("attribute_power") ? configuration.getDouble("attribute_power") : 1D
        );
    }

    @Override
    protected void seedBundledResources(File directory) {
        for (String resourceName : BUNDLED_RESOURCES) {
            copyBundledResource("attributes/" + resourceName, new File(directory, resourceName));
        }
    }

    @Override
    protected boolean validateSchema(File file, YamlConfiguration configuration) {
        boolean valid = true;
        if (Texts.isBlank(configuration.getString("id"))) {
            issue(
                "loader.schema_missing_id",
                Map.of(
                    "type", typeName(),
                    "file", file.getName(),
                    "field", "id"
                )
            );
            valid = false;
        }
        if (configuration.contains("value_kind") && !isValidEnum(configuration.getString("value_kind"), AttributeValueKind.class)) {
            issue(
                "loader.schema_invalid_enum",
                Map.of(
                    "type", typeName(),
                    "file", file.getName(),
                    "field", "value_kind"
                )
            );
            valid = false;
        }
        if (configuration.contains("target_type") && !isValidEnum(configuration.getString("target_type"), AttributeTargetType.class)) {
            issue(
                "loader.schema_invalid_enum",
                Map.of(
                    "type", typeName(),
                    "file", file.getName(),
                    "field", "target_type"
                )
            );
            valid = false;
        }
        if (configuration.contains("priority") && !isNumeric(configuration.get("priority"))) {
            issue(
                "loader.schema_invalid_number",
                Map.of(
                    "type", typeName(),
                    "file", file.getName(),
                    "field", "priority"
                )
            );
            valid = false;
        }
        if (configuration.contains("default_value") && !isNumeric(configuration.get("default_value"))) {
            issue(
                "loader.schema_invalid_number",
                Map.of(
                    "type", typeName(),
                    "file", file.getName(),
                    "field", "default_value"
                )
            );
            valid = false;
        }
        if (configuration.contains("min_value") && !isNumeric(configuration.get("min_value"))) {
            issue(
                "loader.schema_invalid_number",
                Map.of(
                    "type", typeName(),
                    "file", file.getName(),
                    "field", "min_value"
                )
            );
            valid = false;
        }
        if (configuration.contains("max_value") && !isNumeric(configuration.get("max_value"))) {
            issue(
                "loader.schema_invalid_number",
                Map.of(
                    "type", typeName(),
                    "file", file.getName(),
                    "field", "max_value"
                )
            );
            valid = false;
        }
        if (configuration.contains("attribute_power") && !isNumeric(configuration.get("attribute_power"))) {
            issue(
                "loader.schema_invalid_number",
                Map.of(
                    "type", typeName(),
                    "file", file.getName(),
                    "field", "attribute_power"
                )
            );
            valid = false;
        }
        return valid;
    }

    @Override
    protected String idOf(AttributeDefinition value) {
        return value.id();
    }

    @Override
    protected void afterLoad() {
        aliasIndex.clear();
        orderedPatterns.clear();
        List<AttributeDefinition> definitions = new ArrayList<>(items.values());
        definitions.sort((left, right) -> Integer.compare(right.priority(), left.priority()));
        for (AttributeDefinition definition : definitions) {
            aliasIndex.put(definition.id(), definition);
            if (Texts.isNotBlank(definition.displayName())) {
                aliasIndex.putIfAbsent(normalizeId(definition.displayName()), definition);
            }
            for (String alias : definition.aliases()) {
                aliasIndex.putIfAbsent(normalizeId(alias), definition);
            }
            orderedPatterns.addAll(compilePatterns(definition));
        }
        orderedPatterns.sort(Comparator.comparingInt(PatternEntry::priority).reversed());
        logLoadReport(definitions);
    }

    public AttributeDefinition resolve(String id) {
        if (Texts.isBlank(id)) {
            return null;
        }
        return aliasIndex.get(normalizeId(id));
    }

    public AttributeDefinition matchLine(String normalizedLine) {
        if (Texts.isBlank(normalizedLine)) {
            return null;
        }
        for (PatternEntry entry : orderedPatterns) {
            if (entry.pattern().matcher(normalizedLine).find()) {
                return entry.definition();
            }
        }
        return null;
    }

    public String extractMatchedValue(String normalizedLine, AttributeDefinition definition) {
        if (Texts.isBlank(normalizedLine) || definition == null) {
            return null;
        }
        for (PatternEntry entry : orderedPatterns) {
            if (!entry.definition().id().equals(definition.id())) {
                continue;
            }
            var matcher = entry.pattern().matcher(normalizedLine);
            if (matcher.find()) {
                String capturedValue = extractCapturedValue(matcher, definition);
                if (Texts.isNotBlank(capturedValue)) {
                    return capturedValue;
                }
            }
        }
        return null;
    }

    private List<PatternEntry> compilePatterns(AttributeDefinition definition) {
        if (definition == null) {
            return List.of();
        }
        List<PatternEntry> compiled = new ArrayList<>();
        LoreFormatDefinition format = resolveLoreFormat(definition.loreFormatId());
        int basePriority = resolveReadPriority(definition, format);
        if (format != null && !format.readPatterns().isEmpty()) {
            compiled.addAll(compilePatternTemplates(definition, format.readPatterns(), basePriority));
        }
        if (!definition.lorePatterns().isEmpty()) {
            compiled.addAll(compilePatternTemplates(definition, definition.lorePatterns(), basePriority + 1));
        }
        if (compiled.isEmpty()) {
            compiled.addAll(compilePatternTemplates(definition, fallbackPatternTemplates(definition), basePriority));
        }
        return compiled;
    }

    private List<PatternEntry> compilePatternTemplates(AttributeDefinition definition, List<String> templates, int priority) {
        if (definition == null || templates == null || templates.isEmpty()) {
            return List.of();
        }
        List<PatternEntry> compiled = new ArrayList<>();
        for (String template : templates) {
            String expanded = expandPatternTemplate(template, definition);
            if (Texts.isBlank(expanded)) {
                continue;
            }
            try {
                compiled.add(new PatternEntry(
                    definition,
                    Pattern.compile(expanded, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                    priority
                ));
            } catch (Exception exception) {
                issue(
                    "loader.invalid_lore_pattern",
                    Map.of(
                        "attribute", definition.id(),
                        "pattern", template,
                        "error", Texts.toStringSafe(exception.getMessage())
                    )
                );
            }
        }
        return compiled;
    }

    private List<String> fallbackPatternTemplates(AttributeDefinition definition) {
        if (definition == null) {
            return List.of();
        }
        return switch (definition.loreFormatId()) {
            case "default_percent" -> List.of("{Key}.*?: ?{Value}%$");
            case "default_regen" -> List.of("{Key}.*?: ?{Value}/秒$");
            default -> definition.isPercentLike()
                ? List.of("{Key}.*?: ?{Value}%$")
                : List.of("{Key}.*?: ?{Value}$");
        };
    }

    private String expandPatternTemplate(String template, AttributeDefinition definition) {
        if (Texts.isBlank(template) || definition == null) {
            return "";
        }
        String key = buildKeyPattern(definition);
        String value = buildValuePattern();
        return template
            .replace("{Key}", key)
            .replace("{key}", key)
            .replace("{Value}", value)
            .replace("{value}", value);
    }

    private String buildKeyPattern(AttributeDefinition definition) {
        List<String> options = new ArrayList<>();
        List<String> keys = keyCandidates(definition);
        for (String key : keys) {
            String quoted = Pattern.quote(key);
            if (!options.contains(quoted)) {
                options.add(quoted);
            }
        }
        if (options.isEmpty()) {
            return "(?:)";
        }
        if (options.size() == 1) {
            return options.get(0);
        }
        return "(?:" + String.join("|", options) + ")";
    }

    private List<String> keyCandidates(AttributeDefinition definition) {
        if (definition == null) {
            return List.of();
        }
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        if (Texts.isNotBlank(definition.displayName())) {
            keys.add(definition.displayName());
        }
        if (Texts.isNotBlank(definition.id())) {
            keys.add(definition.id());
        }
        if (definition.aliases() != null) {
            for (String alias : definition.aliases()) {
                if (Texts.isNotBlank(alias)) {
                    keys.add(alias);
                }
            }
        }
        List<String> result = new ArrayList<>(keys);
        result.sort((left, right) -> Integer.compare(right.length(), left.length()));
        return result;
    }

    private String extractCapturedValue(Matcher matcher, AttributeDefinition definition) {
        if (matcher == null || matcher.groupCount() <= 0) {
            return null;
        }
        for (int group = 1; group <= matcher.groupCount(); group++) {
            String value = Texts.toStringSafe(matcher.group(group)).trim();
            if (Texts.isBlank(value)) {
                continue;
            }
            String cleaned = value.replace(",", "");
            if (definition != null && definition.isPercentLike() && cleaned.endsWith("%")) {
                cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
            }
            if (NUMERIC_CAPTURE_PATTERN.matcher(cleaned).matches()) {
                return cleaned;
            }
        }
        return null;
    }

    private String buildValuePattern() {
        return "([+-]?\\d+(?:\\.\\d+)?)";
    }

    private int resolveReadPriority(AttributeDefinition definition, LoreFormatDefinition format) {
        if (format != null) {
            return format.readPriority();
        }
        if (definition == null) {
            return 0;
        }
        String loreFormatId = normalizeId(definition.loreFormatId());
        return switch (loreFormatId) {
            case "default_percent" -> 100;
            case "default_regen" -> 80;
            case "default_resource" -> 60;
            case "default_flat" -> 50;
            default -> definition.isPercentLike() ? 100 : 50;
        };
    }

    private LoreFormatDefinition resolveLoreFormat(String loreFormatId) {
        if (Texts.isBlank(loreFormatId) || plugin == null || plugin.loreFormatRegistry() == null) {
            return null;
        }
        LoreFormatRegistry registry = plugin.loreFormatRegistry();
        return registry == null ? null : registry.get(loreFormatId);
    }

    private <E extends Enum<E>> E parseEnum(String value, E defaultValue) {
        if (Texts.isBlank(value)) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(defaultValue.getDeclaringClass(), value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private void logLoadReport(List<AttributeDefinition> definitions) {
        if (definitions == null || definitions.isEmpty()) {
            logInfo(
                "console.attribute_registry_empty",
                Map.of(),
                "[属性注册] 未加载到任何属性定义。"
            );
            return;
        }

        logInfo(
            "console.attribute_registry_summary",
            Map.of("count", definitions.size()),
            "[属性注册] 已加载 " + definitions.size() + " 项属性定义。"
        );

        int index = 1;
        for (AttributeDefinition definition : definitions) {
            int displayIndex = index++;
            String displayName = safeText(definition.displayName(), definition.id());
            String id = safeText(definition.id(), "-");
            String defaultValue = formatNumber(definition.defaultValue());
            String attributePower = formatNumber(definition.attributePower());
            logInfo(
                "console.attribute_registry_entry",
                Map.of(
                    "index", String.format(Locale.ROOT, "%02d", displayIndex),
                    "display_name", displayName,
                    "id", id,
                    "default_value", defaultValue,
                    "attribute_power", attributePower
                ),
                String.format(
                    Locale.ROOT,
                    "[属性注册] %02d | %s (%s) | 默认值 %s | 战力系数 %s",
                    displayIndex,
                    displayName,
                    id,
                    defaultValue,
                    attributePower
                )
            );
        }
    }

    private void logInfo(String key, Map<String, ?> replacements, String fallback) {
        if (plugin.messageService() != null) {
            plugin.messageService().info(key, replacements);
            return;
        }
        plugin.getLogger().info(fallback);
    }

    private String safeText(String value, String fallback) {
        return Texts.isBlank(value) ? fallback : value;
    }

    private String formatNumber(double value) {
        return Numbers.formatNumber(value, "0.###");
    }

    private static boolean isNumeric(Object value) {
        return Numbers.tryParseDouble(value, null) != null;
    }

    private static <E extends Enum<E>> boolean isValidEnum(String value, Class<E> enumType) {
        if (Texts.isBlank(value) || enumType == null) {
            return false;
        }
        try {
            Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
