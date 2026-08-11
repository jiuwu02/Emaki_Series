package emaki.jiuwu.craft.level.api;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;

/**
 * Synchronous state-changing level operations.
 *
 * <p>Reached through {@code EmakiLevelApi.operations()}.
 *
 * <p><strong>Thread:</strong> every method here is owner-thread only. The target player must be
 * online and the calling thread must own that player, otherwise the call fails with
 * {@code TARGET_OFFLINE} or {@code WRONG_THREAD} before any state changes. These methods never
 * reschedule themselves onto the owner thread, so a wrong-thread call is a rejected call, not a
 * deferred one.
 *
 * <h2>Shared failure contract</h2>
 * All methods return a non-null {@code EmakiResult}. Recurring classifications are:
 * <ul>
 * <li>{@code UNAVAILABLE} when no EmakiLevel runtime bridge is installed.</li>
 * <li>{@code INVALID_INPUT} for a {@code null} uuid, a blank {@code typeId}, or an out-of-contract
 * numeric argument. Reason keys {@code level.player_uuid_required}, {@code level.type_id_required}
 * and {@code level.amount_invalid} are used before any runtime work starts.</li>
 * <li>{@code TARGET_OFFLINE} when the uuid resolves to no online player.</li>
 * <li>{@code WRONG_THREAD} when the caller does not own the resolved player.</li>
 * <li>{@code NOT_FOUND} when the level type is unknown or the player's cached level data is not
 * loaded.</li>
 * <li>{@code REJECTED} for business refusals such as a disabled type, a reached daily cap, an
 * unmet level requirement, or insufficient upgrade cost.</li>
 * <li>{@code CANCELLED} when a listener cancelled the corresponding Bukkit event.</li>
 * <li>{@code INTERNAL_ERROR} when the runtime throws, or when a mutation produces no result.</li>
 * </ul>
 * Branch on {@code FailureKind} only; {@code reasonKey} is for logging and diagnostics and must not
 * be treated as an exhaustive enumeration.
 *
 * <p>Level and experience writes only touch the level type named by {@code typeId}; they never fan
 * out to other types.
 */
@ApiStatus.NonExtendable
public interface LevelOperations {

    /**
     * Grants experience through EmakiLevel's full gain pipeline: multiplier resolution, daily-quota
     * capping, {@code PlayerExpGainEvent}, persistence, PDC sync and optional auto level-up.
     *
     * <p>Equivalent to {@link #addExp(UUID, String, double, String, boolean)} with
     * {@code silent = false}.
     *
     * <p>The amount actually applied may be smaller than {@code amount}: configured multipliers and
     * the remaining daily quota are applied first, and a listener may lower it further. Read the
     * applied value from the returned {@code LevelOperationResult} instead of assuming
     * {@code amount} was credited. Use {@code LevelCatalog.previewAdjustment} to inspect the
     * adjustment without recording a gain.
     *
     * <p><strong>Thread:</strong> the target player's owner thread.
     *
     * @param uuid   the target player; must be non-{@code null} and currently online
     * @param typeId the level type to credit; must be non-blank and loaded
     * @param amount raw experience before multipliers and quota; must be finite and strictly
     *               positive, otherwise {@code INVALID_INPUT} is returned
     * @param reason optional normalized gain reason used for multiplier lookup, action placeholders
     *               and diagnostics; {@code null} or blank simply selects no reason-specific
     *               multiplier
     * @return the recorded operation, or a classified failure; {@code REJECTED} covers a disabled
     *         type and an exhausted daily cap, and {@code CANCELLED} covers a listener veto
     */
    @NotNull
    EmakiResult<LevelOperationResult> addExp(@Nullable UUID uuid,
            @Nullable String typeId,
            double amount,
            @Nullable String reason);

    /**
     * Grants experience with explicit control over player-facing feedback.
     *
     * <p>Same pipeline and same adjustment semantics as
     * {@link #addExp(UUID, String, double, String)}. {@code silent} is forwarded to the configured
     * action lines for the gain, level-up success and level-up failure phases, so it suppresses
     * player-facing feedback produced by those actions. It does not suppress Bukkit events,
     * persistence, PDC sync or attribute refresh.
     *
     * <p><strong>Thread:</strong> the target player's owner thread.
     *
     * @param uuid   the target player; must be non-{@code null} and currently online
     * @param typeId the level type to credit; must be non-blank and loaded
     * @param amount raw experience before multipliers and quota; must be finite and strictly
     *               positive, otherwise {@code INVALID_INPUT} is returned
     * @param reason optional normalized gain reason used for multiplier lookup, action placeholders
     *               and diagnostics; may be {@code null} or blank
     * @param silent whether player-facing action feedback for this gain is suppressed
     * @return the recorded operation, or a classified failure as described on
     *         {@link #addExp(UUID, String, double, String)}
     */
    @NotNull
    EmakiResult<LevelOperationResult> addExp(@Nullable UUID uuid,
            @Nullable String typeId,
            double amount,
            @Nullable String reason,
            boolean silent);

    /**
     * Subtracts experience from the current level's progress bar.
     *
     * <p>This is a plain corrective write, not the inverse of {@link #addExp}: multipliers, the
     * daily quota, {@code PlayerExpGainEvent} and auto level-up are not involved, and lifetime
     * total experience is left unchanged. The stored value is floored at {@code 0} rather than
     * borrowing from the level below, so the player never drops a level through this call and no
     * level-change event is fired.
     *
     * <p><strong>Thread:</strong> the target player's owner thread.
     *
     * @param uuid   the target player; must be non-{@code null} and currently online
     * @param typeId the level type to debit; must be non-blank and loaded
     * @param amount experience to subtract; must be finite and strictly positive, otherwise
     *               {@code INVALID_INPUT} is returned. Values larger than the current progress
     *               clamp to {@code 0} and still report success
     * @param reason optional reason recorded for diagnostics and action placeholders; may be
     *               {@code null} or blank
     * @return the applied operation, or a classified failure
     */
    @NotNull
    EmakiResult<LevelOperationResult> removeExp(@Nullable UUID uuid,
            @Nullable String typeId,
            double amount,
            @Nullable String reason);

    /**
     * Overwrites the current level's experience progress with an absolute value.
     *
     * <p>Administrative write: the level itself is not recalculated, no auto level-up runs, lifetime
     * total experience is untouched, and no level-change event is fired. Setting a value above the
     * next level's requirement leaves the player eligible for a later {@link #levelUp} rather than
     * upgrading immediately.
     *
     * <p><strong>Thread:</strong> the target player's owner thread.
     *
     * @param uuid   the target player; must be non-{@code null} and currently online
     * @param typeId the level type to write; must be non-blank and loaded
     * @param amount absolute experience value; must be finite and {@code >= 0}. Unlike the other
     *               experience methods {@code 0} is accepted; negative or non-finite values return
     *               {@code INVALID_INPUT}
     * @param reason optional reason recorded for diagnostics and action placeholders; may be
     *               {@code null} or blank
     * @return the applied operation, or a classified failure
     */
    @NotNull
    EmakiResult<LevelOperationResult> setExp(@Nullable UUID uuid,
            @Nullable String typeId,
            double amount,
            @Nullable String reason);

    /**
     * Raises the level directly, bypassing experience requirements and upgrade costs.
     *
     * <p>This is not a batched {@link #levelUp}: no experience requirement is checked, no upgrade
     * cost is charged, no level-up rewards or success actions run, and neither
     * {@code PlayerPreLevelUpEvent} nor {@code PlayerLevelUpEvent} is fired. The resulting level is
     * clamped into the type's configured start/max range, current progress is reset to {@code 0},
     * attributes are refreshed, and {@code PlayerLevelChangeEvent} is fired only when the stored
     * level actually changed. A player already at max level therefore reports success with an
     * unchanged level.
     *
     * <p><strong>Thread:</strong> the target player's owner thread.
     *
     * @param uuid   the target player; must be non-{@code null} and currently online
     * @param typeId the level type to raise; must be non-blank and loaded
     * @param amount levels to add; must be strictly positive, otherwise {@code INVALID_INPUT} is
     *               returned. Overshoot is clamped to the configured max level
     * @param reason optional reason recorded for diagnostics; may be {@code null} or blank
     * @return the applied operation carrying the old and new level, or a classified failure
     */
    @NotNull
    EmakiResult<LevelOperationResult> addLevel(@Nullable UUID uuid,
            @Nullable String typeId,
            int amount,
            @Nullable String reason);

    /**
     * Lowers the level directly.
     *
     * <p>Mirror of {@link #addLevel}: the result is clamped into the type's start/max range, current
     * progress is reset to {@code 0}, lifetime total experience is left untouched, attributes are
     * refreshed, and {@code PlayerLevelChangeEvent} is fired only when the stored level actually
     * changed. No cost is refunded and no reward is revoked.
     *
     * <p><strong>Thread:</strong> the target player's owner thread.
     *
     * @param uuid   the target player; must be non-{@code null} and currently online
     * @param typeId the level type to lower; must be non-blank and loaded
     * @param amount levels to subtract; must be strictly positive, otherwise {@code INVALID_INPUT}
     *               is returned. Undershoot is clamped to the configured start level
     * @param reason optional reason recorded for diagnostics; may be {@code null} or blank
     * @return the applied operation carrying the old and new level, or a classified failure
     */
    @NotNull
    EmakiResult<LevelOperationResult> removeLevel(@Nullable UUID uuid,
            @Nullable String typeId,
            int amount,
            @Nullable String reason);

    /**
     * Overwrites the level with an absolute value.
     *
     * <p>Administrative write with the same restrictions as {@link #addLevel}: no requirement check,
     * no cost, no rewards, no level-up events. Progress is reset to {@code 0}, lifetime total
     * experience is preserved, attributes are refreshed, and {@code PlayerLevelChangeEvent} is fired
     * only when the stored level actually changed.
     *
     * <p><strong>Thread:</strong> the target player's owner thread.
     *
     * @param uuid   the target player; must be non-{@code null} and currently online
     * @param typeId the level type to write; must be non-blank and loaded
     * @param level  target level; must be {@code >= 0} to pass input validation, and is then clamped
     *               into the type's configured start/max range. Negative values return
     *               {@code INVALID_INPUT}, so a value below the start level is clamped rather than
     *               rejected
     * @param reason optional reason recorded for diagnostics; may be {@code null} or blank
     * @return the applied operation carrying the old and new level, or a classified failure
     */
    @NotNull
    EmakiResult<LevelOperationResult> setLevel(@Nullable UUID uuid,
            @Nullable String typeId,
            int level,
            @Nullable String reason);

    /**
     * Attempts one regular level-up through the full upgrade pipeline.
     *
     * <p>Advances at most a single level. The runtime checks that upgrading is enabled for the type,
     * that manual upgrading is permitted, that the player is below max level, that the next level has
     * a valid requirement, and that the stored progress covers it. It then fires the cancellable
     * {@link emaki.jiuwu.craft.level.api.event.PlayerPreLevelUpEvent}, charges the configured upgrade
     * cost, deducts the required experience, applies the level, grants rewards, runs success actions,
     * and fires {@code PlayerLevelUpEvent} plus {@code PlayerMaxLevelReachedEvent} when the max level
     * is reached by this step.
     *
     * <p>Failures are classified rather than thrown: a disabled upgrade path, a disabled manual
     * upgrade, an already-max level, an invalid requirement, insufficient experience and an unpaid
     * cost all surface as {@code REJECTED} with distinct reason keys such as
     * {@code level.upgrade_disabled}, {@code level.max_level}, {@code level.not_enough_exp} and
     * {@code level.not_enough_money}. A cancelling listener yields {@code CANCELLED}, and a failed
     * cost compensation yields {@code INTERNAL_ERROR}.
     *
     * <p><strong>Thread:</strong> the target player's owner thread.
     *
     * @param uuid   the target player; must be non-{@code null} and currently online
     * @param typeId the level type to upgrade; must be non-blank and loaded
     * @param cause  attribution carried into the level-up events and action placeholders;
     *               {@code null} is treated as {@link LevelUpCause#API}
     * @return the committed upgrade carrying the old and new level, or a classified failure
     */
    @NotNull
    EmakiResult<LevelOperationResult> levelUp(@Nullable UUID uuid,
            @Nullable String typeId,
            @Nullable LevelUpCause cause);

    /**
     * Clears one level type back to its configured starting state.
     *
     * <p>Destructive and not reversible through this API: the level returns to the type's start
     * level, and both current progress and lifetime total experience are set to {@code 0}. Only the
     * named type is cleared. Attributes are refreshed and {@code PlayerLevelChangeEvent} is fired
     * only when the stored level actually changed. Spent upgrade costs are not refunded and granted
     * rewards are not revoked.
     *
     * <p><strong>Thread:</strong> the target player's owner thread.
     *
     * @param uuid   the target player; must be non-{@code null} and currently online
     * @param typeId the level type to clear; must be non-blank and loaded
     * @return the applied operation, or a classified failure
     */
    @NotNull
    EmakiResult<LevelOperationResult> reset(@Nullable UUID uuid, @Nullable String typeId);

    /**
     * Rewrites EmakiLevel's persistent-data-container mirror for every loaded level type and
     * refreshes the player's level-derived attributes.
     *
     * <p>Use this after an external write bypassed the API, or when a consumer reads level values
     * straight from the PDC. It does not load or persist player data, and it does not change level or
     * experience values. Types with PDC mirroring disabled are skipped silently.
     *
     * <p><strong>Thread:</strong> the player's owner thread.
     *
     * @param player the player to re-sync; must be non-{@code null} and currently online
     * @return success, or a failure describing why nothing was written; {@code NOT_FOUND} with
     *         {@code level.player_data_not_found} means the player's data is not currently cached
     */
    @NotNull
    EmakiResult<Unit> syncPlayer(@Nullable Player player);

    /**
     * Opens the level GUI for one level type.
     *
     * <p><strong>Thread:</strong> the player's owner thread.
     *
     * @param player the player to show the GUI to; must be non-{@code null} and currently online
     * @param typeId the level type to display; must be non-blank and loaded
     * @return success, or a failure describing why the GUI did not open; {@code NOT_FOUND} for an
     *         unknown type, and {@code REJECTED} when the GUI is disabled by configuration
     *         ({@code level.gui_disabled}) or the runtime declined to open it
     */
    @NotNull
    EmakiResult<Unit> openGui(@Nullable Player player, @Nullable String typeId);

    /**
     * Opens the leaderboard GUI for one level type.
     *
     * <p>Renders the same cached leaderboard snapshot exposed by {@code LevelCatalog.top}, so it may
     * lag slightly behind the very latest writes.
     *
     * <p><strong>Thread:</strong> the player's owner thread.
     *
     * @param player the player to show the GUI to; must be non-{@code null} and currently online
     * @param typeId the level type whose ranking is displayed; must be non-blank and loaded
     * @return success, or a failure describing why the GUI did not open; classified as for
     *         {@link #openGui}, with reason key {@code level.top_gui_open_failed} when the runtime
     *         declined to open it
     */
    @NotNull
    EmakiResult<Unit> openTopGui(@Nullable Player player, @Nullable String typeId);
}
