package emaki.jiuwu.craft.kits.papi;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.OfflinePlayer;

import emaki.jiuwu.craft.corelib.placeholder.AbstractEmakiPlaceholderExpansion;
import emaki.jiuwu.craft.kits.EmakiKitsPlugin;

public final class KitsPlaceholderExpansion extends AbstractEmakiPlaceholderExpansion {

    private final EmakiKitsPlugin plugin;

    public KitsPlaceholderExpansion(EmakiKitsPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "emakikits";
    }

    @Override
    public String getAuthor() {
        return "JiuWu";
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null) {
            return "";
        }
        String lower = params.toLowerCase(Locale.ROOT);
        if (!"invited".equals(lower) && !"invited_count".equals(lower)) {
            return "";
        }
        if (player == null || player.getUniqueId() == null) {
            return "";
        }
        Map<UUID, Long> counts = plugin.kitService().invitedCounts();
        long count = counts == null ? 0L : counts.getOrDefault(player.getUniqueId(), 0L);
        return String.valueOf(count);
    }
}
