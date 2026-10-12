package emaki.jiuwu.craft.structure.integration.furniture;

import com.nexomc.nexo.api.NexoFurniture;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

final class NexoFurnitureRestorer implements FurnitureRestorer {

    @Override
    public Entity place(String rawId, Location location) {
        return NexoFurniture.place(rawId, location, location.getYaw(),
                FurnitureRestorer.faceOf(location.getYaw()));
    }

    @Override
    public boolean remove(Entity entity) {
        return NexoFurniture.remove(entity);
    }
}
