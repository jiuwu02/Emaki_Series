package emaki.jiuwu.craft.corelib.integration.furniture;

import java.util.Locale;

import emaki.jiuwu.craft.corelib.api.furniture.FurnitureBridge;
import io.th0rgal.oraxen.api.OraxenFurniture;
import io.th0rgal.oraxen.mechanics.provided.gameplay.furniture.FurnitureMechanic;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;

final class OraxenFurnitureBridgeApi implements FurnitureBridge {

    static final String NAMESPACE = "oraxen";

    @Override
    public boolean available() {
        try {
            Class.forName("io.th0rgal.oraxen.api.OraxenFurniture");
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
            return normalize(OraxenFurniture.getFurnitureMechanic(entity));
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
            return normalize(OraxenFurniture.getFurnitureMechanic(block));
        } catch (RuntimeException | LinkageError exception) {
            return null;
        }
    }

    private static String normalize(FurnitureMechanic mechanic) {
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
