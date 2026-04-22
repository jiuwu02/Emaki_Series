package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.pdc.PdcPartition;
import emaki.jiuwu.craft.corelib.pdc.PdcService;
import emaki.jiuwu.craft.corelib.text.Texts;

final class AssemblyDataManager {

    private final PdcService pdcService = new PdcService("emaki");
    private final PdcPartition itemPartition = pdcService.partition("item");
    private final EmakiNamespaceRegistry namespaceRegistry;
    private final EmakiItemLayerCodecRegistry codecRegistry;

    AssemblyDataManager(EmakiNamespaceRegistry namespaceRegistry,
            EmakiItemLayerCodecRegistry codecRegistry) {
        this.namespaceRegistry = Objects.requireNonNull(namespaceRegistry, "namespaceRegistry");
        this.codecRegistry = Objects.requireNonNull(codecRegistry, "codecRegistry");
    }

    boolean isEmakiItem(ItemStack itemStack) {
        return pdcService.has(itemStack, itemPartition, "schema_version", PersistentDataType.INTEGER)
                && pdcService.has(itemStack, itemPartition, "base_source", PersistentDataType.STRING);
    }

    ItemSource readBaseSource(ItemStack itemStack) {
        String shorthand = pdcService.get(itemStack, itemPartition, "base_source", PersistentDataType.STRING);
        return Texts.isBlank(shorthand) ? null : ItemSourceUtil.parseShorthand(shorthand);
    }

    int readBaseAmount(ItemStack itemStack) {
        Integer amount = pdcService.get(itemStack, itemPartition, "base_amount", PersistentDataType.INTEGER);
        return amount == null || amount <= 0 ? 1 : amount;
    }

    List<String> readActiveLayers(ItemStack itemStack) {
        String raw = pdcService.get(itemStack, itemPartition, "active_layers", PersistentDataType.STRING);
        if (Texts.isBlank(raw)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String entry : raw.split(",")) {
            String normalized = Texts.normalizeId(entry);
            if (!normalized.isBlank()) {
                result.add(normalized);
            }
        }
        return result.isEmpty() ? List.of() : namespaceRegistry.orderNamespaces(result);
    }

    Map<String, EmakiItemLayerSnapshot> readLayerSnapshots(ItemStack itemStack) {
        if (itemStack == null) {
            return Map.of();
        }
        Map<String, EmakiItemLayerSnapshot> result = new LinkedHashMap<>();
        for (String namespaceId : readActiveLayers(itemStack)) {
            EmakiItemLayerSnapshot snapshot = readLayerSnapshot(itemStack, namespaceId);
            if (snapshot != null) {
                result.put(namespaceId, snapshot);
            }
        }
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }

    EmakiItemLayerSnapshot readLayerSnapshot(ItemStack itemStack, String namespaceId) {
        if (itemStack == null || Texts.isBlank(namespaceId)) {
            return null;
        }
        String field = Texts.normalizeId(namespaceId) + ".snapshot";
        return pdcService.readBlob(itemStack, pdcService.partition(""), field, codecRegistry.codecFor(namespaceId));
    }

    void writeAssemblyData(ItemStack itemStack,
            int currentSchemaVersion,
            ItemSource baseSource,
            int amount,
            List<String> activeLayers,
            List<String> previousActiveLayers,
            String assemblySignature,
            Iterable<EmakiItemLayerSnapshot> snapshots) {
        pdcService.set(itemStack, itemPartition, "schema_version", PersistentDataType.INTEGER, currentSchemaVersion);
        pdcService.set(itemStack, itemPartition, "base_source", PersistentDataType.STRING, ItemSourceUtil.toShorthand(baseSource));
        pdcService.set(itemStack, itemPartition, "base_amount", PersistentDataType.INTEGER, amount);
        pdcService.set(itemStack, itemPartition, "active_layers", PersistentDataType.STRING, String.join(",", activeLayers));
        pdcService.set(itemStack, itemPartition, "assembly_signature", PersistentDataType.STRING, assemblySignature);
        clearInactiveLayerSnapshots(itemStack, previousActiveLayers, activeLayers);
        if (snapshots == null) {
            return;
        }
        for (EmakiItemLayerSnapshot snapshot : snapshots) {
            if (snapshot == null) {
                continue;
            }
            String field = Texts.normalizeId(snapshot.namespaceId()) + ".snapshot";
            pdcService.writeBlob(itemStack, pdcService.partition(""), field, codecRegistry.codecFor(snapshot.namespaceId()), snapshot);
        }
    }

    private void clearInactiveLayerSnapshots(ItemStack itemStack,
            List<String> previousActiveLayers,
            List<String> activeLayers) {
        if (itemStack == null || previousActiveLayers == null || previousActiveLayers.isEmpty()) {
            return;
        }
        List<String> currentActiveLayers = activeLayers == null ? List.of() : activeLayers;
        for (String namespaceId : previousActiveLayers) {
            String normalized = Texts.normalizeId(namespaceId);
            if (normalized.isBlank() || currentActiveLayers.contains(normalized)) {
                continue;
            }
            pdcService.remove(itemStack, pdcService.partition(""), normalized + ".snapshot");
        }
    }
}

