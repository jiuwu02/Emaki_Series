package emaki.jiuwu.craft.corelib.placeholder;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class ActionContextPlaceholderResolver implements PlaceholderResolver {

    @Override
    public String resolve(ActionContext context, String text) {
        if (context == null || Texts.isBlank(text)) {
            return text;
        }
        return PlaceholderRenderer.renderInternal(text, PlaceholderRenderer.contextVariables(context));
    }
}
