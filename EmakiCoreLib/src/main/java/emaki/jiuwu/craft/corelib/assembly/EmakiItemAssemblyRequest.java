package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.item.ItemSource;

public record EmakiItemAssemblyRequest(ItemSource baseSource,
        int amount,
        ItemStack existingItem,
        List<EmakiItemLayerSnapshot> layerSnapshots,
        UUID feedbackPlayerId) {

    public EmakiItemAssemblyRequest(ItemSource baseSource,
            int amount,
            ItemStack existingItem,
            Collection<EmakiItemLayerSnapshot> layerSnapshots) {
        this(baseSource, amount, existingItem, layerSnapshots, null);
    }

    public EmakiItemAssemblyRequest(ItemSource baseSource,
            int amount,
            ItemStack existingItem,
            Collection<EmakiItemLayerSnapshot> layerSnapshots,
            UUID feedbackPlayerId) {
        this(baseSource, amount, existingItem, copyLayers(layerSnapshots), feedbackPlayerId);
    }

    public EmakiItemAssemblyRequest withFeedbackPlayerId(UUID playerId) {
        return new EmakiItemAssemblyRequest(baseSource, amount, existingItem, layerSnapshots, playerId);
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
}
