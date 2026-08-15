package emaki.jiuwu.craft.station.queue;

import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.station.api.model.ConsumedMaterial;
import emaki.jiuwu.craft.station.api.model.MaterialChannel;
import emaki.jiuwu.craft.station.api.model.PendingOutput;
import emaki.jiuwu.craft.station.api.model.ProgressMode;
import emaki.jiuwu.craft.station.api.model.QueueEntryState;
import emaki.jiuwu.craft.station.api.model.QueueEntryView;

public final class QueueEntry {

    private final String recipeId;
    private final long batch;
    private final MaterialChannel channel;
    private final long durationMillis;
    private final List<ConsumedMaterial> consumedMaterials;
    private final List<PendingOutput> pendingOutputs = new ArrayList<>();
    private final String costProviderId;
    private final long costAmount;

    private QueueEntryState state;
    private long startedAtMs;
    private long accumulatedMs;
    private long lastTickMs;

    public QueueEntry(String recipeId,
            long batch,
            MaterialChannel channel,
            long durationMillis,
            List<ConsumedMaterial> consumedMaterials,
            QueueEntryState state,
            long startedAtMs,
            long accumulatedMs,
            long lastTickMs,
            String costProviderId,
            long costAmount) {
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
        String provider = costProviderId == null ? "" : costProviderId.trim();
        long charged = Math.max(0L, costAmount);
        this.costProviderId = provider.isEmpty() || charged == 0L ? "" : provider;
        this.costAmount = this.costProviderId.isEmpty() ? 0L : charged;
    }

    public String recipeId() {
        return recipeId;
    }

    public long batch() {
        return batch;
    }

    public MaterialChannel channel() {
        return channel;
    }

    public long durationMillis() {
        return durationMillis;
    }

    public List<ConsumedMaterial> consumedMaterials() {
        return consumedMaterials;
    }

    public List<PendingOutput> pendingOutputs() {
        return pendingOutputs;
    }

    public QueueEntryState state() {
        return state;
    }

    public long startedAtMs() {
        return startedAtMs;
    }

    public long accumulatedMs() {
        return accumulatedMs;
    }

    public long lastTickMs() {
        return lastTickMs;
    }

    public String costProviderId() {
        return costProviderId;
    }

    public long costAmount() {
        return costAmount;
    }

    public boolean charged() {
        return !costProviderId.isEmpty() && costAmount > 0L;
    }

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

    public void freezeOnlineProgress(long now) {
        if (lastTickMs > 0L && now > lastTickMs) {
            accumulatedMs += now - lastTickMs;
        }
        lastTickMs = 0L;
    }

    public void resumeOnlineProgress(long now) {
        if (state == QueueEntryState.RUNNING) {
            lastTickMs = now;
        }
    }

    public long remainingMillis(ProgressMode mode, long now) {
        if (state != QueueEntryState.RUNNING) {
            return state == QueueEntryState.PENDING_CLAIM ? 0L : durationMillis;
        }
        long elapsed = mode == ProgressMode.ONLINE
                ? accumulatedMs + (lastTickMs > 0L && now > lastTickMs ? now - lastTickMs : 0L)
                : Math.max(0L, now - startedAtMs);
        return Math.max(0L, durationMillis - elapsed);
    }

    public boolean due(ProgressMode mode, long now) {
        return state == QueueEntryState.RUNNING && remainingMillis(mode, now) <= 0L;
    }

    public void markPendingClaim(List<PendingOutput> outputs) {
        state = QueueEntryState.PENDING_CLAIM;
        pendingOutputs.clear();
        if (outputs != null) {
            pendingOutputs.addAll(outputs);
        }
        lastTickMs = 0L;
    }

    public void clearPendingOutputs() {
        pendingOutputs.clear();
    }

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
