package emaki.jiuwu.craft.corelib.api.integration;

import java.util.Map;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Legacy CoreLib mirror of EmakiAttribute's gameplay bridge.
 *
 * @deprecated Superseded by {@code emaki.jiuwu.craft.attribute.api.EmakiAttributeApi}
 *             in EmakiAttributeApi, which is the canonical contract. The only
 *             remaining implementation is EmakiAttribute's compatibility adapter,
 *             which merely delegates to that facade. Retained for one synchronized
 *             release window and removed afterwards.
 */
@Deprecated(forRemoval = true)
public interface EmakiAttributeBridge {

    boolean available();

    double readResourceCurrent(Player player, String resourceId);

    double readResourceMax(Player player, String resourceId);

    boolean consumeResource(Player player, String resourceId, double amount);

    double readAttributeValue(Player player, String attributeId);

    default void scheduleEquipmentSync(Player player) {
    }

    default boolean applyDamage(LivingEntity attacker, LivingEntity target, String damageTypeId, double baseDamage, Map<String, Object> context) {
        return false;
    }
}
