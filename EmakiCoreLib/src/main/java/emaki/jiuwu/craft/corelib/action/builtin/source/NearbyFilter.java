package emaki.jiuwu.craft.corelib.action.builtin.source;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

final class NearbyFilter {

    private NearbyFilter() {
    }

    static List<Entity> apply(List<? extends Entity> candidates,
            Location centre,
            EntityType type,
            boolean includePlayers,
            int limit,
            boolean playersOnly) {
        if (candidates == null || candidates.isEmpty() || centre == null) {
            return List.of();
        }
        List<Entity> filtered = new ArrayList<>(candidates.size());
        for (Entity entity : candidates) {
            if (entity == null || entity.isDead()) {
                continue;
            }
            boolean isPlayer = entity instanceof Player;
            if (playersOnly ? !isPlayer : (!includePlayers && isPlayer)) {
                continue;
            }
            if (type != null && entity.getType() != type) {
                continue;
            }
            filtered.add(entity);
        }
        filtered.sort(Comparator.comparingDouble(entity -> distanceSquared(entity, centre)));
        int cap = Math.max(1, limit);
        return filtered.size() <= cap ? List.copyOf(filtered) : List.copyOf(filtered.subList(0, cap));
    }

    private static double distanceSquared(Entity entity, Location centre) {
        Location location = entity.getLocation();
        if (location == null || location.getWorld() == null || centre.getWorld() == null
                || !location.getWorld().equals(centre.getWorld())) {

            return Double.MAX_VALUE;
        }
        return location.distanceSquared(centre);
    }
}
