package emaki.jiuwu.craft.dungeon.api.event;

import org.bukkit.event.Event;

/**
 * Base type for the dungeon flow events published by EmakiDungeon.
 *
 * <p>Concrete subclasses carry the instance and dungeon identity of the
 * occurrence as their first two constructor arguments, plus their own
 * occurrence payload.
 */
public abstract class DungeonEvent extends Event {

    private final String instanceId;
    private final String dungeonId;

    /**
     * Creates a dungeon event bound to an instance and dungeon.
     *
     * @param instanceId the instance this event belongs to
     * @param dungeonId the dungeon definition this event belongs to
     */
    protected DungeonEvent(String instanceId, String dungeonId) {
        this.instanceId = instanceId;
        this.dungeonId = dungeonId;
    }

    /**
     * {@return the id of the instance this event belongs to}, which is also
     * the name of the instance world
     */
    public String getInstanceId() {
        return instanceId;
    }

    /** {@return the id of the dungeon definition this event belongs to} */
    public String getDungeonId() {
        return dungeonId;
    }
}
