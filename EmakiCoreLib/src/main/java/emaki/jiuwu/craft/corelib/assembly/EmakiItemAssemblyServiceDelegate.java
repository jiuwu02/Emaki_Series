package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.cache.CacheManager;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.monitor.PerformanceMonitor;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRegistry;
import emaki.jiuwu.craft.corelib.pdc.PdcPartition;
import emaki.jiuwu.craft.corelib.api.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import net.kyori.adventure.text.Component;
import emaki.jiuwu.craft.corelib.api.assembly.BaseNamePolicy;
import emaki.jiuwu.craft.corelib.api.assembly.EmakiStructuredPresentation;
import emaki.jiuwu.craft.corelib.api.assembly.ItemOperationEntry;

final class EmakiItemAssemblyServiceDelegate {

    private final EmakiItemAssemblyService ownerRef;

    EmakiItemAssemblyServiceDelegate(EmakiItemAssemblyService ownerRef) {
        this.ownerRef = ownerRef;
    }

    void configureAsync(AsyncTaskScheduler asyncTaskScheduler, ExecutionDispatcher executionDispatcher, Plugin executionOwner, PerformanceMonitor performanceMonitor) {
        ownerRef.asyncConfig = new EmakiItemAssemblyService.AsyncConfig(asyncTaskScheduler, executionDispatcher, executionOwner, performanceMonitor);
    }

    boolean isEmakiItem(ItemStack itemStack) {
        return ownerRef.dataManager.isEmakiItem(itemStack);
    }

    ItemSourceRef readBaseSource(ItemStack itemStack) {
        return ownerRef.dataManager.readBaseSource(itemStack);
    }

    int readBaseAmount(ItemStack itemStack) {
        return ownerRef.dataManager.readBaseAmount(itemStack);
    }

    List<String> readActiveLayers(ItemStack itemStack) {
        return ownerRef.dataManager.readActiveLayers(itemStack);
    }

    Map<String, EmakiItemLayerSnapshot> readLayerSnapshots(ItemStack itemStack) {
        return ownerRef.dataManager.readLayerSnapshots(itemStack);
    }

    EmakiItemLayerSnapshot readLayerSnapshot(ItemStack itemStack, String namespaceId) {
        return ownerRef.dataManager.readLayerSnapshot(itemStack, namespaceId);
    }

    void clearPreviewCache() {
        ownerRef.previewCache.clear();
    }

    void deliverToPlayer(Player player, ItemStack itemStack) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(itemStack.clone());
        leftover.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    boolean hasAssemblyNameOverlay(Map<String, EmakiItemLayerSnapshot> layers) {
        if (layers == null) {
            return false;
        }
        for (EmakiItemLayerSnapshot snapshot : layers.values()) {
            EmakiStructuredPresentation presentation = snapshot == null ? null : snapshot.structuredPresentation();
            if (presentation == null) {
                continue;
            }
            if (presentation.baseNamePolicy() == BaseNamePolicy.EXPLICIT_TEMPLATE
                    && Texts.isNotBlank(presentation.baseNameTemplate())) {
                return true;
            }
            if (!presentation.nameContributions().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    boolean hasOperationNameOverlay(List<ItemOperationEntry> operationEntries) {
        if (operationEntries == null) {
            return false;
        }
        for (ItemOperationEntry entry : operationEntries) {
            if (entry != null && entry.nameRecords() != null && !entry.nameRecords().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    boolean writeDisplay(ItemStack itemStack, String customName, List<String> lore) {
        ItemMeta itemMeta = itemStack == null ? null : itemStack.getItemMeta();
        if (itemMeta == null) {
            return false;
        }
        ItemTextBridge.customName(itemMeta, Texts.isBlank(customName) ? null : MiniMessages.parse(customName));
        ItemTextBridge.setLoreLines(itemMeta, lore);
        itemStack.setItemMeta(itemMeta);
        return true;
    }

    void applyBaseLore(ItemStack itemStack, List<String> baseLore) {
        if (itemStack == null || baseLore == null || baseLore.isEmpty()) {
            return;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return;
        }
        ItemTextBridge.setLoreLines(itemMeta, baseLore);
        itemStack.setItemMeta(itemMeta);
    }

    Component baseNameOverride(String baseCustomName) {
        return Texts.isBlank(baseCustomName) ? null : MiniMessages.parse(baseCustomName);
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

    List<String> operationIds(List<ItemOperationEntry> entries) {
        return ownerRef.operationIdentities(entries);
    }

    List<String> operationIdentities(List<ItemOperationEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        return entries.stream()
                .filter(Objects::nonNull)
                .map(entry -> entry.sourceNamespace() + ":" + entry.operationId())
                .toList();
    }

    List<String> safeLore(List<String> lore) {
        return lore == null || lore.isEmpty() ? List.of() : List.copyOf(lore);
    }

    void debugAssembly(DebugLogger debugLogger, UUID playerId, String langKey, Map<String, ?> replacements) {
        debugLogger.log("forge", playerId, langKey, replacements);
    }

    Map<String, Object> debugReplacements(Object... entries) {
        Map<String, Object> replacements = new LinkedHashMap<>();
        for (int index = 0; index + 1 < entries.length; index += 2) {
            replacements.put(Texts.toStringSafe(entries[index]), entries[index + 1]);
        }
        return replacements;
    }

    String pdcString(ItemMeta itemMeta, String field) {
        if (itemMeta == null || Texts.isBlank(field)) {
            return "";
        }
        PersistentDataContainer container = itemMeta.getPersistentDataContainer();

        return Texts.toStringSafe(container.get(
                EmakiItemAssemblyService.ITEM_SET_PARTITION.key(field), PersistentDataType.STRING));
    }

    <T> T measure(String metricKey, EmakiItemAssemblyService.SupplierWithException<T> supplier) {
        long startedAt = System.nanoTime();
        boolean success = false;
        try {
            T value = supplier.get();
            success = true;
            return value;
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        } finally {
            PerformanceMonitor monitor = ownerRef.asyncConfig.monitor();
            if (monitor != null) {
                monitor.record(metricKey, System.nanoTime() - startedAt, success);
            }
        }
    }

}
