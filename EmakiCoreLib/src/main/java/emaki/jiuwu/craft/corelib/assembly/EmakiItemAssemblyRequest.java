package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.item.ItemSource;

public record EmakiItemAssemblyRequest(ItemSource baseSource,
        int amount,
        ItemStack existingItem,
        List<EmakiItemLayerSnapshot> layerSnapshots) {

    public EmakiItemAssemblyRequest(ItemSource baseSource,
            int amount,
            ItemStack existingItem,
            Collection<EmakiItemLayerSnapshot> layerSnapshots) {
        this(baseSource, amount, existingItem, copyLayers(layerSnapshots));
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
