package emaki.jiuwu.craft.corelib.assembly.lore;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import emaki.jiuwu.craft.corelib.text.Texts;

public final class ActionNameParser {

    private static final Pattern ACTION_PATTERN = Pattern.compile(
            "^(search|exact_search|regex_search)_insert_(below|above)(?:\\[(\\d+)])?$"
    );

    private ActionNameParser() {
    }

    public static ParsedActionName parse(String actionName) {
        String normalized = Texts.trim(Texts.lower(actionName));
        Matcher matcher = ACTION_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            throw new SearchInsertValidationException(
                    SearchInsertValidationException.Reason.INVALID_ACTION_NAME,
                    "Invalid lore search insert action name: " + actionName
            );
        }
        int matchIndex = matcher.group(3) == null ? 1 : Integer.parseInt(matcher.group(3));
        return new ParsedActionName(resolveMode(matcher.group(1)), resolvePosition(matcher.group(2)), matchIndex);
    }

    public static boolean isSearchInsertAction(String actionName) {
        return ACTION_PATTERN.matcher(Texts.trim(Texts.lower(actionName))).matches();
    }

    private static SearchMode resolveMode(String token) {
        return switch (Texts.trim(Texts.lower(token))) {
            case "search" -> SearchMode.CONTAINS;
            case "exact_search" -> SearchMode.EXACT;
            case "regex_search" -> SearchMode.REGEX;
            default -> throw new SearchInsertValidationException(
                    SearchInsertValidationException.Reason.INVALID_ACTION_NAME,
                    "Unsupported lore search insert mode: " + token
            );
        };
    }

    private static InsertPosition resolvePosition(String token) {
        return switch (Texts.trim(Texts.lower(token))) {
            case "above" -> InsertPosition.ABOVE;
            case "below" -> InsertPosition.BELOW;
            default -> throw new SearchInsertValidationException(
                    SearchInsertValidationException.Reason.INVALID_ACTION_NAME,
                    "Unsupported lore search insert position: " + token
            );
        };
    }
}
