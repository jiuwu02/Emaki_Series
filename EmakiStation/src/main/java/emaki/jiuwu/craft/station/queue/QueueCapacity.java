package emaki.jiuwu.craft.station.queue;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.station.definition.StationDefinition;

/**
 * Resolves a player's effective queue length at a station.
 *
 * <h2>How the three inputs combine</h2>
 * <pre>
 * effective = min( max(base, highest permission tier) + purchased , station max )
 * </pre>
 *
 * <p>Permission tiers <em>replace</em> the baseline rather than adding to it: a node of
 * {@code emakistation.queue.10} means "this player's queue is 10", not "ten more than the default". That is
 * the existing published behaviour and is deliberately left alone.
 *
 * <p>Purchased slots, by contrast, <em>add</em>. A player who paid for capacity must not lose it because an
 * administrator later granted them a permission tier, and must not have their purchase silently absorbed into
 * a tier they already held.
 *
 * <p>The station ceiling always applies last, so neither a permission nor a purchase can exceed what the
 * station allows. The purchase service is responsible for refusing a sale that the ceiling would swallow;
 * this class only reports the resulting number.
 */
public final class QueueCapacity {

    private static final String TIER_PREFIX = "emakistation.queue.";

    private QueueCapacity() {
    }

    /**
     * Computes the effective queue length for one player at one station, ignoring purchases.
     *
     * @param player  the player; {@code null} yields the station baseline
     * @param station the station; {@code null} yields zero
     * @return the effective length ceiling
     */
    public static int effectiveLength(Player player, StationDefinition station) {
        return effectiveLength(player, station, 0);
    }

    /**
     * Computes the effective queue length for one player at one station.
     *
     * @param player          the player; {@code null} yields the station baseline
     * @param station         the station; {@code null} yields zero
     * @param purchasedSlots  how many slots the player has bought at this station; negatives are ignored
     * @return the effective length ceiling
     */
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
                // A non-numeric suffix is not a tier node; skip it rather than failing the lookup.
            }
        }
        return Math.min(best + granted, ceiling);
    }

    /**
     * Reports how many more slots a player could still buy at a station.
     *
     * @param player         the player
     * @param station        the station
     * @param purchasedSlots how many slots the player has already bought here
     * @return the remaining headroom under the station ceiling; never negative
     */
    public static int purchaseHeadroom(Player player, StationDefinition station, int purchasedSlots) {
        if (station == null) {
            return 0;
        }
        int current = effectiveLength(player, station, purchasedSlots);
        return Math.max(0, station.queueSettings().maxLength() - current);
    }
}
