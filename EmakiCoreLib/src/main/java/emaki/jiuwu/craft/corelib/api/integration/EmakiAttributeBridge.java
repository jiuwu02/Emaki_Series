package emaki.jiuwu.craft.corelib.api.integration;

import java.util.Map;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public interface EmakiAttributeBridge {

    boolean available();

    double readResourceCurrent(Player player, String resourceId);

    double readResourceMax(Player player, String resourceId);

    boolean consumeResource(Player player, String resourceId, double amount);

    double readAttributeValue(Player player, String attributeId);

    default boolean applyDamage(LivingEntity attacker, LivingEntity target, String damageTypeId, double baseDamage, Map<String, Object> context) {
        return false;
    }
}
