package emaki.jiuwu.craft.structure.integration.furniture;

import net.momirealms.craftengine.bukkit.api.CraftEngineFurniture;
import net.momirealms.craftengine.bukkit.entity.furniture.BukkitFurniture;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

final class CraftEngineFurnitureRestorer implements FurnitureRestorer {

    @Override
    public Entity place(String rawId, Location location) {
        BukkitFurniture furniture = CraftEngineFurniture.place(location, Key.of(rawId));
        if (furniture == null) {
            return null;
        }
        return furniture.baseEntity();
    }

    @Override
    public boolean remove(Entity entity) {
        return CraftEngineFurniture.remove(entity);
    }
}
