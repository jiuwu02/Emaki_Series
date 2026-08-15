package emaki.jiuwu.craft.skills.integration.attribute;

import java.util.Map;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.attribute.api.EmakiAttributeApi;
import emaki.jiuwu.craft.skills.integration.SkillsAttributeBridge;

public final class EmakiAttributeSkillsBridge implements SkillsAttributeBridge {

    public static SkillsAttributeBridge create() {
        return new EmakiAttributeSkillsBridge();
    }

    private EmakiAttributeSkillsBridge() {
    }

    @Override
    public boolean available() {
        return EmakiAttributeApi.status().usable();
    }

    @Override
    public double readResourceCurrent(Player player, String resourceId) {
        return EmakiAttributeApi.catalog().resourceCurrent(player, resourceId).orElse(-1D);
    }

    @Override
    public double readResourceMax(Player player, String resourceId) {
        return EmakiAttributeApi.catalog().resourceMax(player, resourceId).orElse(-1D);
    }

    @Override
    public boolean consumeResource(Player player, String resourceId, double amount) {
        return EmakiAttributeApi.operations().consumeResource(player, resourceId, amount).isSuccess();
    }

    @Override
    public double readAttributeValue(Player player, String attributeId) {
        return EmakiAttributeApi.catalog().attributeValue(player, attributeId).orElse(0D);
    }

    @Override
    public boolean applyDamage(LivingEntity attacker,
            LivingEntity target,
            String damageTypeId,
            double baseDamage,
            Map<String, Object> context) {
        return EmakiAttributeApi.operations()
                .applyDamage(attacker, target, damageTypeId, baseDamage, context)
                .isSuccess();
    }

    @Override
    public boolean isItemContributionActive(Player player, ItemStack itemStack, String slotName) {
        return EmakiAttributeApi.catalog().isItemContributionActive(player, itemStack, slotName);
    }
}
