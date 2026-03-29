package emaki.jiuwu.craft.attribute.model;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.Locale;

public record ResourceDefinition(String id,
                                 String displayName,
                                 double defaultMax,
                                 double minMax,
                                 double maxMax,
                                 boolean syncToBukkit,
                                 boolean fullOnInit,
                                 double regenPerSecond) {

    public ResourceDefinition {
        id = normalizeId(id);
        displayName = Texts.isBlank(displayName) ? id : Texts.toStringSafe(displayName).trim();
    }

    public static ResourceDefinition fromMap(String fallbackId, Object raw) {
        if (raw == null) {
            return null;
        }
        return new ResourceDefinition(
            ConfigNodes.string(raw, "id", fallbackId),
            ConfigNodes.string(raw, "display_name", fallbackId),
            Numbers.tryParseDouble(ConfigNodes.get(raw, "default_max"), 0D),
            Numbers.tryParseDouble(ConfigNodes.get(raw, "min_max"), 0D),
            Numbers.tryParseDouble(ConfigNodes.get(raw, "max_max"), Double.MAX_VALUE),
            ConfigNodes.bool(raw, "sync_to_bukkit", false),
            ConfigNodes.bool(raw, "full_on_init", true),
            Numbers.tryParseDouble(ConfigNodes.get(raw, "regen_per_second"), 0D)
        );
    }

    public double clampMax(double value) {
        double result = value;
        result = Math.max(result, minMax);
        result = Math.min(result, maxMax);
        return result;
    }

    private static String normalizeId(String value) {
        return Texts.toStringSafe(value).trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
