package emaki.jiuwu.craft.dungeon.api.event;

import org.bukkit.event.HandlerList;

public class DungeonEndingEvent extends DungeonEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String endingId;
    private final String optionCase;

    public DungeonEndingEvent(String instanceId, String dungeonId, String endingId, String optionCase) {
        super(instanceId, dungeonId);
        this.endingId = endingId == null ? "" : endingId;
        this.optionCase = optionCase == null ? "" : optionCase;
    }

    public String getEndingId() {
        return endingId;
    }

    public String getOptionCase() {
        return optionCase;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
