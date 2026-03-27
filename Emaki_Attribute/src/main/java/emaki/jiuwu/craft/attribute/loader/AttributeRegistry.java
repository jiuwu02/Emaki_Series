package emaki.jiuwu.craft.attribute.loader;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.model.AttributeDefinition;
import emaki.jiuwu.craft.attribute.model.AttributeTargetType;
import emaki.jiuwu.craft.attribute.model.AttributeValueKind;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.text.Texts;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.bukkit.configuration.file.YamlConfiguration;

public final class AttributeRegistry extends DirectoryLoader<AttributeDefinition> {

    private final Map<String, AttributeDefinition> aliasIndex = new LinkedHashMap<>();
    private final List<PatternEntry> orderedPatterns = new ArrayList<>();

    private record PatternEntry(AttributeDefinition definition, Pattern pattern) {
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
            for (String alias : definition.aliases()) {
                aliasIndex.putIfAbsent(normalizeId(alias), definition);
            }
            for (String pattern : definition.lorePatterns()) {
                try {
                    orderedPatterns.add(new PatternEntry(definition, Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)));
                } catch (Exception exception) {
                    issue(
                        "loader.invalid_lore_pattern",
                        Map.of(
                            "attribute", definition.id(),
                            "pattern", pattern,
                            "error", Texts.toStringSafe(exception.getMessage())
                        )
                    );
                }
            }
            for (Pattern fallback : fallbackPatterns(definition)) {
                orderedPatterns.add(new PatternEntry(definition, fallback));
            }
        }
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
                for (int group = 1; group <= matcher.groupCount(); group++) {
                    String value = matcher.group(group);
                    if (!Texts.isBlank(value)) {
                        return value;
                    }
                }
                return matcher.group();
            }
        }
        return null;
    }

    private List<Pattern> fallbackPatterns(AttributeDefinition definition) {
        if (definition == null || definition.lorePatterns().isEmpty()) {
            return List.of();
        }
        String name = Pattern.quote(definition.displayName());
        String numeric = "([+-]?\\d+(?:\\.\\d+)?)";
        String suffix = switch (definition.loreFormatId()) {
            case "default_percent" -> "%";
            case "default_regen" -> "/秒";
            default -> "";
        };
        String keyFirst = switch (definition.loreFormatId()) {
            case "default_percent" -> "^" + name + "\\s*" + numeric + suffix + "$";
            case "default_regen" -> "^" + name + "\\s*" + numeric + suffix + "$";
            case "default_resource" -> "^" + name + "\\s*" + numeric + "$";
            default -> "^" + name + "\\s*" + numeric + "$";
        };
        String valueFirst = switch (definition.loreFormatId()) {
            case "default_percent" -> "^" + numeric + suffix + "\\s*" + name + "$";
            case "default_regen" -> "^" + numeric + "\\s*" + name + "/秒$";
            case "default_resource" -> "^" + numeric + "\\s*" + name + "$";
            default -> "^" + numeric + "\\s*" + name + "$";
        };
        try {
            return List.of(
                Pattern.compile(keyFirst, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                Pattern.compile(valueFirst, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
            );
        } catch (Exception exception) {
            return List.of();
        }
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
}
