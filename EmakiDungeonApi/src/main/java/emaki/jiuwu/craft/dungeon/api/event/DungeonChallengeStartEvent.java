package emaki.jiuwu.craft.dungeon.api.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Preamble event fired when a challenge session passes its setup checks and
 * the preparation window is about to open.
 *
 * <p>This is a preamble event: the challenge itself has not started yet.
 * The event implements {@link org.bukkit.event.Cancellable}; cancelling it
 * aborts the session, and the entry API call reports a cancelled result.
 *
 * <p>Fired synchronously on the server thread that processed the entry
 * request or the leader's difficulty and drop-mode selection.
 *
 * <p>Coverage is challenge-wide: one event per challenge session start,
 * carrying the dungeon id, the preparation window length, the picked
 * difficulty, and the picked drop mode. It carries no instance id because
 * no instance world exists yet.
 *
 * <p>Not fired when the picked difficulty or drop mode fails validation,
 * when an earlier check aborts the session, or when the session is no
 * longer the active one for its party.
 */
public class DungeonChallengeStartEvent extends org.bukkit.event.Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String dungeonId;
    private final int windowSeconds;
    private final String difficulty;
    private final String dropMode;
    private boolean cancelled;

    /**
     * Creates a challenge start event.
     *
     * @param dungeonId the dungeon the challenge is starting for
     * @param windowSeconds the preparation window length in seconds
     * @param difficulty the picked difficulty id
     * @param dropMode the picked drop mode id
     */
    public DungeonChallengeStartEvent(String dungeonId, int windowSeconds,
            String difficulty, String dropMode) {
        this.dungeonId = dungeonId;
        this.windowSeconds = windowSeconds;
        this.difficulty = difficulty;
        this.dropMode = dropMode;
    }

    /** {@return the id of the dungeon the challenge starts for} */
    public String getDungeonId() {
        return dungeonId;
    }

    /** {@return the preparation window length in seconds} */
    public int getWindowSeconds() {
        return windowSeconds;
    }

    /** {@return the picked difficulty id} */
    public String getDifficulty() {
        return difficulty;
    }

    /** {@return the picked drop mode id} */
    public String getDropMode() {
        return dropMode;
    }

    /** {@return whether a listener has cancelled the challenge start} */
    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Cancels or uncancels the challenge start; a cancelled start aborts the
     * challenge session.
     *
     * @param cancel {@code true} to cancel the challenge start
     */
    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    /** {@return the handlers of this event} */
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /** {@return the static handler list for this event type} */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
