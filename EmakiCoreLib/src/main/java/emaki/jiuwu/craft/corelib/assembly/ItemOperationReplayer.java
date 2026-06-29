package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.text.Texts;
import net.kyori.adventure.text.Component;

final class ItemOperationReplayer {

    void replay(ItemStack itemStack, List<ItemOperationEntry> entries) {
        if (itemStack == null || itemStack.getType().isAir() || entries == null || entries.isEmpty()) {
            return;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return;
        }
        // Lore is committed via itemMeta first; the name is then injected as a
        // component so a translatable base name survives instead of being
        // flattened to a legacy string.
        boolean loreChanged = replayLoreOperations(itemMeta, entries);
        if (loreChanged) {
            itemStack.setItemMeta(itemMeta);
        }
        replayNameOperations(itemStack, entries);
    }

    private void replayNameOperations(ItemStack itemStack, List<ItemOperationEntry> entries) {
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return;
        }
        List<ItemOperationEntry.NameOperationRecord> nameRecords = new ArrayList<>();
        for (ItemOperationEntry entry : entries) {
            if (entry == null || entry.nameRecords() == null || entry.nameRecords().isEmpty()) {
                continue;
            }
            for (ItemOperationEntry.NameOperationRecord record : entry.nameRecords()) {
                if (record != null) {
                    nameRecords.add(record);
                }
            }
        }
        if (nameRecords.isEmpty()) {
            return;
        }
        Component baseName = LedgerNameComposer.resolveBaseName(itemStack, itemMeta);
        Component result = LedgerNameComposer.composeFromRecords(baseName, nameRecords);
        LedgerNameComposer.writeName(itemStack, itemMeta, result);
    }

    private boolean replayLoreOperations(ItemMeta itemMeta, List<ItemOperationEntry> entries) {
        boolean hasRecords = false;
        List<String> currentLore = new ArrayList<>();
        List<String> existingLore = ItemTextBridge.loreLines(itemMeta);
        if (existingLore != null) {
            currentLore.addAll(existingLore);
        }
        for (ItemOperationEntry entry : entries) {
            if (entry == null || entry.loreRecords() == null || entry.loreRecords().isEmpty()) {
                continue;
            }
            for (ItemOperationEntry.LoreOperationRecord record : entry.loreRecords()) {
                if (record == null) {
                    continue;
                }
                hasRecords = true;
                replayLoreOperation(currentLore, record);
            }
        }
        if (!hasRecords) {
            return false;
        }
        ItemTextBridge.setLoreLines(itemMeta, currentLore.isEmpty() ? null : currentLore);
        return true;
    }

    private void replayLoreOperation(List<String> lore, ItemOperationEntry.LoreOperationRecord record) {
        String action = record.action();
        List<String> renderedLines = record.renderedLines();
        String anchor = record.anchor();
        switch (action) {
            case "append" -> lore.addAll(renderedLines);
            case "prepend" -> prepend(lore, renderedLines);
            case "insert_below", "search_insert_below", "search_insert" -> insert(lore, renderedLines, anchor, true);
            case "insert_above", "search_insert_above" -> insert(lore, renderedLines, anchor, false);
            case "replace_line" -> replaceLine(lore, renderedLines, anchor);
            case "replace_text" -> replaceText(lore, renderedLines, anchor, false);
            case "replace_text_all" -> replaceText(lore, renderedLines, anchor, true);
            case "delete_line" -> deleteLines(lore, anchor);
            default -> {
            }
        }
    }

    private void prepend(List<String> lore, List<String> renderedLines) {
        if (renderedLines == null || renderedLines.isEmpty()) {
            return;
        }
        for (String line : renderedLines) {
            lore.add(0, line);
        }
    }

    private void insert(List<String> lore, List<String> renderedLines, String anchor, boolean below) {
        if (renderedLines == null || renderedLines.isEmpty()) {
            return;
        }
        for (String line : renderedLines) {
            lore.add(findInsertIndex(lore, anchor, below), line);
        }
    }

    private int findInsertIndex(List<String> lore, String anchor, boolean below) {
        if (Texts.isBlank(anchor)) {
            return below ? lore.size() : 0;
        }
        for (int index = 0; index < lore.size(); index++) {
            if (Texts.toStringSafe(lore.get(index)).contains(anchor)) {
                return below ? index + 1 : index;
            }
        }
        return lore.size();
    }

    private void replaceLine(List<String> lore, List<String> renderedLines, String anchor) {
        if (Texts.isBlank(anchor)) {
            return;
        }
        String replacement = renderedLines == null || renderedLines.isEmpty() ? "" : renderedLines.get(0);
        for (int index = 0; index < lore.size(); index++) {
            if (Texts.toStringSafe(lore.get(index)).contains(anchor)) {
                lore.set(index, Texts.toStringSafe(replacement));
                return;
            }
        }
    }

    private void replaceText(List<String> lore, List<String> renderedLines, String anchor, boolean replaceAll) {
        if (Texts.isBlank(anchor)) {
            return;
        }
        String replacement = renderedLines == null || renderedLines.isEmpty() ? "" : renderedLines.get(0);
        for (int index = 0; index < lore.size(); index++) {
            String current = Texts.toStringSafe(lore.get(index));
            if (!current.contains(anchor)) {
                continue;
            }
            lore.set(index, current.replace(anchor, replacement));
            if (!replaceAll) {
                return;
            }
        }
    }

    private void deleteLines(List<String> lore, String anchor) {
        if (Texts.isBlank(anchor)) {
            return;
        }
        for (int index = lore.size() - 1; index >= 0; index--) {
            if (Texts.toStringSafe(lore.get(index)).contains(anchor)) {
                lore.remove(index);
            }
        }
    }
}
