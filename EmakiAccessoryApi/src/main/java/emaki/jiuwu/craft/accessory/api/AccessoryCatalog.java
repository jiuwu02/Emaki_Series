package emaki.jiuwu.craft.accessory.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.accessory.api.model.AccessoryPartView;
import emaki.jiuwu.craft.accessory.api.model.EquippedAccessoryView;

/**
 * Read-only queries over accessory parts, slot instances, and what a player currently has equipped.
 *
 * <p>Part and slot queries are configuration facts and may be called from any thread. Player queries
 * read the in-memory session cache and must be called on that player's owner thread; they return an
 * empty result rather than blocking when the player's data has not finished loading.
 */
@ApiStatus.NonExtendable
public interface AccessoryCatalog {

    /** {@return every configured part in declaration order; never {@code null}} */
    @NotNull
    List<AccessoryPartView> parts();

    /**
     * Looks up one configured part.
     *
     * @param partId the part id; normalized before lookup
     * @return the part, or empty when no such part is configured
     */
    @NotNull
    Optional<AccessoryPartView> part(@Nullable String partId);

    /**
     * {@return every configured slot instance id, in part declaration order then index order; never
     * {@code null}}
     */
    @NotNull
    List<String> slotInstanceIds();

    /**
     * Returns what the player currently has in their accessory slots.
     *
     * <p>Includes orphaned slots so callers can see items pending retrieval; use
     * {@link EquippedAccessoryView#orphaned()} to skip them.
     *
     * @param playerId the player id
     * @return slot instance id to occupant, or an empty map when the player has no loaded session
     */
    @NotNull
    Map<String, EquippedAccessoryView> equipped(@Nullable UUID playerId);

    /**
     * Returns how many pieces of one accessory set the player currently has equipped.
     *
     * <p>Orphaned slots never contribute. Accessory sets only count items inside accessory slots and
     * are deliberately independent from EmakiItem equipment sets.
     *
     * @param playerId the player id
     * @param setId    the accessory set id
     * @return the equipped piece count, or {@code 0} when unknown
     */
    int equippedSetPieces(@Nullable UUID playerId, @Nullable String setId);
}
