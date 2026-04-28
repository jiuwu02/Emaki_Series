package emaki.jiuwu.craft.corelib.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Shared tab-completion utilities extracted from duplicated patterns
 * across multiple Emaki command routers.
 * <p>
 * All methods are stateless, null-safe, and return mutable lists so
 * callers can append additional entries when needed.
 */
public final class CommandTabHelper {

    private CommandTabHelper() {
    }

    // ── Core filter ────────────────────────────────────────────────

    /**
     * Filters candidates whose lowercase form starts with the given prefix.
     *
     * @param candidates possible completions (may be {@code null})
     * @param prefix     the partial input typed so far (may be {@code null})
     * @return a new mutable list of matching candidates
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

    // ── Online players ─────────────────────────────────────────────

    /**
     * Completes online player names matching the given prefix.
     *
     * @param prefix the partial input typed so far (may be {@code null})
     * @return a new mutable list of matching player names
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

    // ── Subcommands / literals ─────────────────────────────────────

    /**
     * Completes subcommand names matching the given prefix.
     * <p>
     * Equivalent to {@link #filterByPrefix(Collection, String)} but
     * reads more clearly at call sites that deal with subcommands.
     *
     * @param subcommands the available subcommand names (may be {@code null})
     * @param prefix      the partial input typed so far (may be {@code null})
     * @return a new mutable list of matching subcommands
     */
    public static List<String> completeSubcommands(Collection<String> subcommands, String prefix) {
        return filterByPrefix(subcommands, prefix);
    }

    /**
     * Completes a fixed set of literal values matching the given prefix.
     * <p>
     * Convenience overload that accepts varargs so callers don't need
     * to wrap values in {@code List.of(...)}.
     *
     * @param prefix the partial input typed so far (may be {@code null})
     * @param values the literal values to offer
     * @return a new mutable list of matching values
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

    // ── Numeric ranges ─────────────────────────────────────────────

    /**
     * Completes integer values in the range {@code [min, max]} (inclusive)
     * whose string form starts with the given prefix.
     *
     * @param prefix the partial input typed so far (may be {@code null})
     * @param min    the minimum value (inclusive)
     * @param max    the maximum value (inclusive); capped at {@code min + 100}
     *               to avoid generating excessively large lists
     * @return a new mutable list of matching integer strings
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
