package emaki.jiuwu.craft.station.api.model;

import java.util.List;

import org.jetbrains.annotations.NotNull;

public record QueueEntryView(int index,
        int schemaVersion,
        @NotNull String recipeIdentity,
        @NotNull String recipeId,
        long batch,
        @NotNull QueueEntryState state,
        @NotNull MaterialChannel channel,
        long remainingMillis,
        long durationMillis,
        @NotNull List<ConsumedMaterial> consumedMaterials,
        @NotNull List<PendingOutput> pendingOutputs) {

    public QueueEntryView {
        if (recipeIdentity == null) {
            throw new NullPointerException("recipeIdentity");
        }
        if (recipeId == null) {
            throw new NullPointerException("recipeId");
        }
        if (state == null) {
            throw new NullPointerException("state");
        }
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        schemaVersion = Math.max(1, schemaVersion);
        remainingMillis = Math.max(0L, remainingMillis);
        consumedMaterials = consumedMaterials == null ? List.of() : List.copyOf(consumedMaterials);
        pendingOutputs = pendingOutputs == null ? List.of() : List.copyOf(pendingOutputs);
    }

    public QueueEntryView(int index,
            String recipeId,
            long batch,
            QueueEntryState state,
            MaterialChannel channel,
            long remainingMillis,
            long durationMillis,
            List<ConsumedMaterial> consumedMaterials,
            List<PendingOutput> pendingOutputs) {
        this(index, 1, recipeId, recipeId, batch, state, channel, remainingMillis, durationMillis,
                consumedMaterials, pendingOutputs);
    }

    public boolean awaitingClaim() {
        return state == QueueEntryState.PENDING_CLAIM;
    }
}
