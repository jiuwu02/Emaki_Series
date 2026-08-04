package emaki.jiuwu.craft.station.queue;

import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.station.api.model.ConsumedMaterial;
import emaki.jiuwu.craft.station.api.model.MaterialChannel;
import emaki.jiuwu.craft.station.api.model.PendingOutput;
import emaki.jiuwu.craft.station.api.model.ProgressMode;
import emaki.jiuwu.craft.station.api.model.QueueEntryState;
import emaki.jiuwu.craft.station.api.model.QueueEntryView;

/**
 * One queued craft, mutable while it lives in a {@link CraftQueue}.
 *
 * <h2>One shape for both progress modes</h2>
 * Both timing fields are always persisted, and only the advance function differs. This is what lets an
 * administrator flip a station between {@code online} and {@code offline} without migrating queue files:
 *
 * <ul>
 *   <li>{@code offline} reads {@code startedAtMs} and compares it to the wall clock.</li>
 *   <li>{@code online} accumulates into {@code accumulatedMs} while {@code lastTickMs} is non-zero, and
 *       freezes by zeroing {@code lastTickMs} on disconnect.</li>
 * </ul>
 *
 * <p>Not thread-safe. Every mutation happens on the owner's owner thread, and {@link CraftQueue} owns the
 * synchronisation for its entry list.
 */
public final class QueueEntry {

    private final String recipeId;
    private final long batch;
    private final MaterialChannel channel;
    private final long durationMillis;
    private final List<ConsumedMaterial> consumedMaterials;
    private final List<PendingOutput> pendingOutputs = new ArrayList<>();

    private QueueEntryState state;
    private long startedAtMs;
    private long accumulatedMs;
    private long lastTickMs;

    /**
     * Creates an entry.
     *
     * @param recipeId          the recipe being crafted
     * @param batch             how many times the recipe is applied
     * @param channel           where the materials came from
     * @param durationMillis    the entry's total duration
     * @param consumedMaterials the materials already debited
     * @param state             the initial state
     * @param startedAtMs       the wall-clock start, or zero when not started
     * @param accumulatedMs     online progress accumulated so far
     * @param lastTickMs        the last online tick timestamp, or zero when frozen
     */
    public QueueEntry(String recipeId,
            long batch,
            MaterialChannel channel,
            long durationMillis,
            List<ConsumedMaterial> consumedMaterials,
            QueueEntryState state,
            long startedAtMs,
            long accumulatedMs,
            long lastTickMs) {
        this.recipeId = recipeId;
        this.batch = Math.max(1L, batch);
        this.channel = channel == null ? MaterialChannel.BACKPACK : channel;
        this.durationMillis = Math.max(0L, durationMillis);
        this.consumedMaterials = consumedMaterials == null
                ? new ArrayList<>()
                : new ArrayList<>(consumedMaterials);
        this.state = state == null ? QueueEntryState.WAITING : state;
        this.startedAtMs = Math.max(0L, startedAtMs);
        this.accumulatedMs = Math.max(0L, accumulatedMs);
        this.lastTickMs = Math.max(0L, lastTickMs);
    }

    /** {@return the recipe being crafted} */
    public String recipeId() {
        return recipeId;
    }

    /** {@return how many times the recipe is applied} */
    public long batch() {
        return batch;
    }

    /** {@return where the materials came from} */
    public MaterialChannel channel() {
        return channel;
    }

    /** {@return the entry's total duration in milliseconds} */
    public long durationMillis() {
        return durationMillis;
    }

    /** {@return the materials already debited; a live view} */
    public List<ConsumedMaterial> consumedMaterials() {
        return consumedMaterials;
    }

    /** {@return the outputs still owed; a live view} */
    public List<PendingOutput> pendingOutputs() {
        return pendingOutputs;
    }

    /** {@return the current lifecycle state} */
    public QueueEntryState state() {
        return state;
    }

    /** {@return the wall-clock start, or zero when not started} */
    public long startedAtMs() {
        return startedAtMs;
    }

    /** {@return online progress accumulated so far} */
    public long accumulatedMs() {
        return accumulatedMs;
    }

    /** {@return the last online tick timestamp, or zero when frozen} */
    public long lastTickMs() {
        return lastTickMs;
    }

    /**
     * Marks this entry as the running head of its queue.
     *
     * @param mode the station's progress mode
     * @param now  the current wall-clock time
     */
    public void start(ProgressMode mode, long now) {
        if (state != QueueEntryState.WAITING) {
            return;
        }
        state = QueueEntryState.RUNNING;
        startedAtMs = now;
        if (mode == ProgressMode.ONLINE) {
            lastTickMs = now;
        }
    }

    /**
     * Folds elapsed online time into the accumulator and freezes the clock.
     *
     * <p>Called on disconnect and before persisting, so a saved entry never carries a {@code lastTickMs}
     * that would be reinterpreted as progress after a restart.
     *
     * @param now the current wall-clock time
     */
    public void freezeOnlineProgress(long now) {
        if (lastTickMs > 0L && now > lastTickMs) {
            accumulatedMs += now - lastTickMs;
        }
        lastTickMs = 0L;
    }

    /**
     * Resumes online accumulation.
     *
     * @param now the current wall-clock time
     */
    public void resumeOnlineProgress(long now) {
        if (state == QueueEntryState.RUNNING) {
            lastTickMs = now;
        }
    }

    /**
     * Computes how much time this entry still needs.
     *
     * @param mode the station's progress mode
     * @param now  the current wall-clock time
     * @return the remaining milliseconds; zero once the entry is due
     */
    public long remainingMillis(ProgressMode mode, long now) {
        if (state != QueueEntryState.RUNNING) {
            return state == QueueEntryState.PENDING_CLAIM ? 0L : durationMillis;
        }
        long elapsed = mode == ProgressMode.ONLINE
                ? accumulatedMs + (lastTickMs > 0L && now > lastTickMs ? now - lastTickMs : 0L)
                : Math.max(0L, now - startedAtMs);
        return Math.max(0L, durationMillis - elapsed);
    }

    /**
     * Tests whether this entry has reached its duration.
     *
     * @param mode the station's progress mode
     * @param now  the current wall-clock time
     * @return whether the entry is ready to settle
     */
    public boolean due(ProgressMode mode, long now) {
        return state == QueueEntryState.RUNNING && remainingMillis(mode, now) <= 0L;
    }

    /**
     * Moves this entry into the pending-claim state with the outputs it still owes.
     *
     * @param outputs the undelivered outputs
     */
    public void markPendingClaim(List<PendingOutput> outputs) {
        state = QueueEntryState.PENDING_CLAIM;
        pendingOutputs.clear();
        if (outputs != null) {
            pendingOutputs.addAll(outputs);
        }
        lastTickMs = 0L;
    }

    /** Clears every owed output, which is what a completed claim leaves behind. */
    public void clearPendingOutputs() {
        pendingOutputs.clear();
    }

    /**
     * Builds an API view of this entry.
     *
     * @param index the entry's current queue position
     * @param mode  the station's progress mode
     * @param now   the current wall-clock time
     * @return the view
     */
    public QueueEntryView toView(int index, ProgressMode mode, long now) {
        return new QueueEntryView(index,
                recipeId,
                batch,
                state,
                channel,
                remainingMillis(mode, now),
                durationMillis,
                List.copyOf(consumedMaterials),
                List.copyOf(pendingOutputs));
    }
}
