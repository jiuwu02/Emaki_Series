package emaki.jiuwu.craft.corelib.web;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

public final class WebJson {

    private WebJson() {
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
        String marker = quote(key) + ":";
        int index = json.indexOf(marker);
        if (index < 0) {
            return null;
        }
        int valueStart = skipWhitespace(json, index + marker.length());
        if (valueStart >= json.length()) {
            return null;
        }
        char first = json.charAt(valueStart);
        if (first == '"') {
            return readString(json, valueStart);
        }
        if (first == '[') {
            return readStringArray(json, valueStart);
        }
        int end = valueStart;
        while (end < json.length() && ",}".indexOf(json.charAt(end)) < 0) {
            end++;
        }
        String raw = json.substring(valueStart, end).trim();
        if ("true".equalsIgnoreCase(raw)) {
            return true;
        }
        if ("false".equalsIgnoreCase(raw)) {
            return false;
        }
        try {
            return raw.contains(".") ? Double.parseDouble(raw) : Integer.parseInt(raw);
        } catch (Exception ignored) {
            return raw;
        }
    }

    private static java.util.List<String> readStringArray(String json, int start) {
        java.util.List<String> result = new java.util.ArrayList<>();
        int index = start + 1;
        while (index < json.length()) {
            index = skipWhitespace(json, index);
            if (index >= json.length() || json.charAt(index) == ']') {
                return result;
            }
            if (json.charAt(index) == '"') {
                result.add(readString(json, index));
                index = nextStringEnd(json, index) + 1;
            } else {
                int end = index;
                while (end < json.length() && ",]".indexOf(json.charAt(end)) < 0) {
                    end++;
                }
                result.add(json.substring(index, end).trim());
                index = end;
            }
            index = skipWhitespace(json, index);
            if (index < json.length() && json.charAt(index) == ',') {
                index++;
            }
        }
        return result;
    }

    private static String readString(String json, int start) {
        StringBuilder builder = new StringBuilder();
        int i = start + 1;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\') {
                if (i + 1 >= json.length()) {
                    return "";
                }
                char next = json.charAt(i + 1);
                switch (next) {
                    case 'n' -> { builder.append('\n'); i += 2; }
                    case 'r' -> { builder.append('\r'); i += 2; }
                    case 't' -> { builder.append('\t'); i += 2; }
                    case 'b' -> { builder.append('\b'); i += 2; }
                    case 'f' -> { builder.append('\f'); i += 2; }
                    case '"' -> { builder.append('"'); i += 2; }
                    case '\\' -> { builder.append('\\'); i += 2; }
                    case '/' -> { builder.append('/'); i += 2; }
                    case 'u' -> {
                        if (i + 6 > json.length()) {
                            return "";
                        }
                        try {
                            int codePoint = Integer.parseInt(json.substring(i + 2, i + 6), 16);
                            builder.append((char) codePoint);
                        } catch (NumberFormatException ignored) {
                            builder.append(next);
                        }
                        i += 6;
                    }
                    default -> { builder.append(next); i += 2; }
                }
            } else if (c == '"') {
                return builder.toString();
            } else {
                builder.append(c);
                i++;
            }
        }
        return "";
    }

    private static int nextStringEnd(String json, int start) {
        int i = start + 1;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\') {
                if (i + 1 < json.length() && json.charAt(i + 1) == 'u') {
                    i += 6; // skip unicode escape
                } else {
                    i += 2; // skip escape pair
                }
            } else if (c == '"') {
                return i;
            } else {
                i++;
            }
        }
        return json.length() - 1;
    }

    private static int skipWhitespace(String json, int index) {
        while (index < json.length() && Character.isWhitespace(json.charAt(index))) {
            index++;
        }
        return index;
    }
}
