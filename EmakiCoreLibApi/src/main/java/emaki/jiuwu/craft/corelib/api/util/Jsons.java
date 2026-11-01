package emaki.jiuwu.craft.corelib.api.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small dependency-free JSON writer and lenient reader for diagnostic and persistence payloads.
 *
 * <p>{@link #stringify(Object)} builds JSON from plain data (maps, collections, arrays, strings,
 * numbers and booleans), while {@link #parse(String)} tolerates malformed input: it returns what
 * it could read (partial objects and arrays, raw text in place of a number) instead of throwing.
 * Both sides preserve map iteration order and keep no shared state, so they are safe to call
 * from any thread.
 */
public final class Jsons {

    private Jsons() {
    }

    /**
     * Writes plain data as JSON text.
     *
     * <p>{@code null} becomes the literal {@code null}, strings are quoted, numbers and booleans
     * are written as-is, maps become objects keyed by {@code String.valueOf}, and collections
     * and object arrays become arrays in iteration order. Any other value is quoted through its
     * string form. Primitive arrays are not supported and fail with
     * {@link ClassCastException}.
     *
     * @param value value to write; may be {@code null}
     * @return the JSON text, never {@code null}
     */
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

    /**
     * Quotes and escapes a JSON string.
     *
     * <p>The quote, backslash, backspace, form feed, newline, carriage return and tab characters
     * use their two-character escapes; every other control character below {@code 0x20} is
     * written as a four-digit hexadecimal escape.
     *
     * @param value text to quote; {@code null} yields the literal {@code null}
     * @return the quoted text
     */
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

    /**
     * Reads one top-level value out of a JSON object as text.
     *
     * @param json JSON text; {@code null} yields an empty string
     * @param key top-level key to read; {@code null} yields an empty string
     * @return the value's string form, or an empty string when the key is missing, the value is
     *         JSON {@code null}, the root is not an object, or the text cannot be read
     */
    public static String extractString(String json, String key) {
        Object value = extractValue(json, key);
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * Reads one top-level value out of a JSON object.
     *
     * <p>A missing key, an explicit JSON {@code null} and an unreadable document are
     * indistinguishable: all three answer {@code null}.
     *
     * @param json JSON text; {@code null} yields {@code null}
     * @param key top-level key to read; {@code null} yields {@code null}
     * @return the parsed value ({@code Map}, {@code List}, {@code String}, {@code Long},
     *         {@code Double}, {@code Boolean} or {@code null}) when the root is an object
     *         holding the key, otherwise {@code null}
     */
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

    /**
     * Parses JSON text leniently.
     *
     * <p>Objects become {@link LinkedHashMap} and arrays become {@link ArrayList}, so key and
     * element order survive a round trip. Numbers become {@code Long} or {@code Double}
     * depending on whether they carry a decimal point or exponent, while text that is not a
     * valid value comes back as a raw {@code String} instead of throwing; a truncated document
     * yields whatever was read so far.
     *
     * @param json JSON text; {@code null} or blank input yields {@code null}
     * @return the parsed value, or {@code null} for null or blank input
     */
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
            Map<String, Object> result = new LinkedHashMap<>();
            index++;
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

        private List<Object> readArray() {
            List<Object> result = new ArrayList<>();
            index++;
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
            index++;
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
