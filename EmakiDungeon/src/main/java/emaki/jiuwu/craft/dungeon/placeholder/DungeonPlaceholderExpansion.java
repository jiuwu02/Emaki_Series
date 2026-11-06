package emaki.jiuwu.craft.dungeon.placeholder;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.placeholder.AbstractEmakiPlaceholderExpansion;
import emaki.jiuwu.craft.dungeon.party.Party;
import emaki.jiuwu.craft.dungeon.party.PartyService;

import me.clip.placeholderapi.PlaceholderAPI;

public final class DungeonPlaceholderExpansion extends AbstractEmakiPlaceholderExpansion {

    private static final String IDENTIFIER = "emakidungeon";
    private static final String DATA_PREFIX = "data_";
    private static final String PARTY_PREFIX = "party_";
    private static final Pattern DIFFICULTY_CLEARED =
            Pattern.compile("(.+)_difficulty_(.+)_cleared", Pattern.CASE_INSENSITIVE);
    private static final List<String> DATA_FIELDS =
            List.of("first_clear", "best_time", "clear_count", "best_score", "cleared");

    private final PartyService parties;
    private final DungeonRecordStore records;

    public DungeonPlaceholderExpansion(JavaPlugin plugin, PartyService parties,
            DungeonRecordStore records) {
        super(plugin);
        this.parties = parties;
        this.records = records;
    }

    @Override
    public String getIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null || Texts.isBlank(params)) {
            return "";
        }
        String raw = params.trim();
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.startsWith(DATA_PREFIX)) {
            return resolveData(player, raw, lower);
        }
        if (lower.startsWith(PARTY_PREFIX)) {
            return resolveParty(player, raw, lower);
        }
        return "";
    }

    private String resolveData(Player player, String raw, String lower) {
        String rest = raw.substring(DATA_PREFIX.length());
        String restLower = lower.substring(DATA_PREFIX.length());
        Matcher difficultyMatcher = DIFFICULTY_CLEARED.matcher(rest);
        if (difficultyMatcher.matches()) {
            String dungeonId = difficultyMatcher.group(1);
            String difficulty = difficultyMatcher.group(2);
            DungeonRecordStore.DungeonRecord record =
                    records.find(player.getUniqueId(), dungeonId);
            return Boolean.toString(record.difficulties().stream()
                    .anyMatch(value -> value.equalsIgnoreCase(difficulty)));
        }
        for (String field : DATA_FIELDS) {
            String suffix = "_" + field;
            if (restLower.endsWith(suffix)) {
                String dungeonId = rest.substring(0, rest.length() - suffix.length());
                if (dungeonId.isEmpty()) {
                    return "";
                }
                DungeonRecordStore.DungeonRecord record =
                        records.find(player.getUniqueId(), dungeonId);
                return formatField(field, record);
            }
        }
        return "";
    }

    private String formatField(String field, DungeonRecordStore.DungeonRecord record) {
        return switch (field) {
            case "cleared" -> Boolean.toString(record.cleared());
            case "first_clear" -> Long.toString(record.firstClearMs());
            case "best_time" -> Long.toString(record.bestTimeMs());
            case "clear_count" -> Long.toString(record.clearCount());
            case "best_score" -> formatScore(record.bestScore());
            default -> "";
        };
    }

    private String formatScore(double score) {
        double rounded = Math.round(score * 10.0D) / 10.0D;
        if (rounded == Math.rint(rounded)) {
            return Long.toString((long) rounded);
        }
        return Double.toString(rounded);
    }

    private String resolveParty(Player player, String raw, String lower) {
        String rest = raw.substring(PARTY_PREFIX.length());
        String restLower = lower.substring(PARTY_PREFIX.length());
        int open = rest.indexOf('{');
        if (open < 0) {
            return resolveFixed(player, restLower);
        }
        int close = rest.lastIndexOf('}');
        if (close <= open) {
            return "";
        }
        String inner = rest.substring(open + 1, close);
        if (inner.isEmpty()) {
            return "";
        }
        String head = rest.substring(0, open);
        if (!head.endsWith("_")) {
            return "";
        }
        String source = head.substring(0, head.length() - 1);
        Player sourcePlayer = resolveSource(player, source);
        if (sourcePlayer == null) {
            return "";
        }
        return PlaceholderAPI.setBracketPlaceholders(sourcePlayer, "{" + inner + "}");
    }

    private String resolveFixed(Player player, String restLower) {
        if (restLower.equals("in_party")) {
            return Boolean.toString(parties.partyOf(player.getUniqueId()) != null);
        }
        if (restLower.equals("size")) {
            Party party = parties.partyOf(player.getUniqueId());
            return Integer.toString(party == null ? 0 : party.size());
        }
        return "";
    }

    private Player resolveSource(Player player, String source) {
        if (source.equalsIgnoreCase("self")) {
            return player;
        }
        Party party = parties.partyOf(player.getUniqueId());
        if (party == null) {
            return null;
        }
        if (source.equalsIgnoreCase("leader")) {
            UUID leaderId = party.leaderId();
            return leaderId == null ? null : Bukkit.getPlayer(leaderId);
        }
        if (source.toLowerCase(Locale.ROOT).startsWith("member_")) {
            int index;
            try {
                index = Integer.parseInt(source.substring("member_".length()));
            } catch (NumberFormatException exception) {
                return null;
            }
            List<UUID> members = party.members();
            if (index < 1 || index > members.size()) {
                return null;
            }
            return Bukkit.getPlayer(members.get(index - 1));
        }
        return null;
    }
}
