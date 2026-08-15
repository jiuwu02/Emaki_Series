package emaki.jiuwu.craft.skills.integration;

import java.util.Map;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface SkillsAttributeBridge {

    SkillsAttributeBridge UNAVAILABLE = new SkillsAttributeBridge() {
    };

    default boolean available() {
        return false;
    }

    default double readResourceCurrent(Player player, String resourceId) {
        return -1D;
    }

    default double readResourceMax(Player player, String resourceId) {
        return -1D;
    }

    default boolean consumeResource(Player player, String resourceId, double amount) {
        return false;
    }

    default double readAttributeValue(Player player, String attributeId) {
        return 0D;
    }

    default boolean applyDamage(LivingEntity attacker,
            LivingEntity target,
            String damageTypeId,
            double baseDamage,
            Map<String, Object> context) {
        return false;
    }

    default boolean isItemContributionActive(Player player, ItemStack itemStack, String slotName) {
        return true;
    }
}
