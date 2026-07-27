package emaki.jiuwu.craft.storage.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A parsed search expression.
 *
 * <p><strong>No regular expression is ever compiled</strong>, not even behind an admin flag: a
 * hostile pattern can stall the server through catastrophic backtracking, and substring matching
 * covers the actual use case. Every term is matched with {@link String#contains(CharSequence)}
 * against text already lower-cased with {@link Locale#ROOT}.
 */
public record SearchQuery(List<Term> terms) {

    /** Which pre-computed text a term is matched against. */
    public enum Scope {
        /** Name and lore combined. */
        ANY,
        /** Display name only. */
        NAME,
        /** Lore only. */
        LORE,
        /** Material key or ItemSource id only. */
        ID
    }

    /**
     * One parsed term.
     *
     * @param scope   which text to match
     * @param text    the lower-cased needle
     * @param exclude whether a match rejects rather than accepts the entry
     */
    public record Term(Scope scope, String text, boolean exclude) {
    }

    /** The operator prefixes, configurable so they can avoid clashing with item names. */
    public record Operators(String name, String lore, String id, String exclude) {

        public static Operators defaults() {
            return new Operators("@", "#", "$", "!");
        }
    }

    public SearchQuery(List<Term> terms) {
        this.terms = List.copyOf(terms);
    }

    /** {@return an always-matching query} */
    public static SearchQuery empty() {
        return new SearchQuery(List.of());
    }

    public boolean isEmpty() {
        return terms.isEmpty();
    }

    /**
     * Parses raw chat input into terms.
     *
     * <p>Whitespace separates terms and every term must match (AND). A term may carry one scope
     * prefix and one exclusion prefix in either order.
     *
     * @param raw       the raw user input
     * @param operators the configured prefixes
     * @return the parsed query, empty when {@code raw} carries no usable term
     */
    public static SearchQuery parse(String raw, Operators operators) {
        if (raw == null || raw.isBlank()) {
            return empty();
        }
        Operators ops = operators == null ? Operators.defaults() : operators;
        List<Term> parsed = new ArrayList<>();
        for (String token : raw.trim().split("\\s+")) {
            Term term = parseTerm(token, ops);
            if (term != null) {
                parsed.add(term);
            }
        }
        return new SearchQuery(parsed);
    }

    private static Term parseTerm(String token, Operators ops) {
        String remaining = token;
        boolean exclude = false;
        Scope scope = Scope.ANY;
        boolean consumed = true;
        while (consumed && !remaining.isEmpty()) {
            consumed = false;
            if (!exclude && matches(remaining, ops.exclude())) {
                exclude = true;
                remaining = remaining.substring(ops.exclude().length());
                consumed = true;
                continue;
            }
            if (scope == Scope.ANY) {
                if (matches(remaining, ops.name())) {
                    scope = Scope.NAME;
                    remaining = remaining.substring(ops.name().length());
                    consumed = true;
                } else if (matches(remaining, ops.lore())) {
                    scope = Scope.LORE;
                    remaining = remaining.substring(ops.lore().length());
                    consumed = true;
                } else if (matches(remaining, ops.id())) {
                    scope = Scope.ID;
                    remaining = remaining.substring(ops.id().length());
                    consumed = true;
                }
            }
        }
        if (remaining.isBlank()) {
            return null;
        }
        return new Term(scope, remaining.toLowerCase(Locale.ROOT), exclude);
    }

    private static boolean matches(String token, String prefix) {
        return prefix != null && !prefix.isEmpty() && token.startsWith(prefix);
    }
}
