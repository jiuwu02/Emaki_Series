package emaki.jiuwu.craft.structure.integration;

import net.momirealms.craftengine.bukkit.entity.furniture.BukkitFurnitureManager;
import net.momirealms.craftengine.core.entity.furniture.Furniture;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;

import java.util.Locale;

public final class CraftEngineFurnitureBridge {

    public static final String NAMESPACE = "craftengine";

    public String identify(Entity entity) {
        if (entity == null) {
            return null;
        }
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
    }

    public String identifyAt(Block block) {
        return null;
    }
}
