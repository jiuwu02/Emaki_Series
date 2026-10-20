package emaki.jiuwu.craft.dungeon.api.event;

import org.bukkit.event.HandlerList;

public class DungeonFailEvent extends DungeonEvent {

    public enum Reason {
        ALL_DEAD,
        TIMEOUT,
        SCRIPTED,
        CRASH,
        OTHER
    }

    private static final HandlerList HANDLERS = new HandlerList();

    private final Reason reason;
    private final String detail;

    public DungeonFailEvent(String instanceId, String dungeonId, Reason reason, String detail) {
        super(instanceId, dungeonId);
        this.reason = reason;
        this.detail = detail == null ? "" : detail;
    }

    public Reason getReason() {
        return reason;
    }

    public String getDetail() {
        return detail;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
