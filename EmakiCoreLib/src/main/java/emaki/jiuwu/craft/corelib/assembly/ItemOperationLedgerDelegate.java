package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.api.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.api.pdc.PdcKeyMigration;
import emaki.jiuwu.craft.corelib.pdc.PdcPartition;
import emaki.jiuwu.craft.corelib.pdc.PdcService;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRegistry;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.assembly.ItemOperationEntry;

final class ItemOperationLedgerDelegate {

    private final ItemOperationLedger ownerRef;

    ItemOperationLedgerDelegate(ItemOperationLedger ownerRef) {
        this.ownerRef = ownerRef;
    }

    String reconcileCustomName(String oldManagedName, String currentName, String newManagedName, boolean managedOverlayRemains) {
        if (currentName.equals(oldManagedName)) {
            return newManagedName;
        }
        if (newManagedName.equals(oldManagedName)) {
            return currentName;
        }
        return managedOverlayRemains ? newManagedName : currentName;
    }

    boolean hasNameOverlay(List<ItemOperationEntry> entries) {
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

    boolean writeDisplay(ItemStack itemStack, String customName, List<String> lore) {
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return false;
        }
        ItemTextBridge.customName(itemMeta, Texts.isBlank(customName) ? null : MiniMessages.parse(customName));
        ItemTextBridge.setLoreLines(itemMeta, lore);
        itemStack.setItemMeta(itemMeta);
        return true;
    }

    List<String> currentLore(ItemStack itemStack) {
        ItemMeta itemMeta = itemStack == null ? null : itemStack.getItemMeta();
        List<String> lore = ItemTextBridge.loreLines(itemMeta);
        return lore == null || lore.isEmpty() ? List.of() : List.copyOf(lore);
    }

    String currentCustomName(ItemStack itemStack) {
        ItemMeta itemMeta = itemStack == null ? null : itemStack.getItemMeta();
        if (!ItemTextBridge.hasCustomName(itemMeta)) {
            return "";
        }
        return MiniMessages.serialize(ItemTextBridge.customName(itemMeta));
    }

    int operationIndex(List<ItemOperationEntry> entries, String operationId) {
        if (entries == null || Texts.isBlank(operationId)) {
            return -1;
        }
        for (int index = 0; index < entries.size(); index++) {
            ItemOperationEntry entry = entries.get(index);
            if (entry != null && operationId.equals(entry.operationId())) {
                return index;
            }
        }
        return -1;
    }

    List<ItemOperationEntry> normalizeEntries(List<ItemOperationEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        Map<String, ItemOperationEntry> byId = new LinkedHashMap<>();
        for (ItemOperationEntry entry : entries) {
            if (entry == null || entry.isEmpty() || Texts.isBlank(entry.operationId())) {
                continue;
            }
            byId.put(entry.operationId(), entry);
        }
        return byId.isEmpty() ? List.of() : List.copyOf(byId.values());
    }

    Object parsePayload(String payload) {
        if (Texts.isBlank(payload)) {
            return null;
        }
        return YamlFiles.load(payload).get("ops");
    }

}
