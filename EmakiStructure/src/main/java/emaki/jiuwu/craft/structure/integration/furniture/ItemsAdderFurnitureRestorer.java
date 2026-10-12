package emaki.jiuwu.craft.structure.integration.furniture;

import dev.lone.itemsadder.api.CustomFurniture;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

final class ItemsAdderFurnitureRestorer implements FurnitureRestorer {

    @Override
    public Entity place(String rawId, Location location) {
        CustomFurniture furniture = CustomFurniture.spawnPreciseNonSolid(rawId, location);
        if (furniture == null) {
            return null;
        }
        Entity entity = furniture.getEntity();
        if (entity != null) {
            entity.teleport(location);
        }
        return entity;
    }

    @Override
    public boolean remove(Entity entity) {
        CustomFurniture.remove(entity, false);
        return entity.isDead();
    }
}
