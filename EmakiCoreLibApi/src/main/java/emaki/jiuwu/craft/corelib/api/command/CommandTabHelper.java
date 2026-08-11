package emaki.jiuwu.craft.corelib.api.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class CommandTabHelper {

    private CommandTabHelper() {
    }


    public static List<String> filterByPrefix(Collection<String> candidates, String prefix) {
        if (candidates == null || candidates.isEmpty()) {
            return new ArrayList<>();
        }
        String lowered = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate != null && candidate.toLowerCase(Locale.ROOT).startsWith(lowered)) {
                result.add(candidate);
            }
        }
        return result;
    }


    public static List<String> completeOnlinePlayers(String prefix) {
        String lowered = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(lowered)) {
                result.add(player.getName());
            }
        }
        return result;
    }


    public static List<String> completeSubcommands(Collection<String> subcommands, String prefix) {
        return filterByPrefix(subcommands, prefix);
    }

    public static List<String> completeLiterals(String prefix, String... values) {
        if (values == null || values.length == 0) {
            return new ArrayList<>();
        }
        String lowered = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).startsWith(lowered)) {
                result.add(value);
            }
        }
        return result;
    }


    public static List<String> completeIntRange(String prefix, int min, int max) {
        int safeMax = Math.min(max, min + 100);
        String lowered = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (int i = min; i <= safeMax; i++) {
            String value = Integer.toString(i);
            if (value.startsWith(lowered)) {
                result.add(value);
            }
        }
        return result;
    }
}
