package emaki.jiuwu.craft.corelib.placeholder;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class PlaceholderApiResolver implements PlaceholderResolver {

    public PlaceholderApiResolver() {
        PlaceholderRenderer.refreshPlaceholderApiState();
    }

    @Override
    public String resolve(ActionContext context, String text) {
        if (context == null || context.player() == null || Texts.isBlank(text)) {
            return text;
        }
        if (!PlaceholderRenderer.placeholderApiAvailable()) {
            return text;
        }
        try {
            return PlaceholderRenderer.renderPapi(context.player(), text, null, "placeholder_api");
        } catch (Exception _) {
            return text;
        }
    }
}
