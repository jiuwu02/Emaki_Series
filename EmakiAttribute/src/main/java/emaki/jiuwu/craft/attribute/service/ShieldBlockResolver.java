package emaki.jiuwu.craft.attribute.service;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import emaki.jiuwu.craft.attribute.config.ShieldConfig;

/**
 * 判定目标本次受击是否处于有效举盾状态。
 *
 * <p>仅在 {@link ShieldConfig#attributeModeEnabled()} 为 true 时参与结算。原版
 * {@code BLOCKING} 减伤自带正面判定，一旦被清零改由 EA 接管，正面判定必须在此重建，
 * 否则格挡会退化为全向生效。
 */
final class ShieldBlockResolver {

    /**
     * 伤害上下文变量键：目标本次是否有效举盾，{@code 1} 为是、{@code 0} 为否。
     *
     * <p>该变量始终存在于伤害阶段表达式作用域中——{@code createDamageContext} 会为每次
     * 实体伤害写入真实值，{@code StageCalculator} 另有默认 {@code 0} 兜底。缺失该变量会让
     * 引用它的表达式求值失败并把该阶段结果压成 0，因此两处都不能省。
     */
    static final String TARGET_BLOCKING = "target_blocking";

    private ShieldBlockResolver() {
    }

    /**
     * {@return 目标是否在本次伤害中构成有效格挡}
     *
     * @param target 受击实体
     * @param damageSource 伤害来源实体；环境伤害为 {@code null}
     * @param shield 盾牌设置
     */
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

    /**
     * 复现原版半平面判定：来源指向目标的水平向量与目标视线水平分量夹角超过阈值的一半时才算正面。
     *
     * <p>{@code facingAngleDegrees} 为 180 时等价于原版行为。来源缺失或两者水平重合时
     * 无法确定方向，按不构成正面处理。
     */
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
