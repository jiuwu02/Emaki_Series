package emaki.jiuwu.craft.structure.integration;

import io.th0rgal.oraxen.api.OraxenFurniture;
import io.th0rgal.oraxen.mechanics.provided.gameplay.furniture.FurnitureMechanic;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;

import java.util.Locale;

public final class OraxenFurnitureBridge {

    public static final String NAMESPACE = "oraxen";

    public String identify(Entity entity) {
        if (entity == null) {
            return null;
        }
        FurnitureMechanic mechanic = OraxenFurniture.getFurnitureMechanic(entity);
        return normalize(mechanic);
    }

    public String identifyAt(Block block) {
        if (block == null) {
            return null;
        }
        FurnitureMechanic mechanic = OraxenFurniture.getFurnitureMechanic(block);
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
