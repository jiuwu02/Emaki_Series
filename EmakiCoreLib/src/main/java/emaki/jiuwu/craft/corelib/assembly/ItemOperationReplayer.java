package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.text.Texts;
import net.kyori.adventure.text.Component;

final class ItemOperationReplayer {

    List<ItemOperationEntry> replay(ItemStack itemStack, List<ItemOperationEntry> entries) {
        if (itemStack == null || itemStack.getType().isAir() || entries == null || entries.isEmpty()) {
            return entries == null ? List.of() : List.copyOf(entries);
        }



        List<ItemOperationEntry> refreshedEntries = replayLore(itemStack, entries);
        replayNameOperations(itemStack, refreshedEntries);
        return refreshedEntries;
    }

    List<ItemOperationEntry> replayLore(ItemStack itemStack, List<ItemOperationEntry> entries) {
        if (itemStack == null || itemStack.getType().isAir() || entries == null || entries.isEmpty()) {
            return entries == null ? List.of() : List.copyOf(entries);
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null || !hasLoreRecords(entries)) {
            return List.copyOf(entries);
        }
        List<ItemOperationEntry> refreshedEntries = replayLoreOperations(itemMeta, entries);
        itemStack.setItemMeta(itemMeta);
        return refreshedEntries;
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

    private boolean hasLoreRecords(List<ItemOperationEntry> entries) {
        for (ItemOperationEntry entry : entries) {
            if (entry != null && entry.loreRecords() != null && !entry.loreRecords().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private List<ItemOperationEntry> replayLoreOperations(ItemMeta itemMeta, List<ItemOperationEntry> entries) {
        List<String> currentLore = new ArrayList<>();
        List<String> existingLore = ItemTextBridge.loreLines(itemMeta);
        if (existingLore != null) {
            currentLore.addAll(existingLore);
        }
        List<ItemOperationEntry> refreshedEntries = new ArrayList<>(entries.size());
        for (ItemOperationEntry entry : entries) {
            if (entry == null || entry.loreRecords() == null || entry.loreRecords().isEmpty()) {
                if (entry != null) {
                    refreshedEntries.add(entry);
                }
                continue;
            }
            List<ItemOperationEntry.LoreOperationRecord> refreshedRecords = new ArrayList<>();
            for (ItemOperationEntry.LoreOperationRecord record : entry.loreRecords()) {
                if (record == null) {
                    continue;
                }
                refreshedRecords.add(refreshedRecords.isEmpty()
                        ? new ItemOperationEntry.LoreOperationRecord(
                                record.action(),
                                record.renderedLines(),
                                record.anchor(),
                                record.originalLines(),
                                new ArrayList<>(currentLore)
                        )
                        : new ItemOperationEntry.LoreOperationRecord(
                                record.action(),
                                record.renderedLines(),
                                record.anchor(),
                                record.originalLines()
                        ));
                replayLoreOperation(currentLore, record);
            }
            refreshedEntries.add(new ItemOperationEntry(
                    entry.operationId(),
                    entry.sourceNamespace(),
                    entry.timestamp(),
                    entry.nameRecords(),
                    refreshedRecords
            ));
        }
        ItemTextBridge.setLoreLines(itemMeta, currentLore.isEmpty() ? null : currentLore);
        return List.copyOf(refreshedEntries);
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
        lore.addAll(0, renderedLines);
    }

    private void insert(List<String> lore, List<String> renderedLines, String anchor, boolean below) {
        if (renderedLines == null || renderedLines.isEmpty()) {
            return;
        }
        lore.addAll(findInsertIndex(lore, anchor, below), renderedLines);
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
