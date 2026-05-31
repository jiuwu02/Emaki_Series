package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;

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
        return revertEntry(itemStack, entry);
    }

    public RevertResult revertAll(ItemStack itemStack, String sourceNamespace) {
        if (itemStack == null || itemStack.getType().isAir() || Texts.isBlank(sourceNamespace)) {
            return RevertResult.NOT_FOUND;
        }
        List<ItemOperationEntry> entries = ledger.removeByNamespace(itemStack, sourceNamespace);
        if (entries.isEmpty()) {
            return RevertResult.NOT_FOUND;
        }
        for (int i = entries.size() - 1; i >= 0; i--) {
            revertEntry(itemStack, entries.get(i));
        }
        return new RevertResult(true, entries.size());
    }

    private RevertResult revertEntry(ItemStack itemStack, ItemOperationEntry entry) {
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return RevertResult.NOT_FOUND;
        }

        revertNameOperations(itemStack, itemMeta, entry.nameRecords());
        revertLoreOperations(itemMeta, entry.loreRecords());

        itemStack.setItemMeta(itemMeta);
        return new RevertResult(true, 1);
    }

    private void revertNameOperations(ItemStack itemStack, ItemMeta itemMeta, List<ItemOperationEntry.NameOperationRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        String currentName = ItemTextBridge.hasCustomName(itemMeta)
                ? MiniMessages.serialize(ItemTextBridge.customName(itemMeta))
                : "";

        for (int i = records.size() - 1; i >= 0; i--) {
            ItemOperationEntry.NameOperationRecord record = records.get(i);
            currentName = revertNameOperation(currentName, record);
        }

        if (Texts.isNotBlank(currentName)) {
            ItemTextBridge.customName(itemMeta, MiniMessages.parse(currentName));
        } else {
            ItemTextBridge.customName(itemMeta, null);
        }
    }

    private String revertNameOperation(String currentName, ItemOperationEntry.NameOperationRecord record) {
        String action = record.action();
        String renderedValue = record.renderedValue();
        String originalValue = record.originalValue();

        return switch (action) {
            case "append_suffix" -> {
                if (Texts.isNotBlank(renderedValue) && currentName.endsWith(renderedValue)) {
                    yield currentName.substring(0, currentName.length() - renderedValue.length());
                }
                yield currentName;
            }
            case "prepend_prefix" -> {
                if (Texts.isNotBlank(renderedValue) && currentName.startsWith(renderedValue)) {
                    yield currentName.substring(renderedValue.length());
                }
                yield currentName;
            }
            case "replace" -> {
                yield Texts.isNotBlank(originalValue) ? originalValue : currentName;
            }
            default -> currentName;
        };
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
