package emaki.jiuwu.craft.corelib.integration.furniture;

import java.util.Locale;

import com.nexomc.nexo.api.NexoFurniture;
import com.nexomc.nexo.mechanics.furniture.FurnitureMechanic;
import emaki.jiuwu.craft.corelib.api.furniture.FurnitureBridge;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;

final class NexoFurnitureBridgeApi implements FurnitureBridge {

    static final String NAMESPACE = "nexo";

    @Override
    public boolean available() {
        try {
            Class.forName("com.nexomc.nexo.api.NexoFurniture");
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
            return normalize(NexoFurniture.furnitureMechanic(entity));
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
            return normalize(NexoFurniture.furnitureMechanic(block));
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
