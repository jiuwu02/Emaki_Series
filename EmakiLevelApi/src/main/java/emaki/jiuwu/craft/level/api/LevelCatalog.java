package emaki.jiuwu.craft.level.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;

/**
 * Read-only level type, player progress, ranking and experience-rule queries.
 *
 * <p>Reached through {@code EmakiLevelApi.catalog()}.
 *
 * <p><strong>Thread:</strong> every method here may be called from any thread. These queries read
 * loaded configuration and in-memory snapshots and do not touch Bukkit entity or world state, so
 * they never return {@code WRONG_THREAD}.
 *
 * <h2>Cached data, not storage reads</h2>
 * Except for {@link #loadPlayerDataAsync(UUID)}, the player-scoped queries only see data already
 * cached by the runtime, which in practice means players with an active session. A player who is
 * offline, or whose load has not finished, yields {@code NOT_FOUND} with reason key
 * {@code level.player_data_not_found} rather than a zeroed value. Use
 * {@link #loadPlayerDataAsync(UUID)} when offline data is required.
 *
 * <h2>Shared failure contract</h2>
 * Recurring classifications on the {@code EmakiResult} returning methods are {@code UNAVAILABLE}
 * when no runtime bridge is installed, {@code INVALID_INPUT} for a {@code null} uuid, a blank
 * {@code typeId} or an out-of-range numeric argument, {@code NOT_FOUND} for an unknown level type or
 * uncached player data, and {@code INTERNAL_ERROR} when the runtime throws. Branch on
 * {@code FailureKind} only and treat {@code reasonKey} as diagnostic text.
 */
@ApiStatus.NonExtendable
public interface LevelCatalog {

    /**
     * {@return every loaded level type; empty when none are loaded or the API is unavailable}
     *
     * <p>Includes types disabled by configuration; check {@code LevelTypeView.enabled()} to filter
     * them. Empty does not distinguish "no types configured" from "runtime unavailable"; use
     * {@code EmakiLevelApi.status()} for that.
     *
     * <p><strong>Thread:</strong> any thread.
     */
    @NotNull
    List<LevelTypeView> types();

    /**
     * {@return the matching level type, or empty when absent or unavailable}
     *
     * <p>The lookup is id-based and case-normalized by the runtime; it never matches on display
     * name. Empty is returned for a {@code null} or blank {@code typeId} as well as for an unknown
     * id.
     *
     * <p><strong>Thread:</strong> any thread.
     *
     * @param typeId the level type id to resolve; {@code null} or blank yields an empty result
     */
    @NotNull
    Optional<LevelTypeView> type(@Nullable String typeId);

    /**
     * Reads the player's current level in one type.
     *
     * <p><strong>Thread:</strong> any thread; reads the in-memory player snapshot.
     *
     * @param uuid   the player to read; {@code null} yields {@code INVALID_INPUT}
     * @param typeId the level type to read; must be non-blank and loaded
     * @return the stored level, or a classified failure; {@code NOT_FOUND} means the type is unknown
     *         or the player's data is not currently cached, and is never collapsed into a zero level
     */
    @NotNull
    EmakiResult<Integer> level(@Nullable UUID uuid, @Nullable String typeId);

    /**
     * Reads the experience the player has accumulated toward the next level.
     *
     * <p>This is progress within the current level, not a lifetime figure; it resets when a level is
     * gained. Use {@link #totalExp(UUID, String)} for the lifetime value.
     *
     * <p><strong>Thread:</strong> any thread; reads the in-memory player snapshot.
     *
     * @param uuid   the player to read; {@code null} yields {@code INVALID_INPUT}
     * @param typeId the level type to read; must be non-blank and loaded
     * @return the stored progress experience, or a classified failure
     */
    @NotNull
    EmakiResult<Double> exp(@Nullable UUID uuid, @Nullable String typeId);

    /**
     * Reads the player's lifetime accumulated experience in one type.
     *
     * <p>This counter is not reduced when a level is consumed, and administrative experience or level
     * writes do not lower it. It is cleared only by {@code LevelOperations.reset}.
     *
     * <p><strong>Thread:</strong> any thread; reads the in-memory player snapshot.
     *
     * @param uuid   the player to read; {@code null} yields {@code INVALID_INPUT}
     * @param typeId the level type to read; must be non-blank and loaded
     * @return the stored lifetime experience, or a classified failure
     */
    @NotNull
    EmakiResult<Double> totalExp(@Nullable UUID uuid, @Nullable String typeId);

    /**
     * Computes the experience needed to reach one specific level.
     *
     * <p>The value is the single-step requirement configured for {@code targetLevel}, resolved from
     * the type's requirement table or its formula, evaluated against the player's current entry. It
     * is not a cumulative total from level one. A type with neither a table value nor a formula for
     * that level yields {@code 0}, which the runtime treats as "no valid requirement" during an
     * actual upgrade.
     *
     * <p><strong>Thread:</strong> any thread; reads loaded requirements and the in-memory snapshot.
     *
     * @param uuid        the player whose current entry parameterizes the formula; {@code null}
     *                    yields {@code INVALID_INPUT}
     * @param typeId      the level type to read; must be non-blank and loaded
     * @param targetLevel the level to price; negative values and values outside the type's configured
     *                    start/max range yield {@code INVALID_INPUT} with reason keys
     *                    {@code level.target_level_invalid} or
     *                    {@code level.target_level_out_of_range}
     * @return the required experience for that step, or a classified failure
     */
    @NotNull
    EmakiResult<Double> requiredExp(@Nullable UUID uuid, @Nullable String typeId, int targetLevel);

    /**
     * Loads a player snapshot through EmakiLevel's asynchronous data store, reading from disk when the
     * player is not cached.
     *
     * <p>This is the only query here that reaches offline players. The returned view is a detached
     * snapshot covering every loaded type the player has an entry for, including the resolved
     * next-level requirement and progress ratio; later runtime writes are not reflected in it.
     *
     * <p><strong>Thread:</strong> may be called from any thread. Completion is asynchronous and
     * <strong>no completion thread is guaranteed</strong>: the future may complete on an internal
     * storage thread, or inline on the calling thread when input validation fails or the data is
     * already resident. EmakiLevel schedules no Bukkit phase on your behalf, so a continuation that
     * touches the player, their inventory or the world must hop to the owner thread itself, for
     * example through CoreLib scheduling.
     *
     * <p>The future completes normally with a classified failure instead of completing
     * exceptionally: {@code INVALID_INPUT} for a {@code null} uuid, and {@code INTERNAL_ERROR} with
     * {@code level.player_data_load_failed} when the load yields nothing or throws.
     *
     * @param uuid the player to load; {@code null} completes immediately with {@code INVALID_INPUT}
     * @return a future carrying the detached player snapshot or a classified failure
     */
    @NotNull
    CompletableFuture<EmakiResult<PlayerLevelView>> loadPlayerDataAsync(@Nullable UUID uuid);

    /**
     * Reads the top ranked players for one level type.
     *
     * <p>Served from a cached leaderboard snapshot that the runtime builds from all known player data
     * at startup and updates incrementally as sessions change, so it may lag slightly behind the most
     * recent writes. Entries are ordered by level descending, then lifetime total experience
     * descending, then name. Fewer entries than {@code limit} are returned when the ranking is
     * shorter, and the list is unmodifiable.
     *
     * <p><strong>Thread:</strong> any thread; reads the immutable leaderboard snapshot.
     *
     * @param typeId the level type whose ranking is read; must be non-blank and loaded
     * @param limit  maximum number of entries; must be strictly positive, otherwise
     *               {@code INVALID_INPUT} with {@code level.top_limit_invalid} is returned
     * @return the ranked entries, or a classified failure
     */
    @NotNull
    EmakiResult<List<LevelTopEntry>> top(@Nullable String typeId, int limit);

    /**
     * Counts the players present in one type's leaderboard snapshot.
     *
     * <p>Reflects the same cached snapshot as {@link #top(String, int)}, so it counts players known to
     * that snapshot rather than every player who has ever played.
     *
     * <p><strong>Thread:</strong> any thread; reads the immutable leaderboard snapshot.
     *
     * @param typeId the level type to count; must be non-blank and loaded
     * @return the number of ranked entries, or a classified failure
     */
    @NotNull
    EmakiResult<Integer> topCount(@Nullable String typeId);

    /**
     * Previews multiplier and daily-quota adjustment without recording any gained experience.
     *
     * <p>Answers "how much of this grant would actually land" before calling
     * {@code LevelOperations.addExp}. The view exposes the original amount, the resolved multiplier,
     * the multiplied amount, the configured daily limit, the amount already gained today and the
     * resulting applicable amount. This call records nothing, so it does not consume quota and
     * repeated calls are side-effect free. Because it is only a preview, a later {@code addExp} may
     * still apply less, for instance when concurrent gains consume the remaining quota first or a
     * listener lowers the amount.
     *
     * <p><strong>Thread:</strong> any thread.
     *
     * @param uuid   the player whose daily quota is consulted; {@code null} yields
     *               {@code INVALID_INPUT}
     * @param typeId the level type to evaluate; must be non-blank and loaded
     * @param amount raw experience to evaluate; must be finite and strictly positive, otherwise
     *               {@code INVALID_INPUT} with {@code level.amount_invalid} is returned
     * @param reason optional normalized reason used to select a reason-specific multiplier; may be
     *               {@code null} or blank
     * @return the computed adjustment, or a classified failure. Unlike the progress queries this does
     *         not require cached player data, so a quota-exhausted preview succeeds with an
     *         applicable amount of {@code 0} rather than failing
     */
    @NotNull
    EmakiResult<LevelExpAdjustmentView> previewAdjustment(@Nullable UUID uuid,
            @Nullable String typeId,
            double amount,
            @Nullable String reason);
}
