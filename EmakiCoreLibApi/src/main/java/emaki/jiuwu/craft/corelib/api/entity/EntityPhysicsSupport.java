package emaki.jiuwu.craft.corelib.api.entity;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * Utility that pushes living entities apart with a horizontal knockback impulse.
 *
 * <p>Invalid input turns into a no-op instead of an exception, and the helper performs no
 * scheduling of its own.
 */
public final class EntityPhysicsSupport {

    private EntityPhysicsSupport() {
    }

    /**
     * Pushes {@code target} away from {@code source} with a horizontal impulse.
     *
     * <p>The impulse runs along the horizontal vector from {@code source} to {@code target}; when
     * both stand on the same spot the direction falls back to the reverse of {@code source}'s
     * facing, and a direction that is still degenerate aborts the call. The target's horizontal
     * velocity grows by {@code strength} along that direction, while the vertical component gains
     * {@code min(0.4, strength * 0.5)} but never ends up below {@code 0.1}.
     *
     * <p><strong>Thread:</strong> the caller must already own {@code target} (its region thread
     * under Folia); this method schedules nothing.
     *
     * @param target entity to push; {@code null}, dead or invalid makes the call a no-op
     * @param source entity to push away from; {@code null} makes the call a no-op
     * @param strength impulse strength; zero or negative makes the call a no-op
     */
    public static void applyKnockback(LivingEntity target, Entity source, double strength) {
        if (target == null || source == null || strength <= 0D || !target.isValid() || target.isDead()) {
            return;
        }
        Vector direction = target.getLocation().toVector().subtract(source.getLocation().toVector());
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
