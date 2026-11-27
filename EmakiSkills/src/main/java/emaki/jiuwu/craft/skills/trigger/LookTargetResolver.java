package emaki.jiuwu.craft.skills.trigger;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public final class LookTargetResolver {

    private LookTargetResolver() {
    }

    public static Entity resolve(Player player, int maxDistance) {
        if (player == null || maxDistance <= 0) {
            return null;
        }
        Entity entity = player.getTargetEntity(maxDistance, false);
        if (!(entity instanceof LivingEntity living)) {
            return null;
        }
        if (living.equals(player) || !living.isValid() || living.isDead()) {
            return null;
        }
        return living;
    }
}
