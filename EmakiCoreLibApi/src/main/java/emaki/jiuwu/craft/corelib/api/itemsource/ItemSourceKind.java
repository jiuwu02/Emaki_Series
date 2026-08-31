package emaki.jiuwu.craft.corelib.api.itemsource;

import java.util.Locale;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Extensible item-source kind in {@code namespace:id} form.
 *
 * <p>The provider that supplies a kind also owns its shorthand prefixes. Built-in constants use the
 * {@code emaki} namespace; third-party providers should use their own namespace.
 */
public record ItemSourceKind(@NotNull String namespace, @NotNull String id) {

    /** Namespace used by the kinds CoreLib implements itself. */
    public static final String EMAKI_NAMESPACE = "emaki";

    /** Vanilla Minecraft materials. Also the fallback for bare text such as {@code IRON_INGOT}. */
    public static final ItemSourceKind VANILLA = new ItemSourceKind(EMAKI_NAMESPACE, "vanilla");

    /** CraftEngine custom items. */
    public static final ItemSourceKind CRAFTENGINE = new ItemSourceKind(EMAKI_NAMESPACE, "craftengine");

    /** ItemsAdder custom items. */
    public static final ItemSourceKind ITEMSADDER = new ItemSourceKind(EMAKI_NAMESPACE, "itemsadder");

    /** NeigeItems custom items. */
    public static final ItemSourceKind NEIGEITEMS = new ItemSourceKind(EMAKI_NAMESPACE, "neigeitems");

    /** MMOItems custom items. */
    public static final ItemSourceKind MMOITEMS = new ItemSourceKind(EMAKI_NAMESPACE, "mmoitems");

    /** Nexo custom items. */
    public static final ItemSourceKind NEXO = new ItemSourceKind(EMAKI_NAMESPACE, "nexo");

    /** Oraxen custom items. */
    public static final ItemSourceKind ORAXEN = new ItemSourceKind(EMAKI_NAMESPACE, "oraxen");

    /** EcoItems custom items. */
    public static final ItemSourceKind ECOITEMS = new ItemSourceKind(EMAKI_NAMESPACE, "ecoitems");

    /**
     * Normalises both segments with {@link Locale#ROOT}.
     *
     * @throws IllegalArgumentException when {@code id} is blank or either segment contains {@code :}
     */
    public ItemSourceKind {
        namespace = namespace == null || namespace.isBlank()
                ? EMAKI_NAMESPACE
                : normalize(namespace, "namespace");
        id = normalize(id, "id");
    }

    /** {@return the canonical {@code namespace:id} form} */
    public @NotNull String key() {
        return namespace + ':' + id;
    }

    /**
     * Parses a kind identifier.
     *
     * @param key either {@code namespace:id}, or a bare {@code id} which is given the
     *            {@code emaki} namespace
     * @return the parsed kind
     * @throws IllegalArgumentException when {@code key} is blank or carries more than one {@code :}
     */
    public static @NotNull ItemSourceKind of(@Nullable String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("item source kind must not be blank");
        }
        String trimmed = key.trim();
        int separator = trimmed.indexOf(':');
        if (separator < 0) {
            return new ItemSourceKind(EMAKI_NAMESPACE, trimmed);
        }
        if (trimmed.indexOf(':', separator + 1) >= 0) {
            throw new IllegalArgumentException("item source kind must be \"namespace:id\": " + key);
        }
        return new ItemSourceKind(trimmed.substring(0, separator), trimmed.substring(separator + 1));
    }

    /** {@return whether this kind is one CoreLib implements itself} */
    public boolean builtin() {
        return EMAKI_NAMESPACE.equals(namespace);
    }

    @Override
    public String toString() {
        return key();
    }

    private static String normalize(String segment, String label) {
        if (segment == null || segment.isBlank()) {
            throw new IllegalArgumentException("item source kind " + label + " must not be blank");
        }
        String normalized = segment.trim().toLowerCase(Locale.ROOT);
        if (normalized.indexOf(':') >= 0) {
            throw new IllegalArgumentException("item source kind " + label + " must not contain ':': " + segment);
        }
        return normalized;
    }
}
