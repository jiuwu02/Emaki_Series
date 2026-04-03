package emaki.jiuwu.craft.corelib.assembly.lore;

import java.util.Locale;
import java.util.regex.Pattern;

import emaki.jiuwu.craft.corelib.text.Texts;

public final class LoreTextUtil {

    private static final Pattern SECTION_STYLE_PATTERN = Pattern.compile("(?i)§x(?:§[0-9a-f]){6}|§[0-9a-fk-or]");
    private static final Pattern MINI_TAG_PATTERN = Pattern.compile("<[^>]+>");

    private LoreTextUtil() {
    }

    public static String stripColorCodes(String value) {
        String withoutSections = SECTION_STYLE_PATTERN.matcher(Texts.toStringSafe(value)).replaceAll("");
        return Texts.stripMiniTags(withoutSections);
    }

    public static String normalizeSearchText(String value, boolean ignoreCase) {
        String normalized = stripColorCodes(value);
        return ignoreCase ? normalized.toLowerCase(Locale.ROOT) : normalized;
    }

    public static boolean hasExplicitStyle(String value) {
        String text = Texts.toStringSafe(value);
        return SECTION_STYLE_PATTERN.matcher(text).find() || MINI_TAG_PATTERN.matcher(text).find();
    }

    public static String leadingStyle(String value) {
        String text = Texts.toStringSafe(value);
        if (text.isEmpty()) {
            return "";
        }
        StringBuilder style = new StringBuilder();
        int index = 0;
        while (index < text.length()) {
            if (text.charAt(index) == '<') {
                int close = text.indexOf('>', index);
                if (close > index) {
                    style.append(text, index, close + 1);
                    index = close + 1;
                    continue;
                }
                break;
            }
            if (text.charAt(index) == '§') {
                int sectionLength = readSectionStyleLength(text, index);
                if (sectionLength > 0) {
                    style.append(text, index, index + sectionLength);
                    index += sectionLength;
                    continue;
                }
            }
            break;
        }
        return style.toString();
    }

    public static String inheritStyle(String line, String referenceLine, boolean inheritStyle) {
        if (!inheritStyle || Texts.isBlank(line) || hasExplicitStyle(line)) {
            return Texts.toStringSafe(line);
        }
        String style = leadingStyle(referenceLine);
        return style.isEmpty() ? Texts.toStringSafe(line) : style + line;
    }

    private static int readSectionStyleLength(String text, int start) {
        if (start + 1 >= text.length() || text.charAt(start) != '§') {
            return 0;
        }
        char marker = text.charAt(start + 1);
        if (marker == 'x' || marker == 'X') {
            if (start + 13 >= text.length()) {
                return 0;
            }
            for (int index = start + 2; index <= start + 12; index += 2) {
                if (text.charAt(index) != '§') {
                    return 0;
                }
            }
            return 14;
        }
        return 2;
    }
}
