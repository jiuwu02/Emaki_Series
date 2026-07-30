package emaki.jiuwu.craft.level.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;

/** Read-only level type, player progress, ranking and experience-rule queries. */
@ApiStatus.NonExtendable
public interface LevelCatalog {

    /**
     * {@return every loaded level type; empty when none are loaded or the API is unavailable}
     *
     * <p><strong>Thread:</strong> any thread.
     */
    @NotNull
    List<LevelTypeView> types();

    /**
     * {@return the matching level type, or empty when absent or unavailable}
     *
     * <p><strong>Thread:</strong> any thread.
     */
    @NotNull
    Optional<LevelTypeView> type(@Nullable String typeId);

    /** <strong>Thread:</strong> any thread; reads the in-memory player snapshot. */
    @NotNull
    EmakiResult<Integer> level(@Nullable UUID uuid, @Nullable String typeId);

    /** <strong>Thread:</strong> any thread; reads the in-memory player snapshot. */
    @NotNull
    EmakiResult<Double> exp(@Nullable UUID uuid, @Nullable String typeId);

    /** <strong>Thread:</strong> any thread; reads the in-memory player snapshot. */
    @NotNull
    EmakiResult<Double> totalExp(@Nullable UUID uuid, @Nullable String typeId);

    /** <strong>Thread:</strong> any thread; reads loaded requirements and the in-memory snapshot. */
    @NotNull
    EmakiResult<Double> requiredExp(@Nullable UUID uuid, @Nullable String typeId, int targetLevel);

    /**
     * Loads a player snapshot through EmakiLevel's asynchronous data store.
     *
     * <p><strong>Thread:</strong> any thread. The returned future does not define its completion thread.
     */
    @NotNull
    CompletableFuture<EmakiResult<PlayerLevelView>> loadPlayerDataAsync(@Nullable UUID uuid);

    /** <strong>Thread:</strong> any thread; reads the immutable leaderboard snapshot. */
    @NotNull
    EmakiResult<List<LevelTopEntry>> top(@Nullable String typeId, int limit);

    /** <strong>Thread:</strong> any thread; reads the immutable leaderboard snapshot. */
    @NotNull
    EmakiResult<Integer> topCount(@Nullable String typeId);

    /**
     * Previews multiplier and daily-quota adjustment without recording any gained experience.
     *
     * <p><strong>Thread:</strong> any thread.
     */
    @NotNull
    EmakiResult<LevelExpAdjustmentView> previewAdjustment(@Nullable UUID uuid,
            @Nullable String typeId,
            double amount,
            @Nullable String reason);
}
