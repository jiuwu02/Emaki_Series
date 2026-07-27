package emaki.jiuwu.craft.storage.service;

import java.util.List;
import java.util.Locale;

import net.kyori.adventure.text.Component;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.storage.model.StorageEntry;
import emaki.jiuwu.craft.storage.model.StorageKey;

/**
 * Builds the pre-computed search and sort text carried by a {@link StorageEntry}.
 *
 * <p>Plain text goes through {@link MiniMessages#plainText(Object)} rather than
 * {@code Texts.stripMiniTags}: the latter only strips MiniMessage tags and would leave legacy
 * {@code §} colour codes inside the comparison key, so a coloured item name would never match a
 * plain search term.
 *
 * <p>Text is computed once per entry creation. Nothing is cached statically and no
 * {@code ThreadLocal} is used, because Folia region threads would each accumulate their own copy.
 */
public final class StorageTextIndexer {

    private final ItemSourceService itemSourceService;

    public StorageTextIndexer(ItemSourceService itemSourceService) {
        this.itemSourceService = itemSourceService;
    }

    /**
     * Builds a fully indexed entry.
     *
     * @param key        the normalised item key
     * @param amount     the initial amount
     * @param stackLimit the per-entry ceiling, {@code 0} meaning inherit
     * @return the entry with its search and sort text populated
     */
    public StorageEntry createEntry(StorageKey key, long amount, long stackLimit) {
        ItemStack template = key.toItemStack();
        String displayName = displayName(template);
        String lore = loreText(template);
        String identifier = identifier(key, template);
        String searchText = buildSearchText(displayName, lore, identifier);
        return new StorageEntry(key, amount, stackLimit, searchText, displayName);
    }

    /**
     * {@return the plain display name, falling back to the material key when unnamed}
     */
    public String displayName(ItemStack template) {
        ItemMeta meta = template.hasItemMeta() ? template.getItemMeta() : null;
        if (meta != null) {
            Component named = meta.hasDisplayName() ? meta.displayName() : null;
            if (named == null && meta.hasItemName()) {
                named = meta.itemName();
            }
            if (named != null) {
                String plain = MiniMessages.plainText(named);
                if (plain != null && !plain.isBlank()) {
                    return plain.trim();
                }
            }
        }
        return template.getType().getKey().value();
    }

    /** {@return the plain concatenated lore, or an empty string when there is none} */
    public String loreText(ItemStack template) {
        ItemMeta meta = template.hasItemMeta() ? template.getItemMeta() : null;
        if (meta == null || !meta.hasLore()) {
            return "";
        }
        List<Component> lore = meta.lore();
        if (lore == null || lore.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (Component line : lore) {
            String plain = MiniMessages.plainText(line);
            if (plain != null && !plain.isBlank()) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(plain.trim());
            }
        }
        return text.toString();
    }

    /**
     * {@return the stable identifier used by {@code $}-scoped search and by the flow log}
     *
     * <p>Prefers the CoreLib ItemSource id so an EmakiItem instance is identified by its own id
     * rather than its underlying material; falls back to the material key.
     */
    public String identifier(StorageKey key, ItemStack template) {
        if (itemSourceService != null) {
            try {
                ItemSource source = itemSourceService.identifyItem(template);
                if (source != null && source.getIdentifier() != null && !source.getIdentifier().isBlank()) {
                    return source.getIdentifier().toLowerCase(Locale.ROOT);
                }
            } catch (RuntimeException ignored) {
                // A resolver that is not ready must never block indexing.
            }
        }
        return key.material().getKey().value();
    }

    /** {@return the identifier for an entry, resolved from its key} */
    public String identifierOf(StorageKey key) {
        return identifier(key, key.toItemStack());
    }

    /**
     * Composes the combined lower-cased search text.
     *
     * <p>Layout is {@code name \u0000 lore \u0000 identifier}; the scoped search modes slice this
     * back apart so only one string has to be stored per entry.
     */
    private String buildSearchText(String displayName, String lore, String identifier) {
        return (displayName + '\u0000' + lore + '\u0000' + identifier).toLowerCase(Locale.ROOT);
    }

    /** {@return the name section of a composed search text} */
    public static String namePart(String searchText) {
        int first = searchText.indexOf('\u0000');
        return first < 0 ? searchText : searchText.substring(0, first);
    }

    /** {@return the lore section of a composed search text} */
    public static String lorePart(String searchText) {
        int first = searchText.indexOf('\u0000');
        if (first < 0) {
            return "";
        }
        int second = searchText.indexOf('\u0000', first + 1);
        return second < 0 ? searchText.substring(first + 1) : searchText.substring(first + 1, second);
    }

    /** {@return the identifier section of a composed search text} */
    public static String idPart(String searchText) {
        int first = searchText.indexOf('\u0000');
        if (first < 0) {
            return "";
        }
        int second = searchText.indexOf('\u0000', first + 1);
        return second < 0 ? "" : searchText.substring(second + 1);
    }

    /**
     * {@return the whole composed text with separators flattened, used by the unscoped match}
     *
     * <p>This deliberately includes the identifier section, so an unprefixed term also matches a
     * material key or ItemSource id.
     */
    public static String anyPart(String searchText) {
        return searchText.replace('\u0000', ' ');
    }
}
