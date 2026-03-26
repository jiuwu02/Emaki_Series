package emaki.jiuwu.craft.corelib.placeholder;

import emaki.jiuwu.craft.corelib.action.ActionContext;

public interface PlaceholderResolver {

    String resolve(ActionContext context, String text);
}
