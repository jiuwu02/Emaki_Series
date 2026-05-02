package emaki.jiuwu.craft.corelib.item;

import java.util.Locale;
import java.util.regex.Pattern;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class ItemSourceUtil {

    private static final Pattern VANILLA_IDENTIFIER_PATTERN = Pattern.compile("[a-z0-9_]+");

    private ItemSourceUtil() {
    }

    public static ItemSource parse(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof ItemSource itemSource) {
            return itemSource;
        }
        if (raw instanceof String text) {
            return parseShorthand(text);
        }
        String item = ConfigNodes.string(raw, "item", null);
        if (Texts.isNotBlank(item)) {
            return parseShorthand(item);
        }
        Object nestedSource = ConfigNodes.get(raw, "source");
        if (nestedSource != null && nestedSource != raw) {
            ItemSource source = parse(nestedSource);
            if (source != null) {
                return source;
            }
        }
        String type = ConfigNodes.string(raw, "type", null);
        String identifier = ConfigNodes.string(raw, "identifier", null);
        if (Texts.isBlank(type) || Texts.isBlank(identifier)) {
            return null;
        }
        ItemSourceType sourceType = ItemSourceType.fromText(type);
        if (sourceType == null) {
            return null;
        }
        String normalizedIdentifier = normalizeIdentifier(sourceType, identifier);
        return Texts.isBlank(normalizedIdentifier) ? null : new ItemSource(sourceType, normalizedIdentifier);
    }

    public static ItemSource parseShorthand(String shorthand) {
        return ItemSourceRegistry.system().parseShorthand(shorthand);
    }

    public static ItemSource parseVanillaShorthand(String shorthand) {
        String identifier = normalizeVanillaIdentifier(shorthand);
        return Texts.isBlank(identifier) ? null : new ItemSource(ItemSourceType.VANILLA, identifier);
    }

    public static void registerParser(ItemSourceParser parser) {
        ItemSourceRegistry.system().registerParser(parser);
    }

    public static String toShorthand(ItemSource source) {
        if (source == null || source.getType() == null || Texts.isBlank(source.getIdentifier())) {
            return null;
        }
        return switch (source.getType()) {
            case MMOITEMS ->
                "mmoitems-" + source.getIdentifier();
            case ITEMSADDER ->
                "itemsadder-" + source.getIdentifier();
            case NEIGEITEMS ->
                "neigeitems-" + source.getIdentifier();
            case EMAKIITEM ->
                "emakiitem-" + source.getIdentifier();
            case NEXO ->
                "nexo-" + source.getIdentifier();
            case CRAFTENGINE ->
                "craftengine-" + source.getIdentifier();
            case VANILLA ->
                canonicalVanillaShorthand(source.getIdentifier());
        };
    }

    public static boolean matches(ItemSource left, ItemSource right) {
        if (left == null || right == null || left.getType() != right.getType()) {
            return false;
        }
        return normalizeIdentifier(left).equals(normalizeIdentifier(right));
    }

    private static String normalizeIdentifier(ItemSource source) {
        if (source == null || source.getType() == null) {
            return "";
        }
        return normalizeIdentifier(source.getType(), source.getIdentifier());
    }

    public static String normalizeIdentifier(ItemSourceType type, String identifier) {
        if (type == null) {
            return "";
        }
        return type == ItemSourceType.VANILLA
                ? normalizeVanillaIdentifier(identifier)
                : normalizeCustomIdentifier(identifier);
    }

    public static String normalizeVanillaIdentifier(String identifier) {
        String normalized = normalizeCustomIdentifier(identifier);
        if (Texts.isBlank(normalized)) {
            return "";
        }
        if (normalized.startsWith("minecraft-")) {
            normalized = normalized.substring("minecraft-".length());
        } else if (normalized.startsWith("mc-")) {
            normalized = normalized.substring("mc-".length());
        } else if (normalized.startsWith("v-")) {
            normalized = normalized.substring("v-".length());
        }
        if (normalized.startsWith("minecraft:") || normalized.contains(":")) {
            return "";
        }
        return VANILLA_IDENTIFIER_PATTERN.matcher(normalized).matches() ? normalized : "";
    }

    public static String normalizeCustomIdentifier(String identifier) {
        return Texts.toStringSafe(identifier).trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    public static String canonicalVanillaShorthand(String identifier) {
        String normalized = normalizeVanillaIdentifier(identifier);
        return Texts.isBlank(normalized) ? null : "minecraft-" + normalized;
    }

    public static Material resolveVanillaMaterial(String identifier) {
        String normalized = normalizeVanillaIdentifier(identifier);
        if (Texts.isBlank(normalized)) {
            return null;
        }
        NamespacedKey key = NamespacedKey.minecraft(normalized);
        return Registry.MATERIAL.get(key);
    }
}
