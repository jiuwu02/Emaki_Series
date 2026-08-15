package emaki.jiuwu.craft.attribute.service;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import emaki.jiuwu.craft.attribute.config.ShieldConfig;

final class ShieldBlockResolver {

    static final String TARGET_BLOCKING = "target_blocking";

    private ShieldBlockResolver() {
    }

    static boolean isBlocking(LivingEntity target, Entity damageSource, ShieldConfig shield) {
        if (target == null || shield == null || !shield.attributeModeEnabled()) {
            return false;
        }
        if (!isRaisingShield(target)) {
            return false;
        }
        return !shield.requireFacing() || facesSource(target, damageSource, shield.facingAngleDegrees());
    }

    private static boolean isRaisingShield(LivingEntity target) {
        if (target instanceof HumanEntity humanEntity) {
            return humanEntity.isBlocking();
        }
        return target.isHandRaised() && isShield(target.getActiveItem());
    }

    private static boolean isShield(ItemStack itemStack) {
        return itemStack != null && itemStack.getType() == Material.SHIELD;
    }

    private static boolean facesSource(LivingEntity target, Entity damageSource, double facingAngleDegrees) {
        if (damageSource == null) {
            return false;
        }
        Vector view = target.getLocation().getDirection().setY(0D);
        Vector toSource = damageSource.getLocation().toVector()
                .subtract(target.getLocation().toVector())
                .setY(0D);
        if (view.lengthSquared() <= 0D || toSource.lengthSquared() <= 0D) {
            return false;
        }
        double cosine = view.normalize().dot(toSource.normalize());
        return cosine > Math.cos(Math.toRadians(facingAngleDegrees / 2D));
    }
}
