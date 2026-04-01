package emaki.jiuwu.craft.corelib.action;

import java.util.Map;

public record ParsedActionLine(int lineNumber,
        String rawLine,
        String actionId,
        Map<String, String> arguments,
        ActionLineControl control) {

}
