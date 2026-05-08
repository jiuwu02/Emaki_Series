package emaki.jiuwu.craft.corelib.api.integration;

import java.util.Map;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Stable bridge contract exposed by EmakiAttribute for optional integrations.
 * <p>
 * This interface intentionally stays narrow so soft-dependent plugins can
 * interact with EA resources and attribute snapshots without depending on
 * EmakiAttribute internal model classes.
 */
public interface EmakiAttributeBridge {

    boolean available();

    double readResourceCurrent(Player player, String resourceId);

    double readResourceMax(Player player, String resourceId);

    boolean consumeResource(Player player, String resourceId, double amount);

    double readAttributeValue(Player player, String attributeId);

    /**
     * Apply attribute-based damage through the full damage calculation pipeline.
     *
     * @param attacker      the attacking entity (may be null for environmental damage)
     * @param target        the target entity receiving damage
     * @param damageTypeId  the damage type identifier (uses default if blank)
     * @param baseDamage    the base damage amount before calculation
     * @param context       additional context variables for the damage formula
     * @return true if damage was successfully applied
     */
    default boolean applyDamage(LivingEntity attacker, LivingEntity target, String damageTypeId, double baseDamage, Map<String, Object> context) {
        return false;
    }
}
