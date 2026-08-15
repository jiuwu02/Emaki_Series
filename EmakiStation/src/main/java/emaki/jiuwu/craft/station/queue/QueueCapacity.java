package emaki.jiuwu.craft.station.queue;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.station.definition.StationDefinition;

public final class QueueCapacity {

    private static final String TIER_PREFIX = "emakistation.queue.";

    private QueueCapacity() {
    }

    public static int effectiveLength(Player player, StationDefinition station) {
        return effectiveLength(player, station, 0);
    }

    public static int effectiveLength(Player player, StationDefinition station, int purchasedSlots) {
        if (station == null) {
            return 0;
        }
        int granted = Math.max(0, purchasedSlots);
        int base = station.queueSettings().baseLength();
        int ceiling = station.queueSettings().maxLength();
        if (player == null || !station.queueSettings().permissionTiers()) {
            return Math.min(base + granted, ceiling);
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

            }
        }
        return Math.min(best + granted, ceiling);
    }

    public static int purchaseHeadroom(Player player, StationDefinition station, int purchasedSlots) {
        if (station == null) {
            return 0;
        }
        int current = effectiveLength(player, station, purchasedSlots);
        return Math.max(0, station.queueSettings().maxLength() - current);
    }
}
