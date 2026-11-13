package emaki.jiuwu.craft.kits.api.event;

import emaki.jiuwu.craft.kits.api.model.KitClaimView;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import java.util.UUID;

/**
 * Fired synchronously after EmakiKits commits a successful claim, purchase or CDK redemption.
 *
 * <p>The event is informational and non-cancellable: cancelling it does not roll back rewards,
 * idempotency keys, counters or audit rows, all of which are written before the event is fired.
 * It is fired on the thread that handled the operation, which on Folia is the player's entity owner
 * thread. Listeners that schedule further work must respect that ownership.
 *
 * <p>The event carries only immutable value types; the {@link Player} reference is live and must
 * not be retained past the dispatch.
 */
public final class KitClaimedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final KitClaimView claim;
    private final String action;

    /**
     * Creates a claimed event.
     *
     * @param player the online player that received the kit
     * @param claim  immutable outcome snapshot
     * @param action stable action id ({@code claim}, {@code purchase} or {@code cdk_redeem})
     */
    public KitClaimedEvent(@NotNull Player player, @NotNull KitClaimView claim, @NotNull String action) {
        this.player = player;
        this.claim = claim;
        this.action = action;
    }

    /** {@return the online player that received the kit} */
    public @NotNull Player getPlayer() {
        return player;
    }

    /** {@return the unique id of the receiving player} */
    public @NotNull UUID getPlayerId() {
        return player.getUniqueId();
    }

    /** {@return immutable outcome snapshot of the committed operation} */
    public @NotNull KitClaimView getClaim() {
        return claim;
    }

    /** {@return stable action id that produced the claim} */
    public @NotNull String getAction() {
        return action;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    /** {@return the handler list used by Bukkit to register listeners} */
    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
