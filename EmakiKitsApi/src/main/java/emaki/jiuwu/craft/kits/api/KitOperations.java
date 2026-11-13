package emaki.jiuwu.craft.kits.api;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.kits.api.model.KitClaimView;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * State-changing kit operations.
 *
 * <p>Reached through {@code EmakiKitsApi.operations()}. Every method runs through the same
 * idempotency, permission, eligibility, audit and rollback boundary as the plugin's own commands:
 * a repeated call in the same cycle is rejected rather than granting rewards twice.
 *
 * <p><strong>Thread:</strong> all methods that take a {@link Player} must be called on that
 * player's owner thread. On Paper this is the main thread; on Folia it is the entity owner thread.
 * A call from the wrong thread is reported as
 * {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind#WRONG_THREAD}. Operations are
 * synchronous: reward dispatch, counter updates and the persistence journal all complete before
 * the result is returned, though the on-disk flush may be deferred to the configured autosave.
 *
 * <p>Failures are classified by {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind}, not
 * by {@code reasonKey}; the reason key is a stable diagnostic identifier and may grow over time.
 */
@ApiStatus.NonExtendable
public interface KitOperations {

    /**
     * Claims a kit for a player without explicit option selections.
     *
     * <p>Valid for join, cyclic, generic, invite and choice kits; CDK kits must use
     * {@link #redeem(Player, String, String)}. Choice kits called through this overload use an
     * empty selection set and are rejected when {@code min_select > 0}.
     *
     * @param player the online claiming player
     * @param kitId  the kit id
     * @return the committed outcome, or a classified failure
     */
    @NotNull
    EmakiResult<KitClaimView> claim(@Nullable Player player, @Nullable String kitId);

    /**
     * Claims a kit with explicit option selections (used by self-choice kits).
     *
     * <p>Selections are validated against the configured option ids, per-option permissions,
     * duplicate entries and the {@code min_select}/{@code max_select} bounds. The selection set
     * and the claim record are written atomically with the reward commit.
     *
     * @param player     the online claiming player
     * @param kitId      the kit id
     * @param selections option ids to claim, in caller order
     * @return the committed outcome, or a classified failure
     */
    @NotNull
    EmakiResult<KitClaimView> claim(@Nullable Player player, @Nullable String kitId,
                                    @Nullable List<String> selections);

    /**
     * Purchases one claim of a kit.
     *
     * <p>Debit and purchase-count increment share one transaction: a failed debit never increases
     * the purchase count. When the configured economy bridge is unavailable the call fails with
     * {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind#REJECTED} rather than granting a
     * free claim.
     *
     * @param player the online buying player
     * @param kitId  the kit id
     * @return a void-like success, or a classified failure
     */
    @NotNull
    EmakiResult<Unit> buy(@Nullable Player player, @Nullable String kitId);

    /**
     * Redeems a CDK code for a player.
     *
     * <p>Global-once codes use a single global idempotency key and per-player-once codes use a
     * per-player key; the two modes are never conflated. Concurrent redemptions of the same key
     * yield exactly one success.
     *
     * @param player the online redeeming player
     * @param kitId  the CDK kit id
     * @param code   the redemption code
     * @return the committed outcome, or a classified failure
     */
    @NotNull
    EmakiResult<KitClaimView> redeem(@Nullable Player player, @Nullable String kitId,
                                     @Nullable String code);

    /**
     * Grants one available claim of a kit to a player (administrative operation).
     *
     * <p>This increases the purchase/claim counter without debiting any economy balance and is
     * intended for compensation and testing. It does not bypass idempotency on a later claim.
     *
     * @param player the online target player
     * @param kitId  the kit id
     * @return a void-like success, or a classified failure
     */
    @NotNull
    EmakiResult<Unit> grantPurchase(@Nullable Player player, @Nullable String kitId);
}
