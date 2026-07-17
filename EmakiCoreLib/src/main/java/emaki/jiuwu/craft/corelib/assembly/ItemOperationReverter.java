package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.text.Texts;
import net.kyori.adventure.text.Component;

final class ItemOperationReverter {

    private final ItemOperationLedger ledger;
    private final ItemOperationReplayer replayer = new ItemOperationReplayer();

    public ItemOperationReverter(ItemOperationLedger ledger) {
        this.ledger = ledger;
    }

    public RevertResult revert(ItemStack itemStack, String operationId) {
        if (itemStack == null || itemStack.getType().isAir() || Texts.isBlank(operationId)) {
            return RevertResult.NOT_FOUND;
        }
        List<ItemOperationEntry> entriesBefore = ledger.readAll(itemStack);
        int removedIndex = lastOperationIndex(entriesBefore, operationId);
        if (removedIndex < 0) {
            return RevertResult.NOT_FOUND;
        }
        ItemOperationEntry entry = ledger.remove(itemStack, operationId);
        if (entry == null) {
            return RevertResult.NOT_FOUND;
        }
        rebuildLoreTail(itemStack, entriesBefore, removedIndex, index -> index != removedIndex);
        if (!entry.nameRecords().isEmpty()) {
            rebuildName(itemStack);
        }
        return new RevertResult(true, 1);
    }

    public RevertResult revertAll(ItemStack itemStack, String sourceNamespace) {
        if (itemStack == null || itemStack.getType().isAir() || Texts.isBlank(sourceNamespace)) {
            return RevertResult.NOT_FOUND;
        }
        List<ItemOperationEntry> entriesBefore = ledger.readAll(itemStack);
        int firstRemovedIndex = firstNamespaceIndex(entriesBefore, sourceNamespace);
        if (firstRemovedIndex < 0) {
            return RevertResult.NOT_FOUND;
        }
        List<ItemOperationEntry> removed = ledger.removeByNamespace(itemStack, sourceNamespace);
        if (removed.isEmpty()) {
            return RevertResult.NOT_FOUND;
        }
        rebuildLoreTail(itemStack, entriesBefore, firstRemovedIndex,
                index -> !sourceNamespace.equals(entriesBefore.get(index).sourceNamespace()));
        if (removed.stream().anyMatch(entry -> !entry.nameRecords().isEmpty())) {
            rebuildName(itemStack);
        }
        return new RevertResult(true, removed.size());
    }

    private int lastOperationIndex(List<ItemOperationEntry> entries, String operationId) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (operationId.equals(entries.get(i).operationId())) {
                return i;
            }
        }
        return -1;
    }

    private int firstNamespaceIndex(List<ItemOperationEntry> entries, String sourceNamespace) {
        for (int i = 0; i < entries.size(); i++) {
            if (sourceNamespace.equals(entries.get(i).sourceNamespace())) {
                return i;
            }
        }
        return -1;
    }

    private void rebuildLoreTail(ItemStack itemStack,
            List<ItemOperationEntry> entriesBefore,
            int startIndex,
            IntPredicate keepIndex) {
        for (int i = entriesBefore.size() - 1; i >= startIndex; i--) {
            revertLore(itemStack, entriesBefore.get(i));
        }
        List<ItemOperationEntry> updatedEntries = new ArrayList<>();
        for (int i = 0; i < startIndex; i++) {
            if (keepIndex.test(i)) {
                updatedEntries.add(entriesBefore.get(i));
            }
        }
        List<ItemOperationEntry> replayEntries = new ArrayList<>();
        for (int i = startIndex; i < entriesBefore.size(); i++) {
            if (keepIndex.test(i)) {
                replayEntries.add(entriesBefore.get(i));
            }
        }
        updatedEntries.addAll(replayer.replayLore(itemStack, replayEntries));
        ledger.replaceAll(itemStack, updatedEntries);
    }

    private void revertLore(ItemStack itemStack, ItemOperationEntry entry) {
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return;
        }
        revertLoreOperations(itemMeta, entry.loreRecords());
        itemStack.setItemMeta(itemMeta);
    }

    /**
     * Deterministically rebuilds the item name after one or more entries were
     * removed. The base name is re-derived from the item type (after clearing
     * the overlaid custom name) so a translatable vanilla name is preserved,
     * then the name records of every <em>remaining</em> ledger entry are
     * replayed on top. This keeps name contributions from other namespaces
     * (e.g. gem prefixes) intact while removing the reverted ones.
     */
    private void rebuildName(ItemStack itemStack) {
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return;
        }
        // Clear the overlaid name so effectiveName resolves the original
        // (translatable) base name rather than the flattened, suffixed name.
        ItemTextBridge.customName(itemMeta, null);
        itemStack.setItemMeta(itemMeta);

        itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return;
        }
        List<ItemOperationEntry.NameOperationRecord> remainingNameRecords = new ArrayList<>();
        for (ItemOperationEntry entry : ledger.readAll(itemStack)) {
            if (entry == null || entry.nameRecords() == null) {
                continue;
            }
            for (ItemOperationEntry.NameOperationRecord record : entry.nameRecords()) {
                if (record != null) {
                    remainingNameRecords.add(record);
                }
            }
        }
        Component baseName = LedgerNameComposer.resolveBaseName(itemStack, itemMeta);
        Component result = LedgerNameComposer.composeFromRecords(baseName, remainingNameRecords);
        LedgerNameComposer.writeName(itemStack, itemMeta, result);
    }

    private void revertLoreOperations(ItemMeta itemMeta, List<ItemOperationEntry.LoreOperationRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<String> currentLore = new ArrayList<>();
        List<String> existingLore = ItemTextBridge.loreLines(itemMeta);
        if (existingLore != null) {
            currentLore.addAll(existingLore);
        }

        for (ItemOperationEntry.LoreOperationRecord record : records) {
            if (record.beforeRecorded()) {
                currentLore.clear();
                currentLore.addAll(record.beforeLines());
                ItemTextBridge.setLoreLines(itemMeta, currentLore.isEmpty() ? null : currentLore);
                return;
            }
        }

        for (int i = records.size() - 1; i >= 0; i--) {
            revertLoreOperation(currentLore, records.get(i));
        }

        ItemTextBridge.setLoreLines(itemMeta, currentLore.isEmpty() ? null : currentLore);
    }

    private void revertLoreOperation(List<String> lore, ItemOperationEntry.LoreOperationRecord record) {
        if (record.beforeRecorded()) {
            lore.clear();
            lore.addAll(record.beforeLines());
            return;
        }

        String action = record.action();
        List<String> renderedLines = record.renderedLines();
        List<String> originalLines = record.originalLines();

        switch (action) {
            case "append" -> removeExactBlock(lore, lore.size() - renderedLines.size(), renderedLines);
            case "prepend" -> removeLegacyPrependBlock(lore, renderedLines);
            case "insert_below", "search_insert_below", "search_insert" -> removeLegacyInsertBlock(lore, renderedLines, record.anchor(), true);
            case "insert_above", "search_insert_above" -> removeLegacyInsertBlock(lore, renderedLines, record.anchor(), false);
            case "replace_line" -> {
                if (!renderedLines.isEmpty() && !originalLines.isEmpty()) {
                    String replacement = renderedLines.get(0);
                    for (int i = 0; i < lore.size(); i++) {
                        if (loreLineMatches(lore.get(i), replacement)) {
                            lore.set(i, originalLines.get(0));
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
        for (int i = 0; i < lore.size(); i++) {
            if (Texts.toStringSafe(lore.get(i)).contains(anchor)) {
                return i;
            }
        }
        return -1;
    }

    private List<String> reversed(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<String> reversed = new ArrayList<>(lines);
        java.util.Collections.reverse(reversed);
        return reversed;
    }

    private boolean removeExactBlock(List<String> lore, int startIndex, List<String> expected) {
        if (lore == null || expected == null || expected.isEmpty() || startIndex < 0 || startIndex + expected.size() > lore.size()) {
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

    public record RevertResult(boolean success, int revertedCount) {

        public static final RevertResult NOT_FOUND = new RevertResult(false, 0);
    }
}
