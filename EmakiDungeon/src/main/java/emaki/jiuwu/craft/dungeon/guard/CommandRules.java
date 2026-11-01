package emaki.jiuwu.craft.dungeon.guard;

import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class CommandRules {

    public static final class Pattern {

        private final String root;
        private final List<String> params;

        public Pattern(String root, List<String> params) {
            this.root = root;
            this.params = params;
        }

        public String root() {
            return root;
        }

        public int paramCount() {
            return params.size();
        }

        public boolean matches(String commandRoot, String[] args) {
            if (!root.equalsIgnoreCase(commandRoot)) {
                return false;
            }
            return matchParams(params, args, 0, 0);
        }

        private static boolean matchParams(List<String> patterns, String[] args, int patternIndex, int argsIndex) {
            if (patternIndex >= patterns.size()) {
                return argsIndex >= args.length;
            }
            String pattern = patterns.get(patternIndex);
            if ("*".equals(pattern)) {
                for (int consume = argsIndex; consume <= args.length; consume++) {
                    if (matchParams(patterns, args, patternIndex + 1, consume)) {
                        return true;
                    }
                }
                return false;
            }
            if (argsIndex >= args.length) {
                return false;
            }
            if ("player".equals(pattern)) {
                return matchParams(patterns, args, patternIndex + 1, argsIndex + 1);
            }
            if (pattern.equalsIgnoreCase(args[argsIndex])) {
                return matchParams(patterns, args, patternIndex + 1, argsIndex + 1);
            }
            return false;
        }
    }

    private CommandRules() {
    }

    public static List<Pattern> parse(List<String> rules) {
        List<Pattern> patterns = new ArrayList<>();
        if (rules == null) {
            return patterns;
        }
        for (String rule : rules) {
            Pattern pattern = parse(rule);
            if (pattern != null) {
                patterns.add(pattern);
            }
        }
        patterns.sort((left, right) -> Integer.compare(right.paramCount(), left.paramCount()));
        return patterns;
    }

    public static Pattern parse(String rule) {
        if (Texts.isBlank(rule)) {
            return null;
        }
        String[] tokens = Texts.trim(rule).split("\\s+");
        if (tokens.length == 0) {
            return null;
        }
        String root = tokens[0];
        if (root.startsWith("/")) {
            root = root.substring(1);
        }
        if (root.isEmpty()) {
            return null;
        }
        List<String> params = new ArrayList<>(tokens.length - 1);
        for (int i = 1; i < tokens.length; i++) {
            params.add(Texts.lower(tokens[i]));
        }
        return new Pattern(root, params);
    }

    public static boolean matchesAny(List<Pattern> patterns, String root, String[] args) {
        for (Pattern pattern : patterns) {
            if (pattern.matches(root, args)) {
                return true;
            }
        }
        return false;
    }
}
