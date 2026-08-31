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
 *
 * <p>Since 1.0.3 accessories live on independent pages: each page stores its own items under the
 * same slot instance ids, and only the enabled page grants attributes, skills, and set pieces.
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
     * {@return every configured accessory page id, in page order; never {@code null}}
     *
     * @since 1.0.3
     */
    @NotNull
    List<String> pageIds();

    /**
     * Returns the accessory page whose contents currently grant effects for the player.
     *
     * <p>Only one page is enabled at a time. When the player lacks the enabled page's permission
     * the page grants nothing and this returns an empty string, while the stored items stay
     * untouched so the player can retrieve them.
     *
     * @param playerId the player id
     * @return the enabled page id, or an empty string when unknown or currently not usable
     * @since 1.0.3
     */
    @NotNull
    String enabledPage(@Nullable UUID playerId);

    /**
     * Returns what the player currently has equipped on the <em>enabled</em> page.
     *
     * <p>Since 1.0.3 accessories are stored per page and only the enabled page grants effects, so
     * this reports that page alone. Use {@link #equippedOnPage(UUID, String)} to inspect any other
     * page. Returns an empty map when no page is enabled or the enabled page is not usable.
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
     * Returns what the player currently has stored on one specific accessory page.
     *
     * <p>Pages other than the enabled one keep their items but grant no effects.
     *
     * @param playerId the player id
     * @param pageId   the accessory page id; normalized before lookup
     * @return slot instance id to occupant, or an empty map when the page holds nothing
     * @since 1.0.3
     */
    @NotNull
    Map<String, EquippedAccessoryView> equippedOnPage(@Nullable UUID playerId, @Nullable String pageId);

    /**
     * Returns how many pieces of one accessory set the player currently has equipped.
     *
     * <p>Orphaned slots never contribute, and only the enabled page is counted. Accessory sets only
     * count items inside accessory slots and are deliberately independent from EmakiItem equipment
     * sets.
     *
     * @param playerId the player id
     * @param setId    the accessory set id
     * @return the equipped piece count, or {@code 0} when unknown
     */
    int equippedSetPieces(@Nullable UUID playerId, @Nullable String setId);
}
