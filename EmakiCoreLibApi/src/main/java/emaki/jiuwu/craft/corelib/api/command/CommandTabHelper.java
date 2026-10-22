package emaki.jiuwu.craft.corelib.api.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Static helpers for building tab-completion suggestions in plugin command handlers.
 *
 * <p>All prefix matching is case-insensitive and uses {@link Locale#ROOT}, a {@code null} prefix
 * is treated as an empty prefix (matching everything), and every method returns a fresh mutable
 * {@link ArrayList} rather than {@code null}, so callers can post-process the suggestions freely.
 */
public final class CommandTabHelper {

    private CommandTabHelper() {
    }


    /**
     * Keeps only the candidates that start with {@code prefix}, ignoring case.
     *
     * @param candidates suggestions to filter; {@code null} or empty yields an empty result and
     *                   {@code null} elements are skipped
     * @param prefix prefix to match; {@code null} matches every candidate
     * @return a new mutable list of the matching candidates in their original order
     */
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


    /**
     * {@return the names of currently online players that start with {@code prefix}, ignoring
     * case}
     *
     * <p>Reads {@link Bukkit#getOnlinePlayers()} as-is, so the order follows the server's
     * iteration order rather than alphabetical order.
     *
     * @param prefix prefix to match; {@code null} matches every online player
     */
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


    /**
     * Filters declared subcommand names by {@code prefix}.
     *
     * <p>Delegates to {@link #filterByPrefix(Collection, String)} and exists so tab completers
     * read in terms of subcommands.
     *
     * @param subcommands declared subcommand names; {@code null} or empty yields an empty result
     * @param prefix prefix to match; {@code null} matches every subcommand
     * @return a new mutable list of matching subcommands in their original order
     */
    public static List<String> completeSubcommands(Collection<String> subcommands, String prefix) {
        return filterByPrefix(subcommands, prefix);
    }

    /**
     * Keeps only the literal values that start with {@code prefix}, ignoring case.
     *
     * @param prefix prefix to match; {@code null} matches every non-null value
     * @param values literal suggestions; a {@code null} or empty array yields an empty result and
     *               {@code null} elements are skipped
     * @return a new mutable list of matching literals in declaration order
     */
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


    /**
     * Produces decimal suggestions from {@code min} upward that start with {@code prefix}.
     *
     * <p>The scan stops at {@code min + 100} (at most 101 candidates) so a wide or negative range
     * cannot flood the completion list. Candidates are tested on their decimal representation and
     * appended in ascending numeric order; a range whose {@code max} is below {@code min} yields
     * an empty result.
     *
     * @param prefix decimal prefix to match, such as {@code "1"}; {@code null} matches the whole
     *               capped range
     * @param min first value of the range (inclusive)
     * @param max last value of the range (inclusive); only the first 101 values are considered
     * @return a new mutable list of matching numbers in ascending order
     */
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
