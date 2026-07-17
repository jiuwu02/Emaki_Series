package emaki.jiuwu.craft.level.papi;

import java.util.List;
import java.util.Locale;

import org.bukkit.OfflinePlayer;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import emaki.jiuwu.craft.level.EmakiLevelPlugin;
import emaki.jiuwu.craft.level.config.LevelTypeConfig;
import emaki.jiuwu.craft.level.model.PlayerLevelData;
import emaki.jiuwu.craft.level.model.PlayerLevelEntry;
import emaki.jiuwu.craft.level.service.LevelTopService;
import emaki.jiuwu.craft.level.service.PlayerLevelService;

public final class LevelPlaceholderExpansion extends PlaceholderExpansion {

    private final EmakiLevelPlugin plugin;

    public LevelPlaceholderExpansion(EmakiLevelPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "emakilevel";
    }

    @Override
    public String getAuthor() {
        return "JiuWu";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null) {
            return "";
        }
        String lower = params.toLowerCase(Locale.ROOT);
        if (lower.startsWith("top_")) {
            return top(lower.substring("top_".length()));
        }
        if (player == null || player.getUniqueId() == null) {
            return "";
        }
        ParsedPlayerPlaceholder parsed = parsePlayerPlaceholder(lower);
        if (parsed == null) {
            return "";
        }
        String field = parsed.field();
        String typeId = parsed.typeId();
        LevelTypeConfig type = plugin.typeRegistry().type(typeId).orElse(null);
        if (type == null) {
            return "";
        }
        PlayerLevelData data = plugin.dataStore().cached(player.getUniqueId());
        PlayerLevelEntry entry = data == null ? null : data.entry(type.id());
        if (entry == null) {
            return "";
        }
        double required = plugin.requirementService().requiredExp(type, entry, Math.min(type.maxLevel(), entry.level() + 1));
        double progress = required <= 0D ? 1D : Math.min(1D, entry.exp() / required);
        return switch (field) {
            case "level" -> String.valueOf(entry.level());
            case "exp" -> PlayerLevelService.format(entry.exp());
            case "total", "totalexp", "total_exp" -> PlayerLevelService.format(entry.totalExp());
            case "required", "requiredexp", "required_exp" -> PlayerLevelService.format(required);
            case "progress" -> PlayerLevelService.format(progress);
            case "progresspercent", "progress_percent" -> PlayerLevelService.format(progress * 100D);
            default -> "";
        };
    }

    private ParsedPlayerPlaceholder parsePlayerPlaceholder(String payload) {
        for (String field : List.of("progress_percent", "required_exp", "total_exp", "totalexp", "requiredexp", "progress", "level", "exp", "total", "required")) {
            String prefix = field + "_";
            if (payload.startsWith(prefix)) {
                return new ParsedPlayerPlaceholder(field, payload.substring(prefix.length()));
            }
        }
        return null;
    }

    private String top(String payload) {
        String[] parts = payload.split("_");
        if (parts.length < 3) {
            return "";
        }
        String typeId = parts[0];
        int rank;
        try {
            rank = Integer.parseInt(parts[1]);
        } catch (NumberFormatException exception) {
            return "";
        }
        String field = parts[2];
        List<LevelTopService.TopEntry> top = plugin.topService().top(typeId, Math.max(1, rank));
        if (rank <= 0 || top.size() < rank) {
            return "";
        }
        LevelTopService.TopEntry entry = top.get(rank - 1);
        return switch (field) {
            case "name" -> entry.name();
            case "level" -> String.valueOf(entry.level());
            case "total", "totalexp", "total_exp" -> PlayerLevelService.format(entry.totalExp());
            default -> "";
        };
    }

    private record ParsedPlayerPlaceholder(String field, String typeId) {
    }
}
