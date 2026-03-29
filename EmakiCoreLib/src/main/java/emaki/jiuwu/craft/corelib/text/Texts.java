package emaki.jiuwu.craft.corelib.text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
        return toStringSafe(value).toLowerCase();
    }

    public static String upper(Object value) {
        return toStringSafe(value).toUpperCase();
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
        return MINI_TAG_PATTERN.matcher(toStringSafe(value)).replaceAll("");
    }

    public static String normalizeWhitespace(String value) {
        String text = toStringSafe(value).trim();
        if (text.isEmpty()) {
            return "";
        }
        return WHITESPACE_PATTERN.matcher(text).replaceAll(" ");
    }

    public static String normalizeWhitespace(Object value) {
        return normalizeWhitespace(toStringSafe(value));
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
        String result = template;
        for (Map.Entry<String, ?> entry : replacements.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", toStringSafe(entry.getValue()));
        }
        return result;
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
}
