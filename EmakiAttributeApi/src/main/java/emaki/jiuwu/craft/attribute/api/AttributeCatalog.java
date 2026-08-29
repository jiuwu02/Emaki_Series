package emaki.jiuwu.craft.attribute.api;

import java.util.Map;
import java.util.Set;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.attribute.api.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.api.model.ResourceDefinitionView;
import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;

/**
 * Read-only Attribute definitions, resource state and snapshot queries.
 *
 * <p>Result-bearing methods use the shared {@link EmakiResult} failure contract. Collection queries return
 * immutable empty values while the runtime is unavailable.
 *
 * <p><strong>Thread:</strong> live-entity queries must run on that entity's owner thread. Definition queries
 * use immutable snapshots and may run from any thread.
 *
 * <p>Attribute, resource and damage-type ids are normalized before matching (trimmed, lower-cased with
 * {@code Locale.ROOT}, spaces folded to {@code _}).
 */
@ApiStatus.NonExtendable
public interface AttributeCatalog {

    /**
     * Reads one fully resolved attribute value for a player, combining every active contribution source.
     *
     * <p><strong>Thread:</strong> the player's owner thread.
     *
     * @param player      the player to read
     * @param attributeId the attribute to resolve
     * @return the resolved value; a registered attribute absent from the player's snapshot is reported as a
     *         miss through the shared result contract
     */
    @NotNull
    EmakiResult<Double> attributeValue(@Nullable Player player, @Nullable String attributeId);

    /**
     * Reads one resource's current value.
     *
     * <p><strong>Thread:</strong> the player's owner thread.
     *
     * @param player     the player to read
     * @param resourceId the resource to read
     * @return the current value; missing player state is reported as a result miss rather than synthesized
     */
    @NotNull
    EmakiResult<Double> resourceCurrent(@Nullable Player player, @Nullable String resourceId);

    /**
     * Reads one resource's current maximum, which is the live derived maximum rather than the configured
     * default.
     *
     * <p><strong>Thread:</strong> the player's owner thread.
     *
     * @param player     the player to read
     * @param resourceId the resource to read
     * @return the current maximum; missing player state is reported as a result miss rather than synthesized
     */
    @NotNull
    EmakiResult<Double> resourceMax(@Nullable Player player, @Nullable String resourceId);

    /**
     * Collects every Lore and PDC attribute contribution carried by one item, without consulting item gates
     * or any player context.
     *
     * <p><strong>Thread:</strong> the owner thread of whatever holds the item.
     *
     * @param itemStack the stack to inspect
     * @return the item's contribution snapshot; a successful empty snapshot means the item carries no
     *         attributes
     */
    @NotNull
    EmakiResult<AttributeSnapshot> itemSnapshot(@Nullable ItemStack itemStack);

    /**
     * Collects the combat snapshot EmakiAttribute would use for one entity right now, merging equipment,
     * providers and the entity's own profile.
     *
     * <p><strong>Thread:</strong> the entity's owner thread.
     *
     * @param entity the entity to inspect
     * @return the entity's combat snapshot through the shared result contract
     */
    @NotNull
    EmakiResult<AttributeSnapshot> combatSnapshot(@Nullable LivingEntity entity);

    /**
     * {@return immutable resource definitions keyed by normalized id; empty while EmakiAttribute is
     * unavailable or no resources are configured, never {@code null}}
     */
    @NotNull
    Map<String, ResourceDefinitionView> resources();

    /**
     * {@return immutable normalized attribute ids; empty while EmakiAttribute is unavailable or the registry
     * has not loaded, never {@code null}}
     */
    @NotNull
    Set<String> attributeIds();

    /**
     * {@return immutable normalized damage type ids; empty while EmakiAttribute is unavailable or the
     * registry has not loaded, never {@code null}}
     */
    @NotNull
    Set<String> damageTypeIds();

    /**
     * Returns whether every registered item gate accepts a contribution.
     *
     * <p>This deliberately degrades to {@code true} when the API is unavailable or the item is empty,
     * so consulting the optional integration never disables an otherwise working item. A gate that throws is
     * likewise treated as accepting, so one broken third-party gate cannot strip an item's attributes.
     *
     * <p>Because the answer collapses "accepted" and "nothing could be checked" into the same {@code true},
     * do not use this method to test whether EmakiAttribute is installed; use
     * {@code EmakiAttributeApi.status()} for that.
     *
     * <p><strong>Thread:</strong> gates receive the player and item as given, so call this on the owner
     * thread of the player whose equipment is being evaluated.
     *
     * @param player    the player the item is being evaluated for; gates may accept {@code null} for
     *                  contexts without a player
     * @param itemStack the item whose contribution is being tested; {@code null} or air short-circuits to
     *                  {@code true}
     * @param slotName  the equipment slot name being evaluated, or {@code null} when no slot applies
     * @return {@code true} when no gate rejects the contribution, including the degraded cases above;
     *         {@code false} only when a gate actively rejected it
     */
    boolean isItemContributionActive(@Nullable Player player,
            @Nullable ItemStack itemStack,
            @Nullable String slotName);
}
