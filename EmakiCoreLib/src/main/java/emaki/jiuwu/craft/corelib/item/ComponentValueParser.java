package emaki.jiuwu.craft.corelib.item;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class ComponentValueParser {

    public static final Object INVALID = new Object();

    private static final String NBT_ARRAY_PREFIXES = "BbIiLl";
    private static final String NUMERIC_SUFFIXES = "bBsSlLfFdD";

    private final String text;
    private int index;

    private ComponentValueParser(String text) {
        this.text = text == null ? "" : text;
    }

    public static Object parse(String text) {
        ComponentValueParser parser = new ComponentValueParser(text);
        Object value = parser.readValue();
        parser.skipWhitespace();
        return value == INVALID || parser.index != parser.text.length() ? INVALID : value;
    }

    public static Object parseLenient(String raw) {
        String value = Texts.toStringSafe(raw).trim();
        if (value.isEmpty()) {
            return true;
        }
        String unquoted = unquote(value);
        String candidate = unquoted == null ? value : unquoted;
        Object parsed = parse(candidate);
        if (parsed != INVALID) {
            return parsed;
        }
        if (unquoted != null) {
            return unquoted;
        }
        Object scalar = parseScalar(value);
        return scalar == INVALID ? value : scalar;
    }

    public static Object parseScalar(String value) {
        if (value == null || value.isEmpty()) {
            return INVALID;
        }
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        if ("null".equalsIgnoreCase(value)) {
            return null;
        }
        String numeric = stripNumericSuffix(value);
        try {
            if (!numeric.isEmpty()
                    && numeric.indexOf('.') < 0
                    && numeric.indexOf('e') < 0
                    && numeric.indexOf('E') < 0) {
                return Long.parseLong(numeric);
            }
            double parsed = Double.parseDouble(numeric);
            return Double.isFinite(parsed) ? parsed : INVALID;
        } catch (NumberFormatException _) {
            return INVALID;
        }
    }

    public static String stripNumericSuffix(String value) {
        if (value == null || value.length() < 2) {
            return value == null ? "" : value;
        }
        char last = value.charAt(value.length() - 1);
        if (NUMERIC_SUFFIXES.indexOf(last) < 0) {
            return value;
        }
        String head = value.substring(0, value.length() - 1);
        if (head.isEmpty() || "-".equals(head) || "+".equals(head)) {
            return value;
        }
        for (int position = 0; position < head.length(); position++) {
            char current = head.charAt(position);
            boolean signAtStart = position == 0 && (current == '-' || current == '+');
            if (!signAtStart && !Character.isDigit(current) && current != '.' && current != 'e' && current != 'E') {
                return value;
            }
        }
        return head;
    }

    public static String unquote(String value) {
        if (value == null || value.length() < 2) {
            return null;
        }
        char quote = value.charAt(0);
        if ((quote != '"' && quote != '\'') || value.charAt(value.length() - 1) != quote) {
            return null;
        }
        StringBuilder builder = new StringBuilder(value.length() - 2);
        for (int position = 1; position < value.length() - 1; position++) {
            char current = value.charAt(position);
            if (current != '\\' || position + 1 >= value.length() - 1) {
                builder.append(current);
                continue;
            }
            char next = value.charAt(++position);
            switch (next) {
                case 'n' -> builder.append('\n');
                case 'r' -> builder.append('\r');
                case 't' -> builder.append('\t');
                case 'b' -> builder.append('\b');
                case 'f' -> builder.append('\f');
                case 'u' -> {
                    if (position + 4 < value.length() - 1) {
                        try {
                            builder.append((char) Integer.parseInt(value.substring(position + 1, position + 5), 16));
                            position += 4;
                        } catch (NumberFormatException _) {
                            builder.append('u');
                        }
                    } else {
                        builder.append('u');
                    }
                }
                default -> builder.append(next);
            }
        }
        return builder.toString();
    }

    private Object readValue() {
        skipWhitespace();
        if (index >= text.length()) {
            return INVALID;
        }
        char current = text.charAt(index);
        return switch (current) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"', '\'' -> readString();
            case 't', 'f' -> readBooleanOrBare();
            case 'n' -> readNullOrBare();
            default -> readNumberOrBare();
        };
    }

    private Object readObject() {
        Map<String, Object> result = new LinkedHashMap<>();
        index++;
        skipWhitespace();
        if (consume('}')) {
            return result;
        }
        while (index < text.length()) {
            Object key = readObjectKey();
            if (!(key instanceof String stringKey)) {
                return INVALID;
            }
            skipWhitespace();
            if (!consume(':')) {
                return INVALID;
            }
            Object value = readValue();
            if (value == INVALID) {
                return INVALID;
            }
            result.put(stringKey, value);
            skipWhitespace();
            if (consume('}')) {
                return result;
            }
            if (!consume(',')) {
                return INVALID;
            }
        }
        return INVALID;
    }

    private Object readArray() {
        List<Object> result = new ArrayList<>();
        index++;
        skipTypedArrayPrefix();
        skipWhitespace();
        if (consume(']')) {
            return result;
        }
        while (index < text.length()) {
            Object value = readValue();
            if (value == INVALID) {
                return INVALID;
            }
            result.add(value);
            skipWhitespace();
            if (consume(']')) {
                return result;
            }
            if (!consume(',')) {
                return INVALID;
            }
        }
        return INVALID;
    }

    private void skipTypedArrayPrefix() {
        if (index + 1 < text.length()
                && NBT_ARRAY_PREFIXES.indexOf(text.charAt(index)) >= 0
                && text.charAt(index + 1) == ';') {
            index += 2;
        }
    }

    private Object readString() {
        if (index >= text.length()) {
            return INVALID;
        }
        char quote = text.charAt(index);
        if (quote != '"' && quote != '\'') {
            return INVALID;
        }
        index++;
        StringBuilder builder = new StringBuilder();
        while (index < text.length()) {
            char current = text.charAt(index++);
            if (current == quote) {
                return builder.toString();
            }
            if (current != '\\') {
                builder.append(current);
                continue;
            }
            if (index >= text.length()) {
                return INVALID;
            }
            char escaped = text.charAt(index++);
            switch (escaped) {
                case '"' -> builder.append('"');
                case '\'' -> builder.append('\'');
                case '\\' -> builder.append('\\');
                case '/' -> builder.append('/');
                case 'b' -> builder.append('\b');
                case 'f' -> builder.append('\f');
                case 'n' -> builder.append('\n');
                case 'r' -> builder.append('\r');
                case 't' -> builder.append('\t');
                case 'u' -> {
                    if (index + 4 > text.length()) {
                        return INVALID;
                    }
                    try {
                        builder.append((char) Integer.parseInt(text.substring(index, index + 4), 16));
                    } catch (NumberFormatException _) {
                        return INVALID;
                    }
                    index += 4;
                }
                default -> builder.append(escaped);
            }
        }
        return INVALID;
    }

    private Object readObjectKey() {
        skipWhitespace();
        if (index >= text.length()) {
            return INVALID;
        }
        char current = text.charAt(index);
        if (current == '"' || current == '\'') {
            return readString();
        }
        int start = index;
        while (index < text.length()) {
            current = text.charAt(index);
            if (current == ':' && !isResourceIdSeparator()) {
                break;
            }
            if (Character.isWhitespace(current) || current == '=' || current == ',' || current == '}') {
                break;
            }
            index++;
        }
        String key = text.substring(start, index).trim();
        return key.isEmpty() ? INVALID : key;
    }

    private boolean isResourceIdSeparator() {
        int probe = index + 1;
        while (probe < text.length()) {
            char current = text.charAt(probe);
            if (Character.isLetterOrDigit(current) || current == '_' || current == '-' || current == '/' || current == '.') {
                probe++;
                continue;
            }
            return current == ':';
        }
        return false;
    }

    private Object readBooleanOrBare() {
        if (text.startsWith("true", index) && boundary(index + 4)) {
            index += 4;
            return true;
        }
        if (text.startsWith("false", index) && boundary(index + 5)) {
            index += 5;
            return false;
        }
        return readBareValue();
    }

    private Object readNullOrBare() {
        if (text.startsWith("null", index) && boundary(index + 4)) {
            index += 4;
            return null;
        }
        return readBareValue();
    }

    private Object readNumberOrBare() {
        int start = index;
        Object number = readNumber();
        if (number != INVALID && boundary(index)) {
            return number;
        }
        index = start;
        return readBareValue();
    }

    private Object readNumber() {
        int start = index;
        if (index < text.length() && (text.charAt(index) == '-' || text.charAt(index) == '+')) {
            index++;
        }
        while (index < text.length() && Character.isDigit(text.charAt(index))) {
            index++;
        }
        boolean floating = false;
        if (index < text.length() && text.charAt(index) == '.') {
            floating = true;
            index++;
            while (index < text.length() && Character.isDigit(text.charAt(index))) {
                index++;
            }
        }
        if (index < text.length() && (text.charAt(index) == 'e' || text.charAt(index) == 'E')) {
            floating = true;
            index++;
            if (index < text.length() && (text.charAt(index) == '+' || text.charAt(index) == '-')) {
                index++;
            }
            while (index < text.length() && Character.isDigit(text.charAt(index))) {
                index++;
            }
        }
        int digitsEnd = index;
        if (start == digitsEnd || (start + 1 == digitsEnd && !Character.isDigit(text.charAt(start)))) {
            return INVALID;
        }
        boolean suffixed = false;
        if (index < text.length() && NUMERIC_SUFFIXES.indexOf(text.charAt(index)) >= 0) {
            index++;
            suffixed = true;
        }
        try {
            String raw = text.substring(start, digitsEnd);
            if (floating || (suffixed && isFloatingSuffix(text.charAt(digitsEnd)))) {
                double parsed = Double.parseDouble(raw);
                return Double.isFinite(parsed) ? parsed : INVALID;
            }
            return Long.parseLong(raw.startsWith("+") ? raw.substring(1) : raw);
        } catch (NumberFormatException _) {
            return INVALID;
        }
    }

    private static boolean isFloatingSuffix(char suffix) {
        return suffix == 'f' || suffix == 'F' || suffix == 'd' || suffix == 'D';
    }

    private Object readBareValue() {
        int start = index;
        int depth = 0;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (depth == 0 && (current == ',' || current == '}' || current == ']')) {
                break;
            }
            if (current == '[' || current == '{' || current == '(') {
                depth++;
            } else if (current == ']' || current == '}' || current == ')') {
                depth = Math.max(0, depth - 1);
            }
            index++;
        }
        String value = text.substring(start, index).trim();
        if (value.isEmpty()) {
            return INVALID;
        }
        Object scalar = parseScalar(value);
        return scalar == INVALID ? value : scalar;
    }

    private boolean boundary(int position) {
        return position >= text.length()
                || Character.isWhitespace(text.charAt(position))
                || text.charAt(position) == ','
                || text.charAt(position) == '}'
                || text.charAt(position) == ']';
    }

    private void skipWhitespace() {
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
    }

    private boolean consume(char expected) {
        if (index < text.length() && text.charAt(index) == expected) {
            index++;
            return true;
        }
        return false;
    }
}
