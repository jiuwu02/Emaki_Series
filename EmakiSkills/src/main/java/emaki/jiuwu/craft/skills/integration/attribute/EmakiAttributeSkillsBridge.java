package emaki.jiuwu.craft.skills.integration.attribute;

import java.util.Map;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.attribute.api.EmakiAttributeApi;
import emaki.jiuwu.craft.skills.integration.SkillsAttributeBridge;

/**
 * {@link SkillsAttributeBridge} implementation backed by the canonical
 * {@link EmakiAttributeApi} facade.
 *
 * <p>This is the only class in EmakiSkills that references EmakiAttributeApi
 * types; it is class-loaded exclusively by {@code SkillsAttributeBridgeHolder}
 * once EmakiAttribute is enabled. Every call goes through the static facade, so
 * a reloaded or disabled EmakiAttribute is never reached through a stale bridge.
 */
public final class EmakiAttributeSkillsBridge implements SkillsAttributeBridge {

    /**
     * Creates the bridge. Invoked reflectively by
     * {@code SkillsAttributeBridgeHolder} only when EmakiAttribute is enabled.
     *
     * @return the EmakiAttribute-backed bridge
     */
    public static SkillsAttributeBridge create() {
        return new EmakiAttributeSkillsBridge();
    }

    private EmakiAttributeSkillsBridge() {
    }

    @Override
    public boolean available() {
        return EmakiAttributeApi.available();
    }

    @Override
    public double readResourceCurrent(Player player, String resourceId) {
        return EmakiAttributeApi.readResourceCurrent(player, resourceId);
    }

    @Override
    public double readResourceMax(Player player, String resourceId) {
        return EmakiAttributeApi.readResourceMax(player, resourceId);
    }

    @Override
    public boolean consumeResource(Player player, String resourceId, double amount) {
        return EmakiAttributeApi.consumeResource(player, resourceId, amount);
    }

    @Override
    public double readAttributeValue(Player player, String attributeId) {
        return EmakiAttributeApi.readAttributeValue(player, attributeId);
    }

    @Override
    public boolean applyDamage(LivingEntity attacker,
            LivingEntity target,
            String damageTypeId,
            double baseDamage,
            Map<String, Object> context) {
        return EmakiAttributeApi.applyDamage(attacker, target, damageTypeId, baseDamage, context);
    }
}
