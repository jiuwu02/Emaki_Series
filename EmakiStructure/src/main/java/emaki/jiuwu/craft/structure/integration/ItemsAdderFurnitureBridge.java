package emaki.jiuwu.craft.structure.integration;

import dev.lone.itemsadder.api.CustomFurniture;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;

import java.util.Locale;

public final class ItemsAdderFurnitureBridge {

    public static final String NAMESPACE = "itemsadder";

    public String identify(Entity entity) {
        if (entity == null) {
            return null;
        }
        CustomFurniture furniture = CustomFurniture.byAlreadySpawned(entity);
        return normalize(furniture);
    }

    public String identifyAt(Block block) {
        if (block == null) {
            return null;
        }
        CustomFurniture furniture = CustomFurniture.byAlreadySpawned(block);
        return normalize(furniture);
    }

    private String normalize(CustomFurniture furniture) {
        if (furniture == null) {
            return null;
        }
        String namespaced = furniture.getNamespacedID();
        if (namespaced == null || namespaced.isBlank()) {
            return null;
        }
        String normalized = namespaced.toLowerCase(Locale.ROOT);
        return normalized.indexOf(':') < 0 ? NAMESPACE + ":" + normalized : normalized;
    }
}
