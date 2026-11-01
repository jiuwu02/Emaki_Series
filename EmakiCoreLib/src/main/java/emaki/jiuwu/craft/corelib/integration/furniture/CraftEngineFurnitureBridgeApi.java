package emaki.jiuwu.craft.corelib.integration.furniture;

import java.util.Locale;

import emaki.jiuwu.craft.corelib.api.furniture.FurnitureBridge;
import net.momirealms.craftengine.bukkit.entity.furniture.BukkitFurnitureManager;
import net.momirealms.craftengine.core.entity.furniture.Furniture;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;

final class CraftEngineFurnitureBridgeApi implements FurnitureBridge {

    static final String NAMESPACE = "craftengine";

    @Override
    public boolean available() {
        try {
            Class.forName("net.momirealms.craftengine.bukkit.entity.furniture.BukkitFurnitureManager");
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
            Furniture furniture = BukkitFurnitureManager.instance()
                    .loadedFurnitureByMetaEntityId(entity.getEntityId());
            if (furniture == null) {
                return null;
            }
            String key = furniture.id().toString();
            if (key == null || key.isBlank()) {
                return null;
            }
            String normalized = key.toLowerCase(Locale.ROOT);
            return normalized.indexOf(':') < 0 ? NAMESPACE + ":" + normalized : normalized;
        } catch (RuntimeException | LinkageError exception) {
            return null;
        }
    }

    @Override
    public String identifyAt(Block block) {
        return null;
    }
}
