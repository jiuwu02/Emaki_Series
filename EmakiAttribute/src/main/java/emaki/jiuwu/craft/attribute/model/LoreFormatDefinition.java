package emaki.jiuwu.craft.attribute.model;

import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;

public record LoreFormatDefinition(String id,
        String format,
        int precision,
        int readPriority,
        List<String> readPatterns) {

    public LoreFormatDefinition     {
        id = Texts.normalizeId(id);
        format = Texts.isBlank(format) ? "{sign}{value} {name}" : Texts.toStringSafe(format).trim();
        readPatterns = readPatterns == null ? List.of() : List.copyOf(readPatterns);
    }

    public static LoreFormatDefinition fromMap(Object raw) {
        if (raw == null) {
            return null;
        }
        List<String> readPatterns = new ArrayList<>();
        appendPatterns(readPatterns, ConfigNodes.get(raw, "read_patterns"));
        appendPatterns(readPatterns, ConfigNodes.get(raw, "read_pattern"));
        appendPatterns(readPatterns, ConfigNodes.get(raw, "lore_patterns"));
        appendPatterns(readPatterns, ConfigNodes.get(raw, "lore_pattern"));
        return new LoreFormatDefinition(
                ConfigNodes.string(raw, "id", null),
                ConfigNodes.string(raw, "format", null),
                Numbers.tryParseInt(ConfigNodes.get(raw, "precision"), 2),
                Numbers.tryParseInt(ConfigNodes.get(raw, "read_priority"), defaultReadPriority(ConfigNodes.string(raw, "id", null))),
                readPatterns
        );
    }
private static void appendPatterns(List<String> target, Object raw) {
        if (target == null || raw == null) {
            return;
        }
        for (Object entry : ConfigNodes.asObjectList(raw)) {
            String pattern = Texts.toStringSafe(entry).trim();
            if (!pattern.isBlank()) {
                target.add(pattern);
            }
        }
    }

    private static int defaultReadPriority(String id) {
        String normalized = Texts.normalizeId(id);
        return switch (normalized) {
            case "default_percent" ->
                100;
            case "default_regen" ->
                80;
            case "default_resource" ->
                60;
            case "default_flat" ->
                50;
            default ->
                0;
        };
    }
}

