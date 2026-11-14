package emaki.jiuwu.craft.item.service;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.api.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.item.model.ItemSetDefinition;

final class ItemSetPresentationProjector {

    private final EmakiItemIdentifier identifier;

    ItemSetPresentationProjector(EmakiItemIdentifier identifier) {
        this.identifier = identifier;
    }

    List<String> baseLore(ItemStack itemStack, ItemSetDefinition setDefinition) {
        List<String> stored = identifier.baseLore(itemStack);
        if (stored != null) {
            return stored;
        }
        List<String> current = loreLines(itemStack);
        if (setDefinition == null || Texts.isBlank(setDefinition.displayLabel())) {
            return current;
        }
        String label = setDefinition.displayLabel();
        int firstHeader = -1;
        for (int index = 0; index < current.size(); index++) {
            if (Texts.toStringSafe(current.get(index)).contains(label)) {
                firstHeader = index;
                break;
            }
        }
        if (firstHeader < 0) {
            return current;
        }
        int cut = firstHeader;
        if (cut > 0 && MiniMessages.plainText(Texts.toStringSafe(current.get(cut - 1))).isBlank()) {
            cut--;
        }
        return List.copyOf(new ArrayList<>(current.subList(0, cut)));
    }

    List<String> storedBaseLore(ItemStack itemStack) {
        List<String> stored = identifier.baseLore(itemStack);
        return stored == null ? loreLines(itemStack) : stored;
    }

    List<String> render(List<String> baseLore, List<String> setLore) {
        List<String> canonical = canonicalLoreLines(setLore);
        List<String> projected = baseLore == null || baseLore.isEmpty()
                ? new ArrayList<>()
                : new ArrayList<>(baseLore);
        if (canonical.isEmpty()) {
            return List.copyOf(projected);
        }
        if (!projected.isEmpty()) {
            projected.add("");
        }
        projected.addAll(canonical);
        return List.copyOf(projected);
    }

    void commit(ItemStack itemStack, List<String> projected, List<String> baseLore) {
        identifier.writeBaseLore(itemStack, baseLore);
        setLore(itemStack, projected);
    }

    void setLore(ItemStack itemStack, List<String> lore) {
        ItemMeta itemMeta = itemStack == null ? null : itemStack.getItemMeta();
        if (itemMeta == null) {
            return;
        }
        ItemTextBridge.setLoreLines(itemMeta, lore);
        itemStack.setItemMeta(itemMeta);
    }

    List<String> loreLines(ItemStack itemStack) {
        ItemMeta itemMeta = itemStack == null ? null : itemStack.getItemMeta();
        List<String> lore = itemMeta == null ? null : ItemTextBridge.loreLines(itemMeta);
        return lore == null || lore.isEmpty() ? List.of() : List.copyOf(lore);
    }

    private List<String> canonicalLoreLines(List<String> lore) {
        if (lore == null || lore.isEmpty()) {
            return List.of();
        }
        List<String> canonical = new ArrayList<>(lore.size());
        for (String line : lore) {
            canonical.add(ItemSetPresentationCalculatorSupport.canonicalLoreLine(line));
        }
        return List.copyOf(canonical);
    }
}
