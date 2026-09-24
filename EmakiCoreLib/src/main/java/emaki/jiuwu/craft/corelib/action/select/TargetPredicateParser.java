package emaki.jiuwu.craft.corelib.action.select;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.condition.ConditionGroup;
import emaki.jiuwu.craft.corelib.condition.ConditionNode;

public final class TargetPredicateParser {

    public sealed interface Result {

        record Parsed(@NotNull ConditionGroup group) implements Result {
        }

        record Invalid(@NotNull String reasonKey, @NotNull Map<String, Object> args) implements Result {
        }
    }

    private static final List<String> OPERATORS = List.of("<=", ">=", "==", "!=", "=", "<", ">");

    private static final List<String> NUMBER_KEYS =
            List.of("health", "health_percent", "distance", "level", "food");

    private static final List<String> TEXT_KEYS =
            List.of("entity_type", "world", "gamemode", "permission", "scoreboard_tag");

    private static final List<String> FLAG_KEYS = List.of("player", "dead");

    private static final String STATE_PREFIX = "state.";
    private static final String EFFECT_PREFIX = "potion_effect.";
    private static final String LEVEL_SUFFIX = "_level";

    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9_]+");

    private TargetPredicateParser() {
    }

    public static Result parse(@NotNull String raw) {
        String text = Texts.trim(raw);
        if (text.isEmpty()) {
            return new Result.Invalid("action.gate.filter.condition_required", Map.of());
        }
        Cursor cursor = new Cursor(text);
        ConditionGroup group = parseOr(cursor);
        if (group == null) {
            return invalid(cursor);
        }
        cursor.skipSpaces();
        if (!cursor.end()) {
            return invalid(cursor);
        }
        return new Result.Parsed(group);
    }

    public static List<String> knownKeys() {
        List<String> keys = new ArrayList<>(NUMBER_KEYS);
        keys.addAll(TEXT_KEYS);
        keys.addAll(FLAG_KEYS);
        keys.add(STATE_PREFIX + "<状态名>");
        keys.add(EFFECT_PREFIX + "<效果名>>=N");
        keys.add("<生物系统 id>=<mob id>");
        keys.add("<生物系统 id>_level<op>N");
        return List.copyOf(keys);
    }

    private static Result.Invalid invalid(Cursor cursor) {
        return new Result.Invalid("action.gate.filter.invalid_condition",
                Map.of("condition", cursor.text(),
                        "position", cursor.position(),
                        "token", cursor.failedToken(),
                        "keys", String.join(", ", knownKeys())));
    }

    private static ConditionGroup parseOr(Cursor cursor) {
        List<ConditionNode> branches = new ArrayList<>();
        while (true) {
            ConditionGroup branch = parseAnd(cursor);
            if (branch == null) {
                return null;
            }
            branches.add(ConditionNode.group(branch));
            cursor.skipSpaces();
            if (!cursor.match("||")) {
                break;
            }
        }
        return new ConditionGroup("any_of", 0, branches);
    }

    private static ConditionGroup parseAnd(Cursor cursor) {
        List<ConditionNode> entries = new ArrayList<>();
        while (true) {
            ConditionNode entry = parseUnary(cursor);
            if (entry == null) {
                return null;
            }
            entries.add(entry);
            cursor.skipSpaces();
            if (!cursor.hasMoreItem()) {
                break;
            }
        }
        return entries.isEmpty() ? null : new ConditionGroup("all_of", 0, entries);
    }

    private static ConditionNode parseUnary(Cursor cursor) {
        cursor.skipSpaces();
        if (cursor.match("!")) {
            ConditionNode inner = parseUnary(cursor);
            if (inner == null) {
                return null;
            }
            return ConditionNode.group(new ConditionGroup("none_of", 0, List.of(inner)));
        }
        if (cursor.match("(")) {
            ConditionGroup group = parseOr(cursor);
            if (group == null) {
                return null;
            }
            cursor.skipSpaces();
            if (!cursor.match(")")) {
                return null;
            }
            return ConditionNode.group(group);
        }
        String token = cursor.nextToken();
        if (token.isEmpty()) {
            cursor.fail(token);
            return null;
        }
        ConditionNode node = node(token);
        if (node == null) {
            cursor.fail(token);
        }
        return node;
    }

    private static ConditionNode node(String token) {
        String key = Texts.lower(token);
        String operator = null;
        String value = "";
        for (String candidate : OPERATORS) {
            int at = token.indexOf(candidate);
            if (at <= 0) {
                continue;
            }
            key = Texts.lower(token.substring(0, at));
            operator = "=".equals(candidate) ? "==" : candidate;
            value = Texts.trim(token.substring(at + candidate.length()));
            break;
        }
        if (key.startsWith(STATE_PREFIX)) {
            return stateNode(key.substring(STATE_PREFIX.length()), operator, value);
        }
        if (key.startsWith(EFFECT_PREFIX)) {
            return effectNode(key.substring(EFFECT_PREFIX.length()), operator, value);
        }
        if (NUMBER_KEYS.contains(key)) {
            return numberNode(key, operator, value);
        }
        if (FLAG_KEYS.contains(key)) {
            return flagNode(key, operator, value);
        }
        if (TEXT_KEYS.contains(key)) {
            return textNode(key, operator, value);
        }
        return identityNode(key, operator, value);
    }

    private static ConditionNode stateNode(String state, String operator, String value) {
        if (Texts.isBlank(state)) {
            return null;
        }
        boolean expected = true;
        if (operator != null) {
            if (!"==".equals(operator) && !"!=".equals(operator)) {
                return null;
            }
            Boolean parsed = Texts.isBlank(value) ? Boolean.TRUE : parseBoolean(value);
            if (parsed == null) {
                return null;
            }
            expected = "!=".equals(operator) ? !parsed : parsed;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("key", Texts.lower(state));
        data.put("value", expected);
        return node("state", data);
    }

    private static ConditionNode effectNode(String effect, String operator, String value) {
        if (Texts.isBlank(effect)) {
            return null;
        }
        if (operator != null && !">=".equals(operator)) {
            return null;
        }
        int minimum = 0;
        if (operator != null) {
            Integer parsed = parseInt(value);
            if (parsed == null || parsed < 0) {
                return null;
            }
            minimum = parsed;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("effect", effect);
        data.put("min_amplifier", minimum);
        return node("potion_effect", data);
    }

    private static ConditionNode numberNode(String key, String operator, String value) {
        if (operator == null) {
            return null;
        }
        Double parsed = parseDouble(value);
        if (parsed == null) {
            return null;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("op", operator);
        data.put("value", parsed);
        return node(key, data);
    }

    private static ConditionNode flagNode(String key, String operator, String value) {
        if (operator != null && !"==".equals(operator) && !"!=".equals(operator)) {
            return null;
        }
        Boolean expected = Texts.isBlank(value) ? Boolean.TRUE : parseBoolean(value);
        if (expected == null) {
            return null;
        }
        if ("!=".equals(operator)) {
            expected = !expected;
        }
        Map<String, Object> data = Map.of("value", expected);
        return node(key, data);
    }

    private static ConditionNode textNode(String key, String operator, String value) {
        List<String> values = splitValues(value);
        if (values.isEmpty()) {
            return null;
        }
        if (operator != null && !"==".equals(operator) && !"!=".equals(operator)) {
            return null;
        }
        ConditionNode candidate = node(key, Map.of("value", values));
        return "!=".equals(operator) ? negate(candidate) : candidate;
    }

    private static ConditionNode identityNode(String key, String operator, String value) {
        if (!IDENTIFIER.matcher(key).matches()) {
            return null;
        }
        if (operator != null && Texts.isBlank(value)) {
            return null;
        }
        if (key.endsWith(LEVEL_SUFFIX) && key.length() > LEVEL_SUFFIX.length()) {
            String systemId = key.substring(0, key.length() - LEVEL_SUFFIX.length());
            Double parsed = parseDouble(value);
            if (operator == null || parsed == null) {
                return null;
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("op", operator);
            data.put("level", parsed);
            return node(systemId, data);
        }
        if (operator != null && !"==".equals(operator) && !"!=".equals(operator)) {
            return null;
        }
        ConditionNode candidate = node(key, Map.of("id", splitValues(value)));
        return "!=".equals(operator) ? negate(candidate) : candidate;
    }

    private static ConditionNode negate(ConditionNode candidate) {
        return ConditionNode.group(new ConditionGroup("none_of", 0, List.of(candidate)));
    }

    private static ConditionNode node(String type, Map<String, Object> data) {
        return new ConditionNode(type, "", null, data);
    }

    private static List<String> splitValues(String raw) {
        List<String> values = new ArrayList<>();
        for (String part : Texts.toStringSafe(raw).split(",")) {
            String value = Texts.trim(part);
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private static Boolean parseBoolean(String raw) {
        String text = Texts.lower(raw);
        if ("true".equals(text) || "yes".equals(text)) {
            return Boolean.TRUE;
        }
        if ("false".equals(text) || "no".equals(text)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static Integer parseInt(String raw) {
        try {
            return Integer.valueOf(Texts.trim(raw));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Double parseDouble(String raw) {
        try {
            return Double.valueOf(Texts.trim(raw));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static final class Cursor {

        private final String text;
        private int index;
        private String failedToken = "";

        private Cursor(String text) {
            this.text = text;
        }

        private String text() {
            return text;
        }

        private int position() {
            return index;
        }

        private String failedToken() {
            return failedToken;
        }

        private void fail(String token) {
            if (failedToken.isEmpty()) {
                failedToken = token;
            }
        }

        private boolean end() {
            return index >= text.length();
        }

        private void skipSpaces() {
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
        }

        private boolean match(String token) {
            skipSpaces();
            if (text.startsWith(token, index)) {
                index += token.length();
                return true;
            }
            return false;
        }

        private boolean hasMoreItem() {
            if (end()) {
                return false;
            }
            char next = text.charAt(index);
            return next != ')' && !text.startsWith("||", index);
        }

        private String nextToken() {
            skipSpaces();
            int start = index;
            while (index < text.length()) {
                char current = text.charAt(index);
                if (Character.isWhitespace(current) || current == '(' || current == ')') {
                    break;
                }
                if (text.startsWith("||", index)) {
                    break;
                }
                index++;
            }
            return text.substring(start, index);
        }
    }
}