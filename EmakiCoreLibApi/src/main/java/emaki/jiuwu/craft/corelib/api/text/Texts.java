package emaki.jiuwu.craft.corelib.api.text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class Texts {

    private static final Pattern MINI_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private Texts() {
    }

    public static String toStringSafe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public static boolean isBlank(Object value) {
        return toStringSafe(value).trim().isEmpty();
    }

    public static boolean isNotBlank(Object value) {
        return !isBlank(value);
    }

    public static String trim(Object value) {
        return toStringSafe(value).trim();
    }

    public static String lower(Object value) {
        return toStringSafe(value).toLowerCase(Locale.ROOT);
    }

    public static String upper(Object value) {
        return toStringSafe(value).toUpperCase(Locale.ROOT);
    }

    public static boolean startsWith(Object text, Object prefix) {
        return toStringSafe(text).startsWith(toStringSafe(prefix));
    }

    public static boolean endsWith(Object text, Object suffix) {
        return toStringSafe(text).endsWith(toStringSafe(suffix));
    }

    public static boolean contains(Object text, Object substring) {
        return toStringSafe(text).contains(toStringSafe(substring));
    }

    public static String stripMiniTags(Object value) {
        String text = toStringSafe(value);
        if (text.indexOf('<') < 0 || text.indexOf('>') < 0) {
            return text;
        }
        return MINI_TAG_PATTERN.matcher(text).replaceAll("");
    }

    public static String normalizeWhitespace(String value) {
        String text = toStringSafe(value).trim();
        if (text.isEmpty()) {
            return "";
        }
        if (text.length() < 2 || !containsRepeatedWhitespace(text)) {
            return text;
        }
        return WHITESPACE_PATTERN.matcher(text).replaceAll(" ");
    }

    public static String normalizeWhitespace(Object value) {
        return normalizeWhitespace(toStringSafe(value));
    }

    public static String normalizeId(String value) {
        return toStringSafe(value).trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    public static List<String> stripMiniTags(Collection<?> values) {
        List<String> result = new ArrayList<>();
        if (values == null) {
            return result;
        }
        for (Object value : values) {
            result.add(stripMiniTags(value));
        }
        return result;
    }

    public static String formatTemplate(String template, Map<String, ?> replacements) {
        if (template == null) {
            return "";
        }
        if (replacements == null || replacements.isEmpty()) {
            return template;
        }
        int len = template.length();
        StringBuilder sb = new StringBuilder(len + 32);
        for (int i = 0; i < len; i++) {
            char ch = template.charAt(i);
            if (ch == '%') {
                int close = template.indexOf('%', i + 1);
                if (close > i + 1) {
                    String key = template.substring(i + 1, close);
                    if (replacements.containsKey(key)) {
                        sb.append(toStringSafe(replacements.get(key)));
                        i = close;
                        continue;
                    }
                    String lowerKey = lower(key);
                    if (replacements.containsKey(lowerKey)) {
                        sb.append(toStringSafe(replacements.get(lowerKey)));
                        i = close;
                        continue;
                    }
                }
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    /**
     * Expands a single template line into one or more lines.
     *
     * <p>When the whole line is exactly one placeholder (for example {@code "%materials%"}) and the
     * matching replacement value is a collection, every collection element becomes its own line. This
     * lets a fixed-length YAML lore list render a variable number of entries. Every other shape falls
     * back to {@link #formatTemplate(String, Map)} and yields exactly one line, so a mixed line such as
     * {@code "cost: %materials%"} keeps its prefix instead of losing it.
     *
     * @param template     the template line; may be {@code null}
     * @param replacements the placeholder replacements
     * @return the expanded lines, never {@code null}
     */
    public static List<String> expandTemplateLines(String template, Map<String, ?> replacements) {
        Object value = solePlaceholderValue(template, replacements);
        if (!(value instanceof Collection<?> collection)) {
            return List.of(formatTemplate(template, replacements));
        }
        List<String> result = new ArrayList<>(collection.size());
        for (Object entry : collection) {
            result.add(formatTemplate(toStringSafe(entry), replacements));
        }
        return result;
    }

    /**
     * {@return the replacement value when the trimmed template is exactly one placeholder, otherwise
     * {@code null}} Key lookup order matches {@link #formatTemplate(String, Map)}: exact key first, then
     * the lower-cased key.
     */
    private static Object solePlaceholderValue(String template, Map<String, ?> replacements) {
        if (template == null || replacements == null || replacements.isEmpty()) {
            return null;
        }
        String trimmed = template.trim();
        if (trimmed.length() < 3 || trimmed.charAt(0) != '%' || trimmed.charAt(trimmed.length() - 1) != '%') {
            return null;
        }
        String key = trimmed.substring(1, trimmed.length() - 1);
        if (key.indexOf('%') >= 0) {
            return null;
        }
        if (replacements.containsKey(key)) {
            return replacements.get(key);
        }
        String lowerKey = lower(key);
        return replacements.containsKey(lowerKey) ? replacements.get(lowerKey) : null;
    }

    public static List<String> formatTemplateList(Collection<?> template, Map<String, ?> replacements) {
        List<String> result = new ArrayList<>();
        if (template == null) {
            return result;
        }
        for (Object value : template) {
            result.add(formatTemplate(toStringSafe(value), replacements));
        }
        return result;
    }

    public static List<String> asStringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value == null) {
            return result;
        }
        if (value instanceof Collection<?> collection) {
            for (Object entry : collection) {
                result.add(toStringSafe(entry));
            }
            return result;
        }
        result.add(toStringSafe(value));
        return result;
    }

    private static boolean containsRepeatedWhitespace(String text) {
        boolean previousWhitespace = false;
        for (int index = 0; index < text.length(); index++) {
            boolean whitespace = Character.isWhitespace(text.charAt(index));
            if (whitespace && previousWhitespace) {
                return true;
            }
            previousWhitespace = whitespace;
        }
        return false;
    }
}
