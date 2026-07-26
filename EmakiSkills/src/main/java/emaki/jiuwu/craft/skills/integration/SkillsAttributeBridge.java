package emaki.jiuwu.craft.skills.integration;

import java.util.Map;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * EmakiAttribute-neutral view of the attribute capabilities EmakiSkills needs.
 *
 * <p>No signature here references an EmakiAttributeApi type, so EmakiSkills
 * loads and runs with EmakiAttribute absent. The implementation that does
 * reference those types lives in
 * {@code emaki.jiuwu.craft.skills.integration.attribute} and is only
 * class-loaded once EmakiAttribute is enabled.
 */
public interface SkillsAttributeBridge {

    /** Bridge used when EmakiAttribute is not installed; every call degrades. */
    SkillsAttributeBridge UNAVAILABLE = new SkillsAttributeBridge() {
    };

    /** {@return whether EmakiAttribute is available} */
    default boolean available() {
        return false;
    }

    /**
     * Reads a player's current value for a resource.
     *
     * @param player the owning player
     * @param resourceId the resource id
     * @return the current value, or {@code -1} when unavailable
     */
    default double readResourceCurrent(Player player, String resourceId) {
        return -1D;
    }

    /**
     * Reads a player's current maximum for a resource.
     *
     * @param player the owning player
     * @param resourceId the resource id
     * @return the current maximum, or {@code -1} when unavailable
     */
    default double readResourceMax(Player player, String resourceId) {
        return -1D;
    }

    /**
     * Consumes a resource amount, firing EmakiAttribute's consume event and
     * honouring cancellation and a modified amount.
     *
     * @param player the owning player
     * @param resourceId the resource id
     * @param amount the amount to consume
     * @return {@code true} when the resource was consumed
     */
    default boolean consumeResource(Player player, String resourceId, double amount) {
        return false;
    }

    /**
     * Reads a player's resolved value for an attribute.
     *
     * @param player the owning player
     * @param attributeId the attribute id
     * @return the resolved value, or {@code 0} when unavailable
     */
    default double readAttributeValue(Player player, String attributeId) {
        return 0D;
    }

    /**
     * Applies EmakiAttribute damage resolution to a target.
     *
     * @param attacker the attacking entity
     * @param target the damaged entity
     * @param damageTypeId the damage type id
     * @param baseDamage the base damage
     * @param context additional damage context variables
     * @return {@code true} when damage was applied
     */
    default boolean applyDamage(LivingEntity attacker,
            LivingEntity target,
            String damageTypeId,
            double baseDamage,
            Map<String, Object> context) {
        return false;
    }
}
