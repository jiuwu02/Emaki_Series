package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.pdc.PdcPartition;
import emaki.jiuwu.craft.corelib.pdc.PdcService;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;

final class AssemblyDataManager {

    private static final String SCHEMA_VERSION = "schema_version";
    private static final String BASE_SOURCE = "base_source";
    private static final String BASE_AMOUNT = "base_amount";
    private static final String BASE_CUSTOM_NAME = "base_custom_name";
    private static final String BASE_LORE = "base_lore";
    private static final String ACTIVE_LAYERS = "active_layers";
    private static final String ASSEMBLY_SIGNATURE = "assembly_signature";
    private static final String OPERATIONS = "operations";
    private static final String PRESENTATION_SNAPSHOT = "presentation_snapshot";

    private static final Set<String> ITEM_FIELDS = Set.of(
            SCHEMA_VERSION,
            BASE_SOURCE,
            BASE_AMOUNT,
            BASE_CUSTOM_NAME,
            BASE_LORE,
            ACTIVE_LAYERS,
            ASSEMBLY_SIGNATURE,
            OPERATIONS,
            PRESENTATION_SNAPSHOT,
            ItemOperationLedger.EXTERNAL_CUSTOM_NAME_FIELD
    );

    private final PdcService pdcService;
    private final PdcPartition itemPartition;
    private final PdcPartition rootPartition;
    private final EmakiNamespaceRegistry namespaceRegistry;
    private final EmakiItemLayerCodecRegistry codecRegistry;

    AssemblyDataManager(EmakiNamespaceRegistry namespaceRegistry,
            EmakiItemLayerCodecRegistry codecRegistry) {
        this(namespaceRegistry, codecRegistry, null);
    }

    AssemblyDataManager(EmakiNamespaceRegistry namespaceRegistry,
            EmakiItemLayerCodecRegistry codecRegistry,
            DebugLogger debugLogger) {
        this.namespaceRegistry = Objects.requireNonNull(namespaceRegistry, "namespaceRegistry");
        this.codecRegistry = Objects.requireNonNull(codecRegistry, "codecRegistry");
        this.pdcService = new PdcService("emaki", "pdc", debugLogger);
        this.itemPartition = pdcService.partition("item");
        this.rootPartition = pdcService.partition("");
    }

    boolean isEmakiItem(ItemStack itemStack) {
        return pdcService.has(itemStack, itemPartition, SCHEMA_VERSION, PersistentDataType.INTEGER)
                && pdcService.has(itemStack, itemPartition, BASE_SOURCE, PersistentDataType.STRING);
    }

    ItemSourceRef readBaseSource(ItemStack itemStack) {
        String shorthand = pdcService.get(itemStack, itemPartition, BASE_SOURCE, PersistentDataType.STRING);
        return Texts.isBlank(shorthand) ? null : ItemSourceUtil.parseShorthand(shorthand);
    }

    int readBaseAmount(ItemStack itemStack) {
        Integer amount = pdcService.get(itemStack, itemPartition, BASE_AMOUNT, PersistentDataType.INTEGER);
        return amount == null || amount <= 0 ? 1 : amount;
    }

    String readBaseCustomName(ItemStack itemStack) {
        String customName = pdcService.get(itemStack, itemPartition, BASE_CUSTOM_NAME, PersistentDataType.STRING);
        return Texts.toStringSafe(customName);
    }

    List<String> readBaseLore(ItemStack itemStack) {
        String payload = pdcService.get(itemStack, itemPartition, BASE_LORE, PersistentDataType.STRING);
        if (Texts.isBlank(payload)) {
            return List.of();
        }
        try {
            return Texts.asStringList(YamlFiles.load(payload).get("lore"));
        } catch (RuntimeException _) {
            return List.of();
        }
    }

    List<String> readActiveLayers(ItemStack itemStack) {
        String raw = pdcService.get(itemStack, itemPartition, ACTIVE_LAYERS, PersistentDataType.STRING);
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
        return pdcService.readBlob(itemStack, rootPartition, field, codecRegistry.codecFor(namespaceId));
    }

    ItemPresentationSnapshot readPresentationSnapshot(ItemStack itemStack) {
        if (itemStack == null) {
            return null;
        }
        String payload = pdcService.get(itemStack, itemPartition, PRESENTATION_SNAPSHOT, PersistentDataType.STRING);
        return ItemPresentationSnapshot.decodeStrict(payload);
    }

    void copyPersistentDataForCommit(ItemStack existingItem, ItemStack targetItem) {
        if (existingItem == null || targetItem == null) {
            return;
        }
        ItemMeta existingMeta = existingItem.getItemMeta();
        ItemMeta targetMeta = targetItem.getItemMeta();
        if (existingMeta == null || targetMeta == null) {
            return;
        }
        existingMeta.getPersistentDataContainer().copyTo(targetMeta.getPersistentDataContainer(), true);
        targetItem.setItemMeta(targetMeta);
    }

    Set<NamespacedKey> nonOwnedKeys(ItemStack itemStack, Iterable<String> layerNamespaceIds) {
        if (itemStack == null) {
            return Set.of();
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return Set.of();
        }
        Set<NamespacedKey> result = new LinkedHashSet<>();
        for (NamespacedKey key : itemMeta.getPersistentDataContainer().getKeys()) {
            if (!isOwnedKey(key, layerNamespaceIds)) {
                result.add(key);
            }
        }
        return result.isEmpty() ? Set.of() : Set.copyOf(result);
    }

    boolean containsKeys(ItemStack itemStack, Set<NamespacedKey> expectedKeys) {
        if (expectedKeys == null || expectedKeys.isEmpty()) {
            return true;
        }
        if (itemStack == null) {
            return false;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        return itemMeta != null && itemMeta.getPersistentDataContainer().getKeys().containsAll(expectedKeys);
    }

    void writeAssemblyData(ItemStack itemStack,
            int currentSchemaVersion,
            ItemSourceRef baseSource,
            int amount,
            String baseCustomName,
            List<String> baseLore,
            List<String> activeLayers,
            List<String> previousActiveLayers,
            String assemblySignature,
            Iterable<EmakiItemLayerSnapshot> snapshots) {
        pdcService.set(itemStack, itemPartition, SCHEMA_VERSION, PersistentDataType.INTEGER, currentSchemaVersion);
        pdcService.set(itemStack, itemPartition, BASE_SOURCE, PersistentDataType.STRING, ItemSourceUtil.toShorthand(baseSource));
        pdcService.set(itemStack, itemPartition, BASE_AMOUNT, PersistentDataType.INTEGER, amount);
        if (Texts.isBlank(baseCustomName)) {
            pdcService.remove(itemStack, itemPartition, BASE_CUSTOM_NAME);
        } else {
            pdcService.set(itemStack, itemPartition, BASE_CUSTOM_NAME, PersistentDataType.STRING, baseCustomName);
        }
        writeBaseLore(itemStack, baseLore);
        pdcService.set(itemStack, itemPartition, ACTIVE_LAYERS, PersistentDataType.STRING, String.join(",", activeLayers));
        pdcService.set(itemStack, itemPartition, ASSEMBLY_SIGNATURE, PersistentDataType.STRING, assemblySignature);
        clearInactiveLayerSnapshots(itemStack, previousActiveLayers, activeLayers);
        if (snapshots == null) {
            return;
        }
        for (EmakiItemLayerSnapshot snapshot : snapshots) {
            if (snapshot == null) {
                continue;
            }
            String field = Texts.normalizeId(snapshot.namespaceId()) + ".snapshot";
            pdcService.writeBlob(itemStack, rootPartition, field, codecRegistry.codecFor(snapshot.namespaceId()), snapshot);
        }
    }

    boolean writePresentationSnapshot(ItemStack itemStack, ItemPresentationSnapshot snapshot) {
        if (itemStack == null || snapshot == null) {
            return false;
        }
        String encoded = ItemPresentationSnapshot.CODEC.encode(snapshot);
        if (Texts.isBlank(encoded)) {
            return false;
        }
        pdcService.set(itemStack, itemPartition, PRESENTATION_SNAPSHOT, PersistentDataType.STRING, encoded);
        ItemPresentationSnapshot restored = readPresentationSnapshot(itemStack);
        return snapshot.equals(restored);
    }

    private boolean isOwnedKey(NamespacedKey key, Iterable<String> layerNamespaceIds) {
        if (key == null || !"emaki".equals(key.getNamespace())) {
            return false;
        }
        String path = key.getKey();
        if (path.startsWith("item.")) {
            return ITEM_FIELDS.contains(path.substring("item.".length()));
        }
        if (layerNamespaceIds == null) {
            return false;
        }
        for (String namespaceId : layerNamespaceIds) {
            String normalized = Texts.normalizeId(namespaceId);
            if (!normalized.isBlank() && path.equals(normalized + ".snapshot")) {
                return true;
            }
        }
        return false;
    }

    private void writeBaseLore(ItemStack itemStack, List<String> baseLore) {
        if (baseLore == null || baseLore.isEmpty()) {
            pdcService.remove(itemStack, itemPartition, BASE_LORE);
            return;
        }
        List<String> lines = baseLore.stream().map(Texts::toStringSafe).toList();
        pdcService.set(itemStack, itemPartition, BASE_LORE, PersistentDataType.STRING, YamlFiles.dump(Map.of("lore", lines)));
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
            pdcService.remove(itemStack, rootPartition, normalized + ".snapshot");
        }
    }
}
