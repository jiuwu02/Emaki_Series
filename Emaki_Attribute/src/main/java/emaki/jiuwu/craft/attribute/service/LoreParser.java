package emaki.jiuwu.craft.attribute.service;

import emaki.jiuwu.craft.attribute.loader.AttributeRegistry;
import emaki.jiuwu.craft.attribute.loader.LoreFormatRegistry;
import emaki.jiuwu.craft.attribute.model.AttributeDefinition;
import emaki.jiuwu.craft.attribute.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.model.LoreFormatDefinition;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.ChatColor;

public final class LoreParser {

    private final AttributeRegistry attributeRegistry;
    private final LoreFormatRegistry loreFormatRegistry;

    public LoreParser(AttributeRegistry attributeRegistry, LoreFormatRegistry loreFormatRegistry) {
        this.attributeRegistry = attributeRegistry;
        this.loreFormatRegistry = loreFormatRegistry;
    }

    public ParsedLore parse(Collection<String> loreLines) {
        List<String> normalized = normalizeLore(loreLines);
        Map<String, Double> values = new LinkedHashMap<>();
        for (String line : normalized) {
            AttributeDefinition definition = attributeRegistry.matchLine(line);
            if (definition == null) {
                continue;
            }
            String matched = attributeRegistry.extractMatchedValue(line, definition);
            double parsed = parseValue(matched, definition);
            values.merge(definition.id(), definition.clamp(parsed), Double::sum);
        }
        String signature = SignatureUtil.stableSignature(normalized);
        AttributeSnapshot snapshot = new AttributeSnapshot(AttributeSnapshot.CURRENT_SCHEMA_VERSION, signature, values, System.currentTimeMillis());
        return new ParsedLore(snapshot, normalized);
    }

    public List<String> render(AttributeDefinition definition, double value) {
        if (definition == null) {
            return List.of();
        }
        LoreFormatDefinition format = loreFormatRegistry == null ? null : loreFormatRegistry.get(definition.loreFormatId());
        String template = format == null ? "{sign}{value} {name}" : format.format();
        int precision = format == null ? 2 : Math.max(0, format.precision());
        String sign = value >= 0D ? "+" : "-";
        String pattern = precision <= 0 ? "0" : "0." + "#".repeat(Math.min(precision, 6));
        String formattedValue = Numbers.formatNumber(Math.abs(value), pattern);
        String unit = definition.isPercentLike() ? "%" : "";
        String rendered = template
            .replace("{sign}", sign)
            .replace("{value}", formattedValue)
            .replace("{name}", definition.displayName())
            .replace("{id}", definition.id())
            .replace("{unit}", unit);
        return List.of(rendered);
    }

    public List<String> normalizeLore(Collection<String> loreLines) {
        List<String> result = new ArrayList<>();
        if (loreLines == null) {
            return result;
        }
        for (Object entry : loreLines) {
            String line = Texts.toStringSafe(entry);
            line = ChatColor.stripColor(line);
            line = Texts.stripMiniTags(line);
            line = line == null ? "" : line.trim().replaceAll("\\s+", " ");
            if (!line.isBlank()) {
                result.add(line);
            }
        }
        return result;
    }

    private double parseValue(String raw, AttributeDefinition definition) {
        if (Texts.isBlank(raw)) {
            return 0D;
        }
        String cleaned = Texts.trim(raw).replace(",", "");
        if (definition.isPercentLike() && cleaned.endsWith("%")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        Double numeric = Numbers.tryParseDouble(cleaned, null);
        if (numeric != null) {
            return numeric;
        }
        return ExpressionEngine.evaluate(cleaned);
    }

    public record ParsedLore(AttributeSnapshot snapshot, List<String> normalizedLore) {
    }
}
