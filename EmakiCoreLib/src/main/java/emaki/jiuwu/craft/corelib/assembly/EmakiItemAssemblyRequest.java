package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.item.ItemSource;

public record EmakiItemAssemblyRequest(ItemSource baseSource,
        int amount,
        ItemStack existingItem,
        List<EmakiItemLayerSnapshot> layerSnapshots,
        List<String> removedNamespaceIds,
        UUID feedbackPlayerId) {

    public EmakiItemAssemblyRequest {
        layerSnapshots = copyLayers(layerSnapshots);
        removedNamespaceIds = copyRemovedNamespaces(removedNamespaceIds);
    }

    public EmakiItemAssemblyRequest(ItemSource baseSource,
            int amount,
            ItemStack existingItem,
            Collection<EmakiItemLayerSnapshot> layerSnapshots) {
        this(baseSource, amount, existingItem, layerSnapshots, List.of(), null);
    }

    public EmakiItemAssemblyRequest(ItemSource baseSource,
            int amount,
            ItemStack existingItem,
            Collection<EmakiItemLayerSnapshot> layerSnapshots,
            UUID feedbackPlayerId) {
        this(baseSource, amount, existingItem, layerSnapshots, List.of(), feedbackPlayerId);
    }

    public EmakiItemAssemblyRequest(ItemSource baseSource,
            int amount,
            ItemStack existingItem,
            Collection<EmakiItemLayerSnapshot> layerSnapshots,
            Collection<String> removedNamespaceIds) {
        this(baseSource, amount, existingItem, layerSnapshots, removedNamespaceIds, null);
    }

    public EmakiItemAssemblyRequest(ItemSource baseSource,
            int amount,
            ItemStack existingItem,
            Collection<EmakiItemLayerSnapshot> layerSnapshots,
            Collection<String> removedNamespaceIds,
            UUID feedbackPlayerId) {
        this(baseSource, amount, existingItem, copyLayers(layerSnapshots), copyRemovedNamespaces(removedNamespaceIds), feedbackPlayerId);
    }

    public EmakiItemAssemblyRequest withFeedbackPlayerId(UUID playerId) {
        return new EmakiItemAssemblyRequest(baseSource, amount, existingItem, layerSnapshots, removedNamespaceIds, playerId);
    }

    public EmakiItemAssemblyRequest withoutLayer(String namespaceId) {
        if (namespaceId == null || namespaceId.isBlank()) {
            return this;
        }
        List<String> namespaces = new ArrayList<>(removedNamespaceIds);
        namespaces.add(namespaceId);
        return new EmakiItemAssemblyRequest(baseSource, amount, existingItem, layerSnapshots, namespaces, feedbackPlayerId);
    }

    public EmakiItemAssemblyRequest withoutLayers(Collection<String> namespaceIds) {
        if (namespaceIds == null || namespaceIds.isEmpty()) {
            return this;
        }
        List<String> namespaces = new ArrayList<>(removedNamespaceIds);
        namespaces.addAll(namespaceIds);
        return new EmakiItemAssemblyRequest(baseSource, amount, existingItem, layerSnapshots, namespaces, feedbackPlayerId);
    }

    private static List<EmakiItemLayerSnapshot> copyLayers(Collection<EmakiItemLayerSnapshot> layerSnapshots) {
        if (layerSnapshots == null || layerSnapshots.isEmpty()) {
            return List.of();
        }
        List<EmakiItemLayerSnapshot> result = new ArrayList<>();
        for (EmakiItemLayerSnapshot snapshot : layerSnapshots) {
            if (snapshot != null) {
                result.add(snapshot);
            }
        }
        return result.isEmpty() ? List.of() : List.copyOf(result);
    }

    private static List<String> copyRemovedNamespaces(Collection<String> removedNamespaceIds) {
        if (removedNamespaceIds == null || removedNamespaceIds.isEmpty()) {
            return List.of();
        }
        Map<String, String> unique = new java.util.TreeMap<>();
        for (String namespaceId : removedNamespaceIds) {
            String normalized = normalizeNamespace(namespaceId);
            if (!normalized.isBlank()) {
                unique.putIfAbsent(normalized, normalized);
            }
        }
        return unique.isEmpty() ? List.of() : List.copyOf(unique.values());
    }

    private static String normalizeNamespace(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
