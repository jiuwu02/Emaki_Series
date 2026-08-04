package emaki.jiuwu.craft.station.queue;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.station.definition.StationDefinition;

/**
 * Resolves a player's effective queue length at a station.
 *
 * <p>Permission tiers follow the repository's existing {@code <plugin>.<thing>.<n>} convention: the highest
 * numbered node a player holds wins, and no node at all leaves the station baseline untouched. The station
 * ceiling always applies last, so a permission cannot exceed what the station allows.
 */
public final class QueueCapacity {

    private static final String TIER_PREFIX = "emakistation.queue.";

    private QueueCapacity() {
    }

    /**
     * Computes the effective queue length for one player at one station.
     *
     * @param player  the player; {@code null} yields the station baseline
     * @param station the station; {@code null} yields zero
     * @return the effective length ceiling
     */
    public static int effectiveLength(Player player, StationDefinition station) {
        if (station == null) {
            return 0;
        }
        int base = station.queueSettings().baseLength();
        if (player == null || !station.queueSettings().permissionTiers()) {
            return Math.min(base, station.queueSettings().maxLength());
        }
        int best = base;
        for (var attachment : player.getEffectivePermissions()) {
            String node = attachment.getPermission();
            if (node == null || !node.startsWith(TIER_PREFIX) || !attachment.getValue()) {
                continue;
            }
            String suffix = node.substring(TIER_PREFIX.length());
            try {
                best = Math.max(best, Integer.parseInt(suffix));
            } catch (NumberFormatException ignored) {
                // A non-numeric suffix is not a tier node; skip it rather than failing the lookup.
            }
        }
        return Math.min(best, station.queueSettings().maxLength());
    }
}
