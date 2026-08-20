package emaki.jiuwu.craft.item.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.item.api.ItemStateType;

public final class ItemStateConfigParser {

    private ItemStateConfigParser() {
    }

    public static ItemStateConfig parse(YamlSection section) {
        if (section == null || section.isEmpty()) {
            return ItemStateConfig.defaults();
        }
        ItemStateConfig fallback = ItemStateConfig.defaults();
        return new ItemStateConfig(
                section.getBoolean("clamp", fallback.clampEnabled()),
                section.getBoolean("fill_defaults", fallback.fillDefaults()),
                parseFields(section.getSection("fields")),
                parseMigrations(section.getSection("migrations")),
                parsePreservation(section.getSection("preservation")),
                parseDerivation(section.getSection("derivation")));
    }

    private static Map<String, ItemStateConfig.Field> parseFields(YamlSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, ItemStateConfig.Field> fields = new LinkedHashMap<>();
        for (String rawKey : section.getKeys(false)) {
            YamlSection entry = section.getSection(rawKey);
            if (entry == null) {
                continue;
            }
            ItemStateType type = parseType(entry.getString("type", "double"));
            if (type == null) {
                continue;
            }
            String key = Texts.toStringSafe(rawKey).trim().toLowerCase(Locale.ROOT);
            fields.put(key, new ItemStateConfig.Field(
                    key,
                    type,
                    type.coerce(entry.get("default")),
                    decimal(entry.get("min")),
                    decimal(entry.get("max")),
                    parseThresholds(entry.getSection("thresholds"))));
        }
        return fields;
    }

    private static List<ItemStateConfig.Threshold> parseThresholds(YamlSection section) {
        if (section == null) {
            return List.of();
        }
        List<ItemStateConfig.Threshold> thresholds = new ArrayList<>();
        for (String rawId : section.getKeys(false)) {
            if (thresholds.size() >= ItemStateConfig.MAX_THRESHOLDS_PER_FIELD) {
                break;
            }
            YamlSection entry = section.getSection(rawId);
            if (entry == null) {
                continue;
            }
            BigDecimal value = decimal(entry.get("value"));
            if (value == null) {
                continue;
            }
            thresholds.add(new ItemStateConfig.Threshold(
                    rawId,
                    value,
                    entry.getBoolean("once", false),
                    entry.getBoolean("reward_on_fall", false),
                    entry.getStringList("actions"),
                    entry.getString("message_key", ""),
                    entry.getString("sound", ""),
                    entry.getDouble("sound_volume", 1D).floatValue(),
                    entry.getDouble("sound_pitch", 1D).floatValue(),
                    entry.getBoolean("refresh_derived", true)));
        }
        thresholds.sort((left, right) -> left.value().compareTo(right.value()));
        return thresholds;
    }

    private static List<ItemStateConfig.Migration> parseMigrations(YamlSection section) {
        if (section == null) {
            return List.of();
        }
        List<ItemStateConfig.Migration> migrations = new ArrayList<>();
        for (String rawKey : section.getKeys(false)) {
            YamlSection entry = section.getSection(rawKey);
            if (entry == null) {
                continue;
            }
            Integer from = entry.getInt("from", null);
            Integer to = entry.getInt("to", null);
            if (from == null || to == null || to <= from) {
                continue;
            }
            migrations.add(new ItemStateConfig.Migration(
                    from,
                    to,
                    parseRenames(entry.getSection("rename")),
                    parseRetypes(entry.getSection("retype")),
                    normalizedList(entry.getStringList("drop"))));
        }
        migrations.sort((left, right) -> Integer.compare(left.fromVersion(), right.fromVersion()));
        return migrations;
    }

    private static Map<String, String> parseRenames(YamlSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, String> renames = new LinkedHashMap<>();
        for (String rawKey : section.getKeys(false)) {
            String target = Texts.toStringSafe(section.getString(rawKey, "")).trim().toLowerCase(Locale.ROOT);
            String source = Texts.toStringSafe(rawKey).trim().toLowerCase(Locale.ROOT);
            if (!source.isBlank() && !target.isBlank() && !source.equals(target)) {
                renames.put(source, target);
            }
        }
        return renames;
    }

    private static Map<String, ItemStateType> parseRetypes(YamlSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, ItemStateType> retypes = new LinkedHashMap<>();
        for (String rawKey : section.getKeys(false)) {
            ItemStateType type = parseType(section.getString(rawKey, ""));
            String source = Texts.toStringSafe(rawKey).trim().toLowerCase(Locale.ROOT);
            if (type != null && !source.isBlank()) {
                retypes.put(source, type);
            }
        }
        return retypes;
    }

    private static ItemStateConfig.Preservation parsePreservation(YamlSection section) {
        ItemStateConfig.Preservation fallback = ItemStateConfig.Preservation.defaults();
        if (section == null) {
            return fallback;
        }
        return new ItemStateConfig.Preservation(
                section.getBoolean("verify_rebuild", fallback.verifyRebuild()),
                section.getBoolean("repair_on_pickup", fallback.repairOnPickup()),
                section.getBoolean("repair_on_drop", fallback.repairOnDrop()),
                section.getBoolean("repair_on_trade", fallback.repairOnTrade()),
                section.getBoolean("repair_on_container_transfer", fallback.repairOnContainerTransfer()),
                section.getBoolean("repair_on_join", fallback.repairOnJoin()));
    }

    private static ItemStateConfig.Derivation parseDerivation(YamlSection section) {
        ItemStateConfig.Derivation fallback = ItemStateConfig.Derivation.defaults();
        if (section == null) {
            return fallback;
        }
        return new ItemStateConfig.Derivation(
                section.getBoolean("enabled", fallback.enabled()),
                section.getBoolean("refresh_lore", fallback.refreshLore()),
                section.getBoolean("refresh_attributes", fallback.refreshAttributes()),
                section.getBoolean("scan_holder", fallback.scanHolder()),
                section.getInt("max_depth", fallback.maxDepth()));
    }

    private static List<String> normalizedList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>(values.size());
        for (String value : values) {
            String candidate = Texts.toStringSafe(value).trim().toLowerCase(Locale.ROOT);
            if (!candidate.isBlank()) {
                normalized.add(candidate);
            }
        }
        return List.copyOf(normalized);
    }

    private static ItemStateType parseType(String raw) {
        String normalized = Texts.toStringSafe(raw).trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return null;
        }
        try {
            return ItemStateType.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        try {
            return new BigDecimal(Texts.toStringSafe(value).trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
