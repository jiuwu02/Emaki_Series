package emaki.jiuwu.craft.storage.service;

import java.util.List;
import java.util.Locale;

import net.kyori.adventure.text.Component;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.VanillaTranslationService;
import emaki.jiuwu.craft.storage.model.StorageEntry;
import emaki.jiuwu.craft.storage.model.StorageKey;

public final class StorageTextIndexer {

    private final ItemSourceService itemSourceService;
    private final VanillaTranslationService translationService;

    public StorageTextIndexer(ItemSourceService itemSourceService,
            VanillaTranslationService translationService) {
        this.itemSourceService = itemSourceService;
        this.translationService = translationService;
    }

    public StorageEntry createEntry(StorageKey key, long amount, long stackLimit) {
        ItemStack template = key.toItemStack();
        String displayName = displayName(template);
        String lore = loreText(template);
        String identifier = identifier(key, template);
        String searchText = buildSearchText(displayName, searchableName(template, displayName), lore, identifier);
        return new StorageEntry(key, amount, stackLimit, searchText, displayName);
    }

    private String searchableName(ItemStack template, String displayName) {
        if (translationService == null || !translationService.isAvailable()) {
            return displayName;
        }
        String localized = translationService.translateMaterial(template.getType());
        if (localized == null || localized.isBlank() || localized.equalsIgnoreCase(displayName)) {
            return displayName;
        }
        return displayName + ' ' + localized.trim();
    }

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

    public String identifier(StorageKey key, ItemStack template) {
        if (itemSourceService != null) {
            try {
                ItemSourceRef source = itemSourceService.identifyItem(template);
                if (source != null && source.identifier() != null && !source.identifier().isBlank()) {
                    return source.identifier().toLowerCase(Locale.ROOT);
                }
            } catch (RuntimeException ignored) {

            }
        }
        return key.material().getKey().value();
    }

    public String identifierOf(StorageKey key) {
        return identifier(key, key.toItemStack());
    }

    private String buildSearchText(String displayName, String searchableName, String lore, String identifier) {
        String name = searchableName == null || searchableName.isBlank() ? displayName : searchableName;
        return (name + '\u0000' + lore + '\u0000' + identifier).toLowerCase(Locale.ROOT);
    }

    public static String namePart(String searchText) {
        int first = searchText.indexOf('\u0000');
        return first < 0 ? searchText : searchText.substring(0, first);
    }

    public static String lorePart(String searchText) {
        int first = searchText.indexOf('\u0000');
        if (first < 0) {
            return "";
        }
        int second = searchText.indexOf('\u0000', first + 1);
        return second < 0 ? searchText.substring(first + 1) : searchText.substring(first + 1, second);
    }

    public static String idPart(String searchText) {
        int first = searchText.indexOf('\u0000');
        if (first < 0) {
            return "";
        }
        int second = searchText.indexOf('\u0000', first + 1);
        return second < 0 ? "" : searchText.substring(second + 1);
    }

    public static String anyPart(String searchText) {
        return searchText.replace('\u0000', ' ');
    }
}
