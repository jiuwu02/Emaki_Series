package emaki.jiuwu.craft.dungeon.api.event;

import org.bukkit.event.HandlerList;

public class DungeonCompleteEvent extends DungeonEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final double score;
    private final long durationMs;

    public DungeonCompleteEvent(String instanceId, String dungeonId, double score, long durationMs) {
        super(instanceId, dungeonId);
        this.score = score;
        this.durationMs = durationMs;
    }

    public double getScore() {
        return score;
    }

    public long getDurationMs() {
        return durationMs;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
