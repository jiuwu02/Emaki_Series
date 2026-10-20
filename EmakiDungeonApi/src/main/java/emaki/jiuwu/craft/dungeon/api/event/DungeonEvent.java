package emaki.jiuwu.craft.dungeon.api.event;

import org.bukkit.event.Event;

public abstract class DungeonEvent extends Event {

    private final String instanceId;
    private final String dungeonId;

    protected DungeonEvent(String instanceId, String dungeonId) {
        this.instanceId = instanceId;
        this.dungeonId = dungeonId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getDungeonId() {
        return dungeonId;
    }
}
