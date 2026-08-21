package emaki.jiuwu.craft.item.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import emaki.jiuwu.craft.corelib.assembly.ItemOperationLedger;

public final class ItemRefreshBatch {

    private final PlayerInventory inventory;
    private final ItemOperationLedger operationLedger;
    private final Map<Integer, SlotSnapshot> snapshots = new LinkedHashMap<>();
    private int ledgerDecodes;

    ItemRefreshBatch(PlayerInventory inventory, ItemOperationLedger operationLedger) {
        this.inventory = inventory;
        this.operationLedger = operationLedger == null ? new ItemOperationLedger() : operationLedger;
    }

    boolean matches(PlayerInventory inventory) {
        return this.inventory == inventory;
    }

    SlotSnapshot capture(int slot) {
        if (!validSlot(slot)) {
            return null;
        }
        return snapshots.computeIfAbsent(slot, this::captureCurrent);
    }

    SlotSnapshot recapture(int slot) {
        if (!validSlot(slot)) {
            snapshots.remove(slot);
            return null;
        }
        SlotSnapshot snapshot = captureCurrent(slot);
        snapshots.put(slot, snapshot);
        return snapshot;
    }

    int ledgerDecodes() {
        return ledgerDecodes;
    }

    private boolean validSlot(int slot) {
        return inventory != null && slot >= 0 && slot < inventory.getSize();
    }

    private SlotSnapshot captureCurrent(int slot) {
        ItemStack current = inventory.getItem(slot);
        ItemStack expected = current == null ? null : current.clone();
        ItemOperationLedger.ReadResult ledgerRead;
        if (expected == null || expected.getType().isAir()) {
            ledgerRead = ItemOperationLedger.ReadResult.absent();
        } else {
            ledgerRead = operationLedger.read(expected);
            if (ledgerRead.status() != ItemOperationLedger.ReadStatus.ABSENT) {
                ledgerDecodes++;
            }
        }
        return new SlotSnapshot(slot, expected, ledgerRead);
    }

    record SlotSnapshot(int slot, ItemStack expected, ItemOperationLedger.ReadResult ledgerRead) {

        SlotSnapshot {
            ledgerRead = ledgerRead == null ? ItemOperationLedger.ReadResult.corrupt(List.of()) : ledgerRead;
        }
    }
}
