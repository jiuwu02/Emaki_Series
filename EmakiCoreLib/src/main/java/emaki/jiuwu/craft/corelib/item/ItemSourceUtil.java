package emaki.jiuwu.craft.corelib.item;

import java.util.Locale;
import java.util.regex.Pattern;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceKind;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * Static entry points for reading item sources out of config and writing them back.
 *
 * <p>{@link #toShorthand(ItemSourceRef)} now asks the owning provider instead of running an exhaustive
 * {@code switch} over an enum. That switch was the reason adding an item source kind broke CoreLib's own
 * compilation, and it is why the kind is a record now.
 *
 * <p><strong>The shorthand text format is unchanged.</strong> {@code minecraft-iron_ingot},
 * {@code emakiitem-mystic_dust} and {@code ce-namespace:id} all parse exactly as before, so server
 * config needs no edits.
 */
public final class ItemSourceUtil {

    private static final Pattern VANILLA_IDENTIFIER_PATTERN = Pattern.compile("[a-z0-9_]+");

    private ItemSourceUtil() {
    }

    /**
     * Reads an item source out of an arbitrary config node.
     *
     * @param raw a shorthand string, a list, or a map carrying {@code item} / {@code source} /
     *            {@code item_sources}, or an explicit {@code kind} + {@code identifier} pair
     * @return the reference, or {@code null} when nothing usable was found
     */
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

    /**
     * Reads the explicit {@code type}/{@code kind} + {@code identifier} form.
     *
     * <p>{@code type} is still accepted alongside {@code kind} so existing config keeps working; the
     * value is now resolved through the prefix table rather than an enum's own {@code fromText}, which
     * means a third-party kind works here as well.
     */
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

    /**
     * Resolves a kind from config text.
     *
     * <p>Accepts a canonical {@code namespace:id}, and also a bare name or short alias by reusing the
     * provider prefix table: {@code craftengine} and {@code ce} both resolve because some provider
     * claims {@code craftengine-} and {@code ce-}. That keeps the alias list in exactly one place.
     *
     * @param text the config text
     * @return the kind, or {@code null} when nothing claims it
     */
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
        // A bare name is matched by pretending it is a prefixed shorthand with a throwaway identifier.
        ItemSourceRef probe = ItemSourceRegistry.system().parseShorthand(trimmed + "-x");
        if (probe != null && !probe.vanilla()) {
            return probe.kind();
        }
        return switch (Texts.lower(trimmed)) {
            case "vanilla", "minecraft", "mc", "v" -> ItemSourceKind.VANILLA;
            default -> null;
        };
    }

    /**
     * Parses shorthand text.
     *
     * @param shorthand the shorthand text
     * @return the reference, or {@code null} when nothing claims it
     */
    public static ItemSourceRef parseShorthand(String shorthand) {
        return ItemSourceRegistry.system().parseShorthand(shorthand);
    }

    /**
     * Parses bare vanilla text such as {@code IRON_INGOT}.
     *
     * @param shorthand the material id, with or without a vanilla prefix
     * @return the reference, or {@code null} when it is not a plain material id
     */
    public static ItemSourceRef parseVanillaShorthand(String shorthand) {
        String identifier = normalizeVanillaIdentifier(shorthand);
        return Texts.isBlank(identifier) ? null : ItemSourceRef.orNull(ItemSourceKind.VANILLA, identifier);
    }

    /**
     * Writes a reference back into shorthand text.
     *
     * @param ref the reference
     * @return the shorthand, or {@code null} when no provider claims the kind
     */
    public static String toShorthand(ItemSourceRef ref) {
        if (ref == null) {
            return null;
        }
        String registered = ItemSourceRegistry.system().toShorthand(ref);
        if (Texts.isNotBlank(registered)) {
            return registered;
        }
        // No provider claims the kind: the only shorthand still safe to emit is the vanilla canonical
        // form, because CoreLib owns that reading itself.
        return ref.vanilla() ? canonicalVanillaShorthand(ref.identifier()) : null;
    }

    /**
     * @param left first reference
     * @param right second reference
     * @return whether both denote the same item source
     */
    public static boolean matches(ItemSourceRef left, ItemSourceRef right) {
        return left != null && right != null && left.equals(right);
    }

    /**
     * Normalises an identifier for one kind.
     *
     * @param kind the owning kind
     * @param identifier the raw identifier
     * @return the normalised identifier, or an empty string when it is unusable
     */
    public static String normalizeIdentifier(ItemSourceKind kind, String identifier) {
        if (kind == null) {
            return "";
        }
        return ItemSourceKind.VANILLA.equals(kind)
                ? normalizeVanillaIdentifier(identifier)
                : normalizeCustomIdentifier(identifier);
    }

    /**
     * Normalises a vanilla material id, rejecting namespaced input.
     *
     * @param identifier the raw identifier
     * @return the normalised id, or an empty string when it is not a plain material id
     */
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

    /**
     * @param identifier the raw identifier
     * @return the identifier trimmed, lower-cased and with spaces turned into underscores
     */
    public static String normalizeCustomIdentifier(String identifier) {
        return Texts.toStringSafe(identifier).trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    /**
     * @param identifier the raw material id
     * @return the canonical {@code minecraft-<id>} form, or {@code null} when the id is unusable
     */
    public static String canonicalVanillaShorthand(String identifier) {
        String normalized = normalizeVanillaIdentifier(identifier);
        return Texts.isBlank(normalized) ? null : "minecraft-" + normalized;
    }

    /**
     * @param identifier the raw material id
     * @return the material, or {@code null} when no such material exists
     */
    public static Material resolveVanillaMaterial(String identifier) {
        String normalized = normalizeVanillaIdentifier(identifier);
        if (Texts.isBlank(normalized)) {
            return null;
        }
        NamespacedKey key = NamespacedKey.minecraft(normalized);
        return Registry.MATERIAL.get(key);
    }
}
