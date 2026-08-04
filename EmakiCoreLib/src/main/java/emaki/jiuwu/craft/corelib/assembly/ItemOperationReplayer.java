package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.api.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import net.kyori.adventure.text.Component;
import emaki.jiuwu.craft.corelib.api.assembly.ItemOperationEntry;

final class ItemOperationReplayer {

    List<ItemOperationEntry> replay(ItemStack itemStack, List<ItemOperationEntry> entries) {
        if (itemStack == null || itemStack.getType().isAir() || entries == null || entries.isEmpty()) {
            return entries == null ? List.of() : List.copyOf(entries);
        }
        List<ItemOperationEntry> refreshedEntries = replayLore(itemStack, entries);
        replayNameOperations(itemStack, refreshedEntries);
        return refreshedEntries;
    }

    List<ItemOperationEntry> replayFromBase(ItemStack itemStack,
            ItemOperationBaseView baseView,
            List<ItemOperationEntry> entries) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return entries == null ? List.of() : List.copyOf(entries);
        }
        applyBaseView(itemStack, baseView);
        return replay(itemStack, entries);
    }

    ReplayResult renderFromBase(ItemStack template,
            ItemOperationBaseView baseView,
            List<ItemOperationEntry> entries) {
        if (template == null || template.getType().isAir()) {
            return new ReplayResult(null, entries == null ? List.of() : List.copyOf(entries));
        }
        ItemStack rendered = template.clone();
        List<ItemOperationEntry> refreshedEntries = replayFromBase(rendered, baseView, entries);
        return new ReplayResult(rendered, refreshedEntries);
    }

    ItemOperationBaseView resolveBaseView(ItemStack itemStack, List<ItemOperationEntry> entries) {
        List<ItemOperationEntry> safeEntries = entries == null ? List.of() : entries;
        List<String> baseLore = firstRecordedLoreBaseline(safeEntries);
        if (baseLore == null) {
            baseLore = deriveLegacyBaseLore(currentLore(itemStack), safeEntries);
        }
        String baseCustomName = firstRecordedNameBaseline(safeEntries);
        if (baseCustomName == null) {
            baseCustomName = currentCustomName(itemStack);
        }
        return new ItemOperationBaseView(baseCustomName, baseLore);
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

    private void applyBaseView(ItemStack itemStack, ItemOperationBaseView baseView) {
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return;
        }
        ItemOperationBaseView safeBase = baseView == null
                ? new ItemOperationBaseView("", List.of())
                : baseView;
        ItemTextBridge.customName(itemMeta, Texts.isBlank(safeBase.customName())
                ? null
                : MiniMessages.parse(safeBase.customName()));
        ItemTextBridge.setLoreLines(itemMeta, safeBase.lore());
        itemStack.setItemMeta(itemMeta);
    }

    private void replayNameOperations(ItemStack itemStack, List<ItemOperationEntry> entries) {
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return;
        }
        Component current = null;
        for (ItemOperationEntry entry : entries) {
            if (entry == null || entry.nameRecords() == null || entry.nameRecords().isEmpty()) {
                continue;
            }
            if (current == null) {
                current = LedgerNameComposer.resolveBaseName(itemStack, itemMeta);
            }
            current = LedgerNameComposer.composeFromRecords(current, entry.nameRecords());
        }
        if (current != null) {
            LedgerNameComposer.writeName(itemStack, itemMeta, current);
        }
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
                                new ArrayList<>(currentLore),
                                record.requestedIndex(),
                                record.regexPattern(),
                                record.regexReplacement()
                        )
                        : new ItemOperationEntry.LoreOperationRecord(
                                record.action(),
                                record.renderedLines(),
                                record.anchor(),
                                record.originalLines(),
                                record.requestedIndex(),
                                record.regexPattern(),
                                record.regexReplacement()
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
        ItemTextBridge.setLoreLines(itemMeta, currentLore);
        return List.copyOf(refreshedEntries);
    }

    private List<String> firstRecordedLoreBaseline(List<ItemOperationEntry> entries) {
        for (ItemOperationEntry entry : entries) {
            if (entry == null || entry.loreRecords() == null) {
                continue;
            }
            for (ItemOperationEntry.LoreOperationRecord record : entry.loreRecords()) {
                if (record != null && record.beforeRecorded()) {
                    return List.copyOf(record.beforeLines());
                }
            }
        }
        return null;
    }

    private String firstRecordedNameBaseline(List<ItemOperationEntry> entries) {
        for (ItemOperationEntry entry : entries) {
            if (entry == null || entry.nameRecords() == null) {
                continue;
            }
            for (ItemOperationEntry.NameOperationRecord record : entry.nameRecords()) {
                if (record != null) {
                    return record.originalValue();
                }
            }
        }
        return null;
    }

    private List<String> deriveLegacyBaseLore(List<String> currentLore, List<ItemOperationEntry> entries) {
        List<String> lore = new ArrayList<>(currentLore == null ? List.of() : currentLore);
        for (int entryIndex = entries.size() - 1; entryIndex >= 0; entryIndex--) {
            ItemOperationEntry entry = entries.get(entryIndex);
            if (entry == null || entry.loreRecords() == null) {
                continue;
            }
            List<ItemOperationEntry.LoreOperationRecord> records = entry.loreRecords();
            for (int recordIndex = records.size() - 1; recordIndex >= 0; recordIndex--) {
                ItemOperationEntry.LoreOperationRecord record = records.get(recordIndex);
                if (record != null) {
                    revertLegacyLoreOperation(lore, record);
                }
            }
        }
        return List.copyOf(lore);
    }

    private void revertLegacyLoreOperation(List<String> lore, ItemOperationEntry.LoreOperationRecord record) {
        String action = record.action();
        List<String> renderedLines = record.renderedLines();
        List<String> originalLines = record.originalLines();
        switch (action) {
            case "append" -> removeExactBlock(lore, lore.size() - renderedLines.size(), renderedLines);
            case "prepend" -> removeLegacyPrependBlock(lore, renderedLines);
            case "insert_below", "search_insert_below", "search_insert" -> removeLegacyInsertBlock(
                    lore, renderedLines, record.anchor(), true);
            case "insert_above", "search_insert_above" -> removeLegacyInsertBlock(
                    lore, renderedLines, record.anchor(), false);
            case "replace_line" -> {
                if (!renderedLines.isEmpty() && !originalLines.isEmpty()) {
                    String replacement = renderedLines.get(0);
                    for (int index = 0; index < lore.size(); index++) {
                        if (loreLineMatches(lore.get(index), replacement)) {
                            lore.set(index, originalLines.get(0));
                            break;
                        }
                    }
                }
            }
            case "delete_line" -> lore.addAll(originalLines);
            default -> {
            }
        }
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
            case "replace_text" -> replaceText(lore, renderedLines, anchor, false, record.requestedIndex());
            case "replace_text_all" -> replaceText(lore, renderedLines, anchor, true, 0);
            case "delete_line" -> deleteLines(lore, anchor);
            case "regex_replace" -> regexReplace(lore, record.regexPattern(), record.regexReplacement());
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

    private void replaceText(List<String> lore,
            List<String> renderedLines,
            String anchor,
            boolean replaceAll,
            int requestedIndex) {
        if (Texts.isBlank(anchor)) {
            return;
        }
        String replacement = renderedLines == null || renderedLines.isEmpty() ? "" : renderedLines.get(0);
        List<Integer> matches = new ArrayList<>();
        for (int index = 0; index < lore.size(); index++) {
            String current = Texts.toStringSafe(lore.get(index));
            if (!current.contains(anchor)) {
                continue;
            }
            if (replaceAll) {
                lore.set(index, current.replace(anchor, replacement));
            } else {
                matches.add(index);
            }
        }
        if (replaceAll || matches.isEmpty()) {
            return;
        }
        int targetIndex;
        if (requestedIndex <= 0) {
            targetIndex = matches.get(0);
        } else if (requestedIndex <= matches.size()) {
            targetIndex = matches.get(requestedIndex - 1);
        } else {
            targetIndex = matches.get(matches.size() - 1);
        }
        String current = Texts.toStringSafe(lore.get(targetIndex));
        lore.set(targetIndex, current.replace(anchor, replacement));
    }

    private void regexReplace(List<String> lore, String regexPattern, String replacement) {
        if (lore == null || Texts.isBlank(regexPattern)) {
            return;
        }
        for (int index = 0; index < lore.size(); index++) {
            lore.set(index, OperationTemplateRenderer.replaceRegex(
                    lore.get(index),
                    regexPattern,
                    replacement,
                    java.util.Map.of()
            ));
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

    private void removeLegacyPrependBlock(List<String> lore, List<String> renderedLines) {
        List<String> legacyOrder = reversed(renderedLines);
        if (!removeExactBlock(lore, 0, legacyOrder)) {
            removeExactBlock(lore, 0, renderedLines);
        }
    }

    private void removeLegacyInsertBlock(List<String> lore,
            List<String> renderedLines,
            String anchor,
            boolean below) {
        int anchorIndex = findAnchorIndex(lore, anchor);
        if (anchorIndex < 0) {
            removeExactBlock(lore, lore.size() - renderedLines.size(), renderedLines);
            return;
        }
        if (below) {
            removeExactBlock(lore, anchorIndex + 1, reversed(renderedLines));
            return;
        }
        removeExactBlock(lore, anchorIndex - renderedLines.size(), renderedLines);
    }

    private int findAnchorIndex(List<String> lore, String anchor) {
        if (lore == null || Texts.isBlank(anchor)) {
            return -1;
        }
        for (int index = 0; index < lore.size(); index++) {
            if (Texts.toStringSafe(lore.get(index)).contains(anchor)) {
                return index;
            }
        }
        return -1;
    }

    private List<String> reversed(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<String> reversed = new ArrayList<>(lines);
        Collections.reverse(reversed);
        return reversed;
    }

    private boolean removeExactBlock(List<String> lore, int startIndex, List<String> expected) {
        if (lore == null || expected == null || expected.isEmpty()
                || startIndex < 0 || startIndex + expected.size() > lore.size()) {
            return false;
        }
        for (int offset = 0; offset < expected.size(); offset++) {
            if (!java.util.Objects.equals(lore.get(startIndex + offset), expected.get(offset))) {
                return false;
            }
        }
        lore.subList(startIndex, startIndex + expected.size()).clear();
        return true;
    }

    private boolean loreLineMatches(String loreLine, String target) {
        if (loreLine == null || target == null) {
            return false;
        }
        if (loreLine.equals(target)) {
            return true;
        }
        String strippedLore = loreLine.replaceAll("<[^>]+>", "").trim();
        String strippedTarget = target.replaceAll("<[^>]+>", "").trim();
        return !strippedLore.isEmpty() && strippedLore.equals(strippedTarget);
    }

    private List<String> currentLore(ItemStack itemStack) {
        if (itemStack == null) {
            return List.of();
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        List<String> lore = ItemTextBridge.loreLines(itemMeta);
        return lore == null || lore.isEmpty() ? List.of() : List.copyOf(lore);
    }

    private String currentCustomName(ItemStack itemStack) {
        if (itemStack == null) {
            return "";
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (!ItemTextBridge.hasCustomName(itemMeta)) {
            return "";
        }
        return MiniMessages.serialize(ItemTextBridge.customName(itemMeta));
    }

    record ReplayResult(ItemStack itemStack, List<ItemOperationEntry> entries) {

        ReplayResult {
            entries = entries == null || entries.isEmpty() ? List.of() : List.copyOf(entries);
        }
    }
}
