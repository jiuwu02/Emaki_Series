package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.text.Texts;
import net.kyori.adventure.text.Component;

final class ItemOperationReverter {

    private final ItemOperationLedger ledger;

    public ItemOperationReverter(ItemOperationLedger ledger) {
        this.ledger = ledger;
    }

    public RevertResult revert(ItemStack itemStack, String operationId) {
        if (itemStack == null || itemStack.getType().isAir() || Texts.isBlank(operationId)) {
            return RevertResult.NOT_FOUND;
        }
        ItemOperationEntry entry = ledger.remove(itemStack, operationId);
        if (entry == null) {
            return RevertResult.NOT_FOUND;
        }
        boolean hadName = !entry.nameRecords().isEmpty();
        revertLore(itemStack, entry);
        if (hadName) {
            rebuildName(itemStack);
        }
        return new RevertResult(true, 1);
    }

    public RevertResult revertAll(ItemStack itemStack, String sourceNamespace) {
        if (itemStack == null || itemStack.getType().isAir() || Texts.isBlank(sourceNamespace)) {
            return RevertResult.NOT_FOUND;
        }
        List<ItemOperationEntry> entries = ledger.removeByNamespace(itemStack, sourceNamespace);
        if (entries.isEmpty()) {
            return RevertResult.NOT_FOUND;
        }
        boolean hadName = false;
        for (int i = entries.size() - 1; i >= 0; i--) {
            ItemOperationEntry entry = entries.get(i);
            hadName = hadName || !entry.nameRecords().isEmpty();
            revertLore(itemStack, entry);
        }
        if (hadName) {
            rebuildName(itemStack);
        }
        return new RevertResult(true, entries.size());
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

        for (int i = records.size() - 1; i >= 0; i--) {
            ItemOperationEntry.LoreOperationRecord record = records.get(i);
            revertLoreOperation(currentLore, record);
        }

        ItemTextBridge.setLoreLines(itemMeta, currentLore.isEmpty() ? null : currentLore);
    }

    private void revertLoreOperation(List<String> lore, ItemOperationEntry.LoreOperationRecord record) {
        String action = record.action();
        List<String> renderedLines = record.renderedLines();
        List<String> originalLines = record.originalLines();

        switch (action) {
            case "append", "prepend", "insert_below", "insert_above", "search_insert_below", "search_insert_above" -> {
                for (String line : renderedLines) {
                    removeFirstMatch(lore, line);
                }
            }
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
            case "delete_line" -> {
                lore.addAll(originalLines);
            }
            default -> {
            }
        }
    }

    private void removeFirstMatch(List<String> lore, String target) {
        if (lore == null || Texts.isBlank(target)) {
            return;
        }
        for (int i = 0; i < lore.size(); i++) {
            if (loreLineMatches(lore.get(i), target)) {
                lore.remove(i);
                return;
            }
        }
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
