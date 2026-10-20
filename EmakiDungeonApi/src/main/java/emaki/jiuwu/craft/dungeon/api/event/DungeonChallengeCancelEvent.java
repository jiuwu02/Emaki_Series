package emaki.jiuwu.craft.dungeon.api.event;

import org.bukkit.event.HandlerList;

public class DungeonChallengeCancelEvent extends org.bukkit.event.Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String dungeonId;
    private final String reason;

    public DungeonChallengeCancelEvent(String dungeonId, String reason) {
        this.dungeonId = dungeonId;
        this.reason = reason == null ? "" : reason;
    }

    public String getDungeonId() {
        return dungeonId;
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
