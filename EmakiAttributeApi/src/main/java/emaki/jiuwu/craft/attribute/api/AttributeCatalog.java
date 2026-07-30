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

/** Read-only Attribute definitions, resource state and snapshot queries. */
@ApiStatus.NonExtendable
public interface AttributeCatalog {

    /**
     * Reads one resolved player attribute.
     *
     * <p><strong>Thread:</strong> the player's owner thread.
     */
    @NotNull
    EmakiResult<Double> attributeValue(@Nullable Player player, @Nullable String attributeId);

    /**
     * Reads one resource's current value.
     *
     * <p><strong>Thread:</strong> the player's owner thread.
     */
    @NotNull
    EmakiResult<Double> resourceCurrent(@Nullable Player player, @Nullable String resourceId);

    /**
     * Reads one resource's current maximum.
     *
     * <p><strong>Thread:</strong> the player's owner thread.
     */
    @NotNull
    EmakiResult<Double> resourceMax(@Nullable Player player, @Nullable String resourceId);

    /**
     * Collects all Lore and PDC contributions from an item.
     *
     * <p><strong>Thread:</strong> the owner thread of whatever holds the item.
     */
    @NotNull
    EmakiResult<AttributeSnapshot> itemSnapshot(@Nullable ItemStack itemStack);

    /**
     * Collects the current combat snapshot for an entity.
     *
     * <p><strong>Thread:</strong> the entity's owner thread.
     */
    @NotNull
    EmakiResult<AttributeSnapshot> combatSnapshot(@Nullable LivingEntity entity);

    /** {@return immutable resource definitions keyed by normalized id} */
    @NotNull
    Map<String, ResourceDefinitionView> resources();

    /** {@return immutable normalized attribute ids} */
    @NotNull
    Set<String> attributeIds();

    /** {@return immutable normalized damage type ids} */
    @NotNull
    Set<String> damageTypeIds();

    /**
     * Returns whether every registered item gate accepts a contribution.
     *
     * <p>This deliberately degrades to {@code true} when the API is unavailable or the item is empty,
     * so consulting the optional integration never disables an otherwise working item.
     */
    boolean isItemContributionActive(@Nullable Player player,
            @Nullable ItemStack itemStack,
            @Nullable String slotName);
}
