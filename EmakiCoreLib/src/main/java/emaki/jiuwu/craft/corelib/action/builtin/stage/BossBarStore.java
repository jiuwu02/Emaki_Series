package emaki.jiuwu.craft.corelib.action.builtin.stage;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * Per-player boss bars owned by the boss bar stages, keyed by player and bar id.
 *
 * <p>Separate from the v1 {@code BuiltinBossBarRegistry} on purpose. That class is package-private inside
 * {@code action.builtin} and phase 6 deletes it; sharing state across the two would make the v2 stages
 * depend on code scheduled for removal. During the dual-registration window each side owns the bars it
 * created, which is correct because a given configuration goes down exactly one of the two paths.</p>
 */
final class BossBarStore {

    private static final ConcurrentMap<Key, BossBar> BARS = new ConcurrentHashMap<>();

    private BossBarStore() {
    }

    static BossBar show(Player player,
            String id,
            String title,
            BarColor color,
            BarStyle style,
            double progress,
            BarFlag... flags) {
        Key key = key(player, id);
        if (key == null) {
            return null;
        }
        hide(player, id);
        BossBar bossBar = Bukkit.createBossBar(Texts.toStringSafe(title),
                color == null ? BarColor.PURPLE : color,
                style == null ? BarStyle.SOLID : style,
                flags == null ? new BarFlag[0] : flags);
        bossBar.setProgress(Math.max(0D, Math.min(1D, progress)));
        bossBar.addPlayer(player);
        BARS.put(key, bossBar);
        return bossBar;
    }

    static boolean hide(Player player, String id) {
        Key key = key(player, id);
        if (key == null) {
            return false;
        }
        BossBar removed = BARS.remove(key);
        if (removed == null) {
            return false;
        }
        removed.removeAll();
        return true;
    }

    static int hideAll(Player player) {
        if (player == null) {
            return 0;
        }
        UUID playerId = player.getUniqueId();
        int removedCount = 0;
        Iterator<Map.Entry<Key, BossBar>> iterator = BARS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Key, BossBar> entry = iterator.next();
            if (!playerId.equals(entry.getKey().playerId())) {
                continue;
            }
            BossBar bossBar = entry.getValue();
            if (bossBar != null) {
                bossBar.removeAll();
            }
            if (BARS.remove(entry.getKey(), bossBar)) {
                removedCount++;
            }
        }
        return removedCount;
    }

    /** Detaches every tracked bar. Called when CoreLib shuts down, so no bar outlives the plugin. */
    static void clear() {
        for (Key key : Map.copyOf(BARS).keySet()) {
            BossBar removed = BARS.remove(key);
            if (removed != null) {
                removed.removeAll();
            }
        }
    }

    private static Key key(Player player, String id) {
        if (player == null || Texts.isBlank(id)) {
            return null;
        }
        String normalizedId = Texts.normalizeId(id);
        return Texts.isBlank(normalizedId) ? null : new Key(player.getUniqueId(), normalizedId);
    }

    private record Key(UUID playerId, String id) {
    }
}
