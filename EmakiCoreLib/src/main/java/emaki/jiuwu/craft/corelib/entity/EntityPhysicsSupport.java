package emaki.jiuwu.craft.corelib.entity;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

public final class EntityPhysicsSupport {

    private EntityPhysicsSupport() {
    }

    public static void applyKnockback(LivingEntity target, Entity source, double strength) {
        if (target == null || source == null || strength <= 0D || !target.isValid() || target.isDead()) {
            return;
        }
        Vector direction = source.getLocation().toVector().subtract(target.getLocation().toVector());
        direction.setY(0D);
        if (direction.lengthSquared() < 1.0E-6D) {
            direction = source.getLocation().getDirection().multiply(-1D).setY(0D);
        }
        if (direction.lengthSquared() < 1.0E-6D) {
            return;
        }
        direction.normalize().multiply(strength);
        Vector velocity = target.getVelocity().clone();
        velocity.setX(velocity.getX() + direction.getX());
        velocity.setZ(velocity.getZ() + direction.getZ());
        velocity.setY(Math.max(0.1D, velocity.getY() + Math.min(0.4D, strength * 0.5D)));
        target.setVelocity(velocity);
    }
}
