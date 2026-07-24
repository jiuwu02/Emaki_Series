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
    private final ItemOperationReplayer replayer = new ItemOperationReplayer();
    private final ItemLoreReconciler loreReconciler = new ItemLoreReconciler();

    ItemOperationReverter(ItemOperationLedger ledger) {
        this.ledger = ledger;
    }

    RevertResult revert(ItemStack itemStack,
                        String operationId,
                        ItemOperationLedger.ReadResult readResult) {
        if (readResult == null || readResult.corrupt()) {
            return RevertResult.notFound(readResult == null ? List.of() : readResult.entries());
        }
        return revert(itemStack, operationId, readResult.entries());
    }

    RevertResult revert(ItemStack itemStack,
                        String operationId,
                        List<ItemOperationEntry> entries) {
        List<ItemOperationEntry> entriesBefore = entries == null || entries.isEmpty()
                ? List.of()
                : List.copyOf(entries);
        if (itemStack == null || itemStack.getType().isAir() || Texts.isBlank(operationId)) {
            return RevertResult.notFound(entriesBefore);
        }
        int removedIndex = lastOperationIndex(entriesBefore, operationId);
        if (removedIndex < 0) {
            return RevertResult.notFound(entriesBefore);
        }
        List<ItemOperationEntry> retained = new ArrayList<>(entriesBefore);
        retained.remove(removedIndex);
        return rebuild(itemStack, entriesBefore, retained, 1);
    }

    RevertResult revertAll(ItemStack itemStack,
                           String sourceNamespace,
                           ItemOperationLedger.ReadResult readResult) {
        if (readResult == null || readResult.corrupt()) {
            return RevertResult.notFound(readResult == null ? List.of() : readResult.entries());
        }
        List<ItemOperationEntry> entriesBefore = readResult.entries();
        if (itemStack == null || itemStack.getType().isAir() || Texts.isBlank(sourceNamespace)) {
            return RevertResult.notFound(entriesBefore);
        }
        List<ItemOperationEntry> retained = new ArrayList<>();
        int removedCount = 0;
        for (ItemOperationEntry entry : entriesBefore) {
            if (entry != null && sourceNamespace.equals(entry.sourceNamespace())) {
                removedCount++;
            } else if (entry != null) {
                retained.add(entry);
            }
        }
        if (removedCount == 0) {
            return RevertResult.notFound(entriesBefore);
        }
        return rebuild(itemStack, entriesBefore, retained, removedCount);
    }

    private RevertResult rebuild(ItemStack itemStack,
                                 List<ItemOperationEntry> entriesBefore,
                                 List<ItemOperationEntry> retainedEntries,
                                 int removedCount) {
        ItemStack managedTemplate = ledger.managedDisplayTemplate(itemStack);
        if (managedTemplate == null) {
            return RevertResult.notFound(entriesBefore);
        }
        ItemOperationBaseView baseView = replayer.resolveBaseView(managedTemplate, entriesBefore);
        boolean assemblyNameOverlay = ledger.hasAssemblyNameOverlay(itemStack, baseView);
        ItemOperationReplayer.ReplayResult oldProjection = replayer.renderFromBase(
                managedTemplate,
                baseView,
                entriesBefore
        );
        ItemOperationReplayer.ReplayResult newProjection = replayer.renderFromBase(
                managedTemplate,
                baseView,
                retainedEntries
        );
        if (oldProjection.itemStack() == null || newProjection.itemStack() == null) {
            return RevertResult.notFound(entriesBefore);
        }

        List<String> oldManagedLore = currentLore(oldProjection.itemStack());
        List<String> currentActualLore = currentLore(itemStack);
        List<String> newManagedLore = currentLore(newProjection.itemStack());
        ItemLoreReconciler.Reconciliation reconciliation = loreReconciler.reconcile(
                oldManagedLore,
                currentActualLore,
                newManagedLore
        );
        if (!loreReconciler.preservesExternalProjection(
                newManagedLore,
                reconciliation.lore(),
                reconciliation.externalLines())) {
            return RevertResult.notFound(entriesBefore);
        }

        ItemOperationLedger.CustomNameUpdate customNameUpdate = ledger.prepareCustomNameUpdate(
                itemStack,
                currentCustomName(oldProjection.itemStack()),
                currentCustomName(newProjection.itemStack()),
                assemblyNameOverlay || hasNameOverlay(entriesBefore),
                assemblyNameOverlay || hasNameOverlay(newProjection.entries())
        );
        ItemOperationLedger.SnapshotUpdate snapshotUpdate = ledger.preparePresentationSnapshotUpdate(
                itemStack,
                newProjection.itemStack(),
                assemblyNameOverlay
        );
        if (!snapshotUpdate.valid()
                || !writeDisplay(itemStack, customNameUpdate.customName(), reconciliation.lore())) {
            return RevertResult.notFound(entriesBefore);
        }
        ledger.replaceAll(itemStack, newProjection.entries());
        ledger.writePresentationSnapshotUpdate(itemStack, snapshotUpdate);
        ledger.writeCustomNameUpdate(itemStack, customNameUpdate);
        return new RevertResult(true, removedCount, newProjection.entries());
    }

    private int lastOperationIndex(List<ItemOperationEntry> entries, String operationId) {
        for (int index = entries.size() - 1; index >= 0; index--) {
            if (operationId.equals(entries.get(index).operationId())) {
                return index;
            }
        }
        return -1;
    }

    private boolean hasNameOverlay(List<ItemOperationEntry> entries) {
        if (entries == null) {
            return false;
        }
        for (ItemOperationEntry entry : entries) {
            if (entry != null && entry.nameRecords() != null && !entry.nameRecords().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean writeDisplay(ItemStack itemStack, String customName, List<String> lore) {
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return false;
        }
        ItemTextBridge.customName(itemMeta, Texts.isBlank(customName) ? null : MiniMessages.parse(customName));
        ItemTextBridge.setLoreLines(itemMeta, lore);
        itemStack.setItemMeta(itemMeta);
        return true;
    }

    private List<String> currentLore(ItemStack itemStack) {
        ItemMeta itemMeta = itemStack == null ? null : itemStack.getItemMeta();
        List<String> lore = ItemTextBridge.loreLines(itemMeta);
        return lore == null || lore.isEmpty() ? List.of() : List.copyOf(lore);
    }

    private String currentCustomName(ItemStack itemStack) {
        ItemMeta itemMeta = itemStack == null ? null : itemStack.getItemMeta();
        if (!ItemTextBridge.hasCustomName(itemMeta)) {
            return "";
        }
        return MiniMessages.serialize(ItemTextBridge.customName(itemMeta));
    }

    record RevertResult(boolean success, int revertedCount, List<ItemOperationEntry> entries) {

        static final RevertResult NOT_FOUND = new RevertResult(false, 0, List.of());

        static RevertResult notFound(List<ItemOperationEntry> entries) {
            return new RevertResult(false, 0, entries);
        }

        RevertResult {
            entries = entries == null || entries.isEmpty() ? List.of() : List.copyOf(entries);
        }
    }
}
