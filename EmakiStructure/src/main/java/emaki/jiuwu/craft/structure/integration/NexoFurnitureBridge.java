package emaki.jiuwu.craft.structure.integration;

import com.nexomc.nexo.api.NexoFurniture;
import com.nexomc.nexo.mechanics.furniture.FurnitureMechanic;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;

import java.util.Locale;

public final class NexoFurnitureBridge {

    public static final String NAMESPACE = "nexo";

    public String identify(Entity entity) {
        if (entity == null) {
            return null;
        }
        FurnitureMechanic mechanic = NexoFurniture.furnitureMechanic(entity);
        return normalize(mechanic);
    }

    public String identifyAt(Block block) {
        if (block == null) {
            return null;
        }
        FurnitureMechanic mechanic = NexoFurniture.furnitureMechanic(block);
        return normalize(mechanic);
    }

    private String normalize(FurnitureMechanic mechanic) {
        if (mechanic == null) {
            return null;
        }
        String itemID = mechanic.getItemID();
        if (itemID == null || itemID.isBlank()) {
            return null;
        }
        String normalized = itemID.toLowerCase(Locale.ROOT);
        return normalized.indexOf(':') < 0 ? NAMESPACE + ":" + normalized : normalized;
    }
}
