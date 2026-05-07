package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.Map;

import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;

final class ActionItemSourceArguments {

    private ActionItemSourceArguments() {
    }

    static ItemSource resolve(Map<String, String> arguments) {
        String raw = Texts.toStringSafe(arguments.get("source"));
        if (Texts.isBlank(raw)) {
            raw = Texts.toStringSafe(arguments.get("item"));
        }
        if (Texts.isBlank(raw)) {
            raw = Texts.toStringSafe(arguments.get("item_source"));
        }
        return ItemSourceUtil.parse(raw);
    }

    static boolean isAlias(String name) {
        String normalized = Texts.lower(name);
        return "item".equals(normalized) || "item_source".equals(normalized);
    }
}
