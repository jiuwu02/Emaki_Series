package emaki.jiuwu.craft.corelib.integration.furniture;

import java.util.Locale;

import dev.lone.itemsadder.api.CustomFurniture;
import emaki.jiuwu.craft.corelib.api.furniture.FurnitureBridge;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;

final class ItemsAdderFurnitureBridgeApi implements FurnitureBridge {

    static final String NAMESPACE = "itemsadder";

    @Override
    public boolean available() {
        try {
            Class.forName("dev.lone.itemsadder.api.CustomFurniture");
            return true;
        } catch (RuntimeException | LinkageError | ClassNotFoundException exception) {
            return false;
        }
    }

    @Override
    public String identify(Entity entity) {
        if (entity == null) {
            return null;
        }
        try {
            return normalize(CustomFurniture.byAlreadySpawned(entity));
        } catch (RuntimeException | LinkageError exception) {
            return null;
        }
    }

    @Override
    public String identifyAt(Block block) {
        if (block == null) {
            return null;
        }
        try {
            return normalize(CustomFurniture.byAlreadySpawned(block));
        } catch (RuntimeException | LinkageError exception) {
            return null;
        }
    }

    private static String normalize(CustomFurniture furniture) {
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
