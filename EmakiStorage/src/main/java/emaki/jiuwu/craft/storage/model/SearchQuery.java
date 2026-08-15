package emaki.jiuwu.craft.storage.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record SearchQuery(List<Term> terms) {

    public enum Scope {

        ANY,

        NAME,

        LORE,

        ID
    }

    public record Term(Scope scope, String text, boolean exclude) {
    }

    public record Operators(String name, String lore, String id, String exclude) {

        public static Operators defaults() {
            return new Operators("@", "#", "$", "!");
        }
    }

    public SearchQuery(List<Term> terms) {
        this.terms = List.copyOf(terms);
    }

    public static SearchQuery empty() {
        return new SearchQuery(List.of());
    }

    public boolean isEmpty() {
        return terms.isEmpty();
    }

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
