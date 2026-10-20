package emaki.jiuwu.craft.dungeon.api.event;

import org.bukkit.event.HandlerList;

public class DungeonExitEvent extends DungeonEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String player;
    private final String reason;

    public DungeonExitEvent(String instanceId, String dungeonId, String player, String reason) {
        super(instanceId, dungeonId);
        this.player = player;
        this.reason = reason == null ? "" : reason;
    }

    public String getPlayer() {
        return player;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
