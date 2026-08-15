package emaki.jiuwu.craft.corelib.item;

import java.util.Locale;
import java.util.regex.Pattern;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceKind;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class ItemSourceUtil {

    private static final Pattern VANILLA_IDENTIFIER_PATTERN = Pattern.compile("[a-z0-9_]+");

    private ItemSourceUtil() {
    }

    public static ItemSourceRef parse(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof ItemSourceRef ref) {
            return ref;
        }
        if (raw instanceof String text) {
            return parseShorthand(text);
        }
        for (Object entry : ConfigNodes.asObjectList(raw)) {
            if (entry == raw) {
                continue;
            }
            ItemSourceRef ref = parse(entry);
            if (ref != null) {
                return ref;
            }
        }
        Object itemSources = ConfigNodes.get(raw, "item_sources");
        for (Object entry : ConfigNodes.asObjectList(itemSources)) {
            ItemSourceRef ref = parse(entry);
            if (ref != null) {
                return ref;
            }
        }
        String item = ConfigNodes.string(raw, "item", null);
        if (Texts.isNotBlank(item)) {
            return parseShorthand(item);
        }
        Object nestedSource = ConfigNodes.get(raw, "source");
        if (nestedSource != null && nestedSource != raw) {
            ItemSourceRef ref = parse(nestedSource);
            if (ref != null) {
                return ref;
            }
        }
        return parseExplicit(raw);
    }

    private static ItemSourceRef parseExplicit(Object raw) {
        String kindText = ConfigNodes.string(raw, "kind", null);
        if (Texts.isBlank(kindText)) {
            kindText = ConfigNodes.string(raw, "type", null);
        }
        String identifier = ConfigNodes.string(raw, "identifier", null);
        if (Texts.isBlank(kindText) || Texts.isBlank(identifier)) {
            return null;
        }
        ItemSourceKind kind = resolveKind(kindText);
        if (kind == null) {
            return null;
        }
        String normalized = normalizeIdentifier(kind, identifier);
        return Texts.isBlank(normalized) ? null : ItemSourceRef.orNull(kind, normalized);
    }

    public static ItemSourceKind resolveKind(String text) {
        if (Texts.isBlank(text)) {
            return null;
        }
        String trimmed = Texts.trim(text);
        if (trimmed.indexOf(':') >= 0) {
            try {
                ItemSourceKind parsed = ItemSourceKind.of(trimmed);
                return ItemSourceRegistry.system().claims(parsed) ? parsed : null;
            } catch (IllegalArgumentException malformed) {
                return null;
            }
        }

        ItemSourceRef probe = ItemSourceRegistry.system().parseShorthand(trimmed + "-x");
        if (probe != null && !probe.vanilla()) {
            return probe.kind();
        }
        return switch (Texts.lower(trimmed)) {
            case "vanilla", "minecraft", "mc", "v" -> ItemSourceKind.VANILLA;
            default -> null;
        };
    }

    public static ItemSourceRef parseShorthand(String shorthand) {
        return ItemSourceRegistry.system().parseShorthand(shorthand);
    }

    public static ItemSourceRef parseVanillaShorthand(String shorthand) {
        String identifier = normalizeVanillaIdentifier(shorthand);
        return Texts.isBlank(identifier) ? null : ItemSourceRef.orNull(ItemSourceKind.VANILLA, identifier);
    }

    public static String toShorthand(ItemSourceRef ref) {
        if (ref == null) {
            return null;
        }
        String registered = ItemSourceRegistry.system().toShorthand(ref);
        if (Texts.isNotBlank(registered)) {
            return registered;
        }

        return ref.vanilla() ? canonicalVanillaShorthand(ref.identifier()) : null;
    }

    public static boolean matches(ItemSourceRef left, ItemSourceRef right) {
        return left != null && right != null && left.equals(right);
    }

    public static String normalizeIdentifier(ItemSourceKind kind, String identifier) {
        if (kind == null) {
            return "";
        }
        return ItemSourceKind.VANILLA.equals(kind)
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
