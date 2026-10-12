package emaki.jiuwu.craft.corelib.api.furniture;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Bridge for resolving third-party furniture identities from world state.
 *
 * <p>Implementations reverse-look-up which furniture occupies an entity or anchor block, mirroring
 * {@link emaki.jiuwu.craft.corelib.api.integration.CustomBlockBridge} for custom blocks.
 */
@ApiStatus.Experimental
public interface FurnitureBridge {

    /** {@return whether the backing furniture plugin is present and this bridge can answer queries} */
    boolean available();

    /**
     * Resolves the furniture carried by an entity.
     *
     * @return the {@code namespace:id} furniture identity in lower case, or {@code null} when the
     *         entity is not furniture of this source
     */
    @Nullable
    String identify(Entity entity);

    /**
     * Resolves the furniture occupying an anchor block.
     *
     * @return the {@code namespace:id} furniture identity in lower case, or {@code null} when the
     *         block does not hold furniture of this source
     */
    @Nullable
    String identifyAt(Block block);
}
