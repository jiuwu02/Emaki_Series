package emaki.jiuwu.craft.structure.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Fired synchronously whenever EmakiStructure accepts a multiblock trigger from a player
 * (after condition and cooldown checks pass, right before the configured actions run).
 *
 * <p>The event is informational and non-cancellable: cancelling it has no effect on action
 * execution. It is fired on the thread that handled the interaction, which on Folia is the
 * region thread owning the clicked block.
 */
public final class MultiblockTriggerEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String multiblockId;
    private final Player player;
    private final String worldName;
    private final int x;
    private final int y;
    private final int z;
    private final String facing;

    /**
     * Creates a new trigger event.
     *
     * @param multiblockId the multiblock definition id that triggered
     * @param player       the player whose interaction triggered the structure
     * @param worldName    the world containing the structure anchor
     * @param x            anchor block X coordinate
     * @param y            anchor block Y coordinate
     * @param z            anchor block Z coordinate
     * @param facing       the structure facing in config form ({@code north}/{@code east}/
     *                     {@code south}/{@code west} plus {@code _mirror} variants)
     */
    public MultiblockTriggerEvent(String multiblockId, Player player, String worldName,
            int x, int y, int z, String facing) {
        this.multiblockId = multiblockId;
        this.player = player;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.facing = facing;
    }

    /**
     * {@return the multiblock definition id that triggered}
     */
    public String getMultiblockId() {
        return multiblockId;
    }

    /**
     * {@return the player whose interaction triggered the structure; never null}
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * {@return the unique id of the triggering player}
     */
    public UUID getPlayerId() {
        return player.getUniqueId();
    }

    /**
     * {@return the world name containing the structure anchor}
     */
    public String getWorldName() {
        return worldName;
    }

    /**
     * {@return anchor block X coordinate}
     */
    public int getX() {
        return x;
    }

    /**
     * {@return anchor block Y coordinate}
     */
    public int getY() {
        return y;
    }

    /**
     * {@return anchor block Z coordinate}
     */
    public int getZ() {
        return z;
    }

    /**
     * {@return the structure facing in config form}
     */
    public String getFacing() {
        return facing;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
