package emaki.jiuwu.craft.corelib.assembly.lore;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import emaki.jiuwu.craft.corelib.text.Texts;

public final class IndexedLineInsertActionParser {

    private static final Pattern ACTION_PATTERN = Pattern.compile(
            "^(top_line_insert|bottom_line_insert)\\[(\\d+)]$"
    );

    private IndexedLineInsertActionParser() {
    }

    public static ParsedIndexedLineInsertAction parse(String actionName) {
        String normalized = Texts.trim(Texts.lower(actionName));
        Matcher matcher = ACTION_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid indexed lore insert action name: " + actionName);
        }
        LineDirection direction = switch (matcher.group(1)) {
            case "top_line_insert" -> LineDirection.TOP;
            case "bottom_line_insert" -> LineDirection.BOTTOM;
            default -> throw new IllegalArgumentException("Unsupported indexed lore insert action: " + actionName);
        };
        return new ParsedIndexedLineInsertAction(direction, Integer.parseInt(matcher.group(2)));
    }

    public static boolean isIndexedLineInsertAction(String actionName) {
        return ACTION_PATTERN.matcher(Texts.trim(Texts.lower(actionName))).matches();
    }

    public enum LineDirection {
        TOP,
        BOTTOM
    }

    public record ParsedIndexedLineInsertAction(LineDirection direction, int lineIndex) {

    }
}
