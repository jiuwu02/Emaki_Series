package emaki.jiuwu.craft.dungeon.api.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

public class DungeonChallengeStartEvent extends org.bukkit.event.Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String dungeonId;
    private final int windowSeconds;
    private final String difficulty;
    private final String dropMode;
    private boolean cancelled;

    public DungeonChallengeStartEvent(String dungeonId, int windowSeconds,
            String difficulty, String dropMode) {
        this.dungeonId = dungeonId;
        this.windowSeconds = windowSeconds;
        this.difficulty = difficulty;
        this.dropMode = dropMode;
    }

    public String getDungeonId() {
        return dungeonId;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public String getDropMode() {
        return dropMode;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
