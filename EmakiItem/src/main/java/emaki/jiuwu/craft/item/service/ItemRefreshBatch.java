package emaki.jiuwu.craft.item.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import emaki.jiuwu.craft.corelib.assembly.ItemOperationLedger;

public final class ItemRefreshBatch {

    private static final NamespacedKey OPERATIONS_KEY = Objects.requireNonNull(
            NamespacedKey.fromString("emaki:item.operations"));

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
        if (expected == null || expected.getType().isAir() || !operationsFieldPresent(expected)) {
            ledgerRead = ItemOperationLedger.ReadResult.absent();
        } else {
            ledgerRead = operationLedger.read(expected);
            ledgerDecodes++;
        }
        return new SlotSnapshot(slot, expected, ledgerRead);
    }

    private boolean operationsFieldPresent(ItemStack itemStack) {
        ItemMeta itemMeta = itemStack == null ? null : itemStack.getItemMeta();
        return itemMeta != null
                && itemMeta.getPersistentDataContainer().has(OPERATIONS_KEY, PersistentDataType.STRING);
    }

    record SlotSnapshot(int slot, ItemStack expected, ItemOperationLedger.ReadResult ledgerRead) {

        SlotSnapshot {
            ledgerRead = ledgerRead == null ? ItemOperationLedger.ReadResult.corrupt(List.of()) : ledgerRead;
        }
    }
}
