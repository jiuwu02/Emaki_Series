package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * 物品操作回退器（内部实现）。
 * <p>
 * 根据操作记录精确撤销对物品 Name/Lore 的修改。
 * 外部插件应通过 {@link ItemOperationLedger#revert} 或 {@link ItemOperationLedger#revertAll} 使用。
 */
final class ItemOperationReverter {

    private final ItemOperationLedger ledger;

    public ItemOperationReverter(ItemOperationLedger ledger) {
        this.ledger = ledger;
    }

    /**
     * 根据 operationId 回退一条操作。
     *
     * @param itemStack   目标物品
     * @param operationId 要回退的操作 ID
     * @return 回退结果
     */
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

    /**
     * 回退指定命名空间的所有操作（按逆序回退）。
     *
     * @param itemStack       目标物品
     * @param sourceNamespace 命名空间
     * @return 回退结果
     */
    public RevertResult revertAll(ItemStack itemStack, String sourceNamespace) {
        if (itemStack == null || itemStack.getType().isAir() || Texts.isBlank(sourceNamespace)) {
            return RevertResult.NOT_FOUND;
        }
        List<ItemOperationEntry> entries = ledger.removeByNamespace(itemStack, sourceNamespace);
        if (entries.isEmpty()) {
            return RevertResult.NOT_FOUND;
        }
        // Revert in reverse order (last applied first reverted)
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
            // Restore to no custom name (use default item name)
            ItemTextBridge.customName(itemMeta, null);
        }
    }

    private String revertNameOperation(String currentName, ItemOperationEntry.NameOperationRecord record) {
        String action = record.action();
        String renderedValue = record.renderedValue();
        String originalValue = record.originalValue();

        return switch (action) {
            case "append_suffix" -> {
                // Remove suffix from end
                if (Texts.isNotBlank(renderedValue) && currentName.endsWith(renderedValue)) {
                    yield currentName.substring(0, currentName.length() - renderedValue.length());
                }
                yield currentName;
            }
            case "prepend_prefix" -> {
                // Remove prefix from start
                if (Texts.isNotBlank(renderedValue) && currentName.startsWith(renderedValue)) {
                    yield currentName.substring(renderedValue.length());
                }
                yield currentName;
            }
            case "replace" -> {
                // Restore original name
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

        // Revert in reverse order
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
                // Remove the lines that were added
                for (String line : renderedLines) {
                    removeFirstMatch(lore, line);
                }
            }
            case "replace_line" -> {
                // Restore original lines: find the replacement and put back original
                if (!renderedLines.isEmpty() && !originalLines.isEmpty()) {
                    String replacement = renderedLines.get(0);
                    for (int i = 0; i < lore.size(); i++) {
                        if (loreLineMatches(lore.get(i), replacement)) {
                            // Replace with first original line
                            lore.set(i, originalLines.get(0));
                            break;
                        }
                    }
                }
            }
            case "delete_line" -> {
                // Re-insert the deleted lines at the end (position lost)
                lore.addAll(originalLines);
            }
            default -> {
                // regex_replace and unknown actions: cannot reliably revert
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
        // Exact match (MiniMessage string comparison)
        if (loreLine.equals(target)) {
            return true;
        }
        // Strip tags and compare content
        String strippedLore = loreLine.replaceAll("<[^>]+>", "").trim();
        String strippedTarget = target.replaceAll("<[^>]+>", "").trim();
        return !strippedLore.isEmpty() && strippedLore.equals(strippedTarget);
    }

    public record RevertResult(boolean success, int revertedCount) {

        public static final RevertResult NOT_FOUND = new RevertResult(false, 0);
    }
}
