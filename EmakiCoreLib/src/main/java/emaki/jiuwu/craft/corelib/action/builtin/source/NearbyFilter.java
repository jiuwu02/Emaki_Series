package emaki.jiuwu.craft.corelib.action.builtin.source;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

/**
 * The filter chain the v1 {@code KillEntityAction} carried inside its {@code execute}.
 *
 * <p>Extracted as a pure function over an already-collected candidate list so that the ordering and
 * truncation rules can be verified without a live world. The rules are kept verbatim: drop null and
 * dead entities, drop players unless {@code includePlayers}, keep only the requested type, order by
 * squared distance to the centre, then truncate to {@code limit}.</p>
 */
final class NearbyFilter {

    private NearbyFilter() {
    }

    /**
     * Applies the filter chain.
     *
     * @param candidates raw candidates, typically from {@code World#getNearbyEntities}
     * @param centre distance reference point
     * @param type type filter, {@code null} to accept every type
     * @param includePlayers whether player entities are eligible
     * @param limit maximum number of results, at least one
     * @param playersOnly when true only player entities are eligible, used by {@code nearby_players}
     * @return the filtered, ordered and truncated result
     */
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
            // Sorting must stay total. A candidate in another world cannot be ordered against the centre,
            // so it sorts last rather than throwing out of the comparator.
            return Double.MAX_VALUE;
        }
        return location.distanceSquared(centre);
    }
}
