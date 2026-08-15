package emaki.jiuwu.craft.corelib.action.legacy;

import java.util.Map;

record LegacyParsedActionLine(int lineNumber,
        String rawLine,
        String actionId,
        Map<String, String> arguments,
        LegacyLineControl control) {
}
