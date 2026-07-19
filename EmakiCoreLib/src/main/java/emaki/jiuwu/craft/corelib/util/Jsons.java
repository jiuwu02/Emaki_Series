package emaki.jiuwu.craft.corelib.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

public final class Jsons {

    private Jsons() {
    }

    public static String stringify(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String string) {
            return quote(string);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder builder = new StringBuilder("{");
            Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<?, ?> entry = iterator.next();
                builder.append(quote(String.valueOf(entry.getKey()))).append(':').append(stringify(entry.getValue()));
                if (iterator.hasNext()) {
                    builder.append(',');
                }
            }
            return builder.append('}').toString();
        }
        if (value instanceof Collection<?> collection) {
            StringBuilder builder = new StringBuilder("[");
            Iterator<?> iterator = collection.iterator();
            while (iterator.hasNext()) {
                builder.append(stringify(iterator.next()));
                if (iterator.hasNext()) {
                    builder.append(',');
                }
            }
            return builder.append(']').toString();
        }
        if (value.getClass().isArray()) {
            Object[] array = (Object[]) value;
            StringBuilder builder = new StringBuilder("[");
            for (int i = 0; i < array.length; i++) {
                builder.append(stringify(array[i]));
                if (i + 1 < array.length) {
                    builder.append(',');
                }
            }
            return builder.append(']').toString();
        }
        return quote(String.valueOf(value));
    }

    public static String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder(value.length() + 16).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (c < 0x20) {
                        builder.append(String.format("\\u%04x", (int) c));
                    } else {
                        builder.append(c);
                    }
                }
            }
        }
        return builder.append('"').toString();
    }

    public static String extractString(String json, String key) {
        Object value = extractValue(json, key);
        return value == null ? "" : String.valueOf(value);
    }

    public static Object extractValue(String json, String key) {
        if (json == null || key == null) {
            return null;
        }
        Object parsed = parse(json);
        if (parsed instanceof Map<?, ?> map) {
            return map.get(key);
        }
        return null;
    }

    public static Object parse(String json) {
        if (json == null) {
            return null;
        }
        return new Parser(json).readValue();
    }

    private static final class Parser {
        private final String json;
        private int index;

        private Parser(String json) {
            this.json = json;
        }

        private Object readValue() {
            skipWhitespace();
            if (index >= json.length()) {
                return null;
            }
            char first = json.charAt(index);
            return switch (first) {
                case '"' -> readString();
                case '{' -> readObject();
                case '[' -> readArray();
                case 't' -> readLiteral("true", Boolean.TRUE);
                case 'f' -> readLiteral("false", Boolean.FALSE);
                case 'n' -> readLiteral("null", null);
                default -> readNumberOrRaw();
            };
        }

        private Map<String, Object> readObject() {
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            index++; // {
            while (index < json.length()) {
                skipWhitespace();
                if (consume('}')) {
                    return result;
                }
                if (index >= json.length() || json.charAt(index) != '"') {
                    return result;
                }
                String key = readString();
                skipWhitespace();
                consume(':');
                Object value = readValue();
                result.put(key, value);
                skipWhitespace();
                if (consume('}')) {
                    return result;
                }
                consume(',');
            }
            return result;
        }

        private java.util.List<Object> readArray() {
            java.util.List<Object> result = new java.util.ArrayList<>();
            index++; // [
            while (index < json.length()) {
                skipWhitespace();
                if (consume(']')) {
                    return result;
                }
                result.add(readValue());
                skipWhitespace();
                if (consume(']')) {
                    return result;
                }
                consume(',');
            }
            return result;
        }

        private Object readLiteral(String literal, Object value) {
            if (json.regionMatches(true, index, literal, 0, literal.length())) {
                index += literal.length();
                return value;
            }
            return readNumberOrRaw();
        }

        private Object readNumberOrRaw() {
            int start = index;
            while (index < json.length() && ",]}\r\n\t ".indexOf(json.charAt(index)) < 0) {
                index++;
            }
            String raw = json.substring(start, index).trim();
            if (raw.isEmpty()) {
                return "";
            }
            try {
                if (raw.contains(".") || raw.contains("e") || raw.contains("E")) {
                    return Double.parseDouble(raw);
                }
                return Long.parseLong(raw);
            } catch (NumberFormatException ignored) {
                return raw;
            }
        }

        private String readString() {
            StringBuilder builder = new StringBuilder();
            index++; // opening quote
            while (index < json.length()) {
                char c = json.charAt(index++);
                if (c == '\\') {
                    if (index >= json.length()) {
                        return builder.toString();
                    }
                    char next = json.charAt(index++);
                    switch (next) {
                        case 'n' -> builder.append('\n');
                        case 'r' -> builder.append('\r');
                        case 't' -> builder.append('\t');
                        case 'b' -> builder.append('\b');
                        case 'f' -> builder.append('\f');
                        case '"' -> builder.append('"');
                        case '\\' -> builder.append('\\');
                        case '/' -> builder.append('/');
                        case 'u' -> builder.append(readUnicodeEscape());
                        default -> builder.append(next);
                    }
                } else if (c == '"') {
                    return builder.toString();
                } else {
                    builder.append(c);
                }
            }
            return builder.toString();
        }

        private char readUnicodeEscape() {
            if (index + 4 > json.length()) {
                index = json.length();
                return '?';
            }
            try {
                char value = (char) Integer.parseInt(json.substring(index, index + 4), 16);
                index += 4;
                return value;
            } catch (NumberFormatException exception) {
                index = Math.min(json.length(), index + 4);
                return '?';
            }
        }

        private boolean consume(char expected) {
            skipWhitespace();
            if (index < json.length() && json.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (index < json.length() && Character.isWhitespace(json.charAt(index))) {
                index++;
            }
        }
    }
}
