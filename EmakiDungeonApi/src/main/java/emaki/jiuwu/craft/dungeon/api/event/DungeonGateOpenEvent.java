package emaki.jiuwu.craft.dungeon.api.event;

import org.bukkit.event.HandlerList;

public class DungeonGateOpenEvent extends DungeonEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String gateId;
    private final String condition;

    public DungeonGateOpenEvent(String instanceId, String dungeonId,
            String gateId, String condition) {
        super(instanceId, dungeonId);
        this.gateId = gateId == null ? "" : gateId;
        this.condition = condition == null ? "" : condition;
    }

    public String getGateId() {
        return gateId;
    }

    public String getCondition() {
        return condition;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
