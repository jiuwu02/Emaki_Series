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
        return "attribute";
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
            configuration.getString("description")
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
                    issue("Invalid lore pattern for attribute " + definition.id() + ": " + pattern + " (" + exception.getMessage() + ")");
                }
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
                if (matcher.groupCount() >= 1) {
                    return matcher.group(1);
                }
                return matcher.group();
            }
        }
        return null;
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
