package emaki.jiuwu.craft.corelib.placeholder;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class ActionInlineTokenResolver implements PlaceholderResolver {

    @Override
    public String resolve(ActionContext context, String text) {
        if (context == null || Texts.isBlank(text)) {
            return text;
        }
        String resolved = text;
        String showItem = context.placeholder("show_item");
        if (Texts.isNotBlank(showItem)) {
            resolved = resolved.replace("<show_item>", showItem);
        }
        return resolved;
    }
}
