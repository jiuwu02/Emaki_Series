package emaki.jiuwu.craft.kits.join;

import org.bukkit.Statistic;
import org.bukkit.entity.Player;

public final class PlaytimeTracker {

    private PlaytimeTracker() {
    }

    public static long playedSeconds(Player player) {
        long ticks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        return Math.max(0L, ticks / 20L);
    }

    public static boolean reached(Player player, long thresholdSeconds) {
        return playedSeconds(player) >= Math.max(0L, thresholdSeconds);
    }
}
