package emaki.jiuwu.craft.structure.integration.furniture;

import io.th0rgal.oraxen.api.OraxenFurniture;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

final class OraxenFurnitureRestorer implements FurnitureRestorer {

    @Override
    public Entity place(String rawId, Location location) {
        return OraxenFurniture.place(rawId, location, location.getYaw(),
                FurnitureRestorer.faceOf(location.getYaw()));
    }

    @Override
    public boolean remove(Entity entity) {
        return OraxenFurniture.remove(entity, null);
    }
}
