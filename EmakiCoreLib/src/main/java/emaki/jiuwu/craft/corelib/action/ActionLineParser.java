package emaki.jiuwu.craft.corelib.action;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.text.Texts;

public final class ActionLineParser {

    public ParsedActionLine parse(int lineNumber, String rawLine) throws ActionSyntaxException {
        String raw = rawLine == null ? "" : rawLine;
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return null;
        }
        List<String> tokens = tokenize(lineNumber, raw);
        if (tokens.isEmpty()) {
            return null;
        }
        String actionId = null;
        Map<String, String> arguments = new LinkedHashMap<>();
        String condition = null;
        String chance = null;
        String delay = null;
        boolean ignoreFailure = false;
        for (String token : tokens) {
            if (actionId == null && token.startsWith("@")) {
                int eq = token.indexOf('=');
                String key = eq < 0 ? token.substring(1) : token.substring(1, eq);
                String value = eq < 0 ? "" : token.substring(eq + 1);
                if ("ignore_failure".equals(key)) {
                    if (ignoreFailure) {
                        throw new ActionSyntaxException(lineNumber, raw, "Duplicate control '@ignore_failure'.");
                    }
                    ignoreFailure = true;
                    continue;
                }
                if ("if".equals(key)) {
                    if (condition != null) {
                        throw new ActionSyntaxException(lineNumber, raw, "Duplicate control '@if'.");
                    }
                    condition = value;
                    continue;
                }
                if ("chance".equals(key)) {
                    if (chance != null) {
                        throw new ActionSyntaxException(lineNumber, raw, "Duplicate control '@chance'.");
                    }
                    chance = value;
                    continue;
                }
                if ("delay".equals(key)) {
                    if (delay != null) {
                        throw new ActionSyntaxException(lineNumber, raw, "Duplicate control '@delay'.");
                    }
                    delay = value;
                    continue;
                }
                throw new ActionSyntaxException(lineNumber, raw, "Unknown control prefix: " + token);
            }
            if (actionId == null) {
                actionId = Texts.lower(token);
                continue;
            }
            int equals = token.indexOf('=');
            if (equals <= 0) {
                throw new ActionSyntaxException(lineNumber, raw, "Invalid argument token: " + token);
            }
            String key = token.substring(0, equals);
            String value = token.substring(equals + 1);
            if (arguments.containsKey(key)) {
                throw new ActionSyntaxException(lineNumber, raw, "Duplicate argument: " + key);
            }
            arguments.put(key, value);
        }
        if (actionId == null) {
            throw new ActionSyntaxException(lineNumber, raw, "Missing action id.");
        }
        return new ParsedActionLine(lineNumber, raw, actionId, Map.copyOf(arguments), new ActionLineControl(condition, chance, delay, ignoreFailure));
    }

    private List<String> tokenize(int lineNumber, String rawLine) throws ActionSyntaxException {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean escaping = false;
        for (int index = 0; index < rawLine.length(); index++) {
            char ch = rawLine.charAt(index);
            if (escaping) {
                current.append(unescape(ch));
                escaping = false;
                continue;
            }
            if (ch == '\\' && quote != 0) {
                escaping = true;
                continue;
            }
            if (quote != 0) {
                if (ch == quote) {
                    quote = 0;
                } else {
                    current.append(ch);
                }
                continue;
            }
            if (ch == '"' || ch == '\'') {
                quote = ch;
                continue;
            }
            if (Character.isWhitespace(ch)) {
                flush(tokens, current);
                continue;
            }
            current.append(ch);
        }
        if (quote != 0) {
            throw new ActionSyntaxException(lineNumber, rawLine, "Unclosed quoted value.");
        }
        if (escaping) {
            current.append('\\');
        }
        flush(tokens, current);
        return tokens;
    }

    private char unescape(char value) {
        return switch (value) {
            case 'n' ->
                '\n';
            case 't' ->
                '\t';
            case '\\' ->
                '\\';
            case '"' ->
                '"';
            case '\'' ->
                '\'';
            default ->
                value;
        };
    }

    private void flush(List<String> tokens, StringBuilder current) {
        if (current.isEmpty()) {
            return;
        }
        tokens.add(current.toString());
        current.setLength(0);
    }
}
