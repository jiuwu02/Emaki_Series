package emaki.jiuwu.craft.corelib.placeholder;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import java.util.ArrayList;
import java.util.List;

public final class PlaceholderRegistry {

    private final List<PlaceholderResolver> resolvers = new ArrayList<>();

    public void register(PlaceholderResolver resolver) {
        if (resolver != null) {
            resolvers.add(resolver);
        }
    }

    public String resolve(ActionContext context, String text) {
        String resolved = text;
        for (PlaceholderResolver resolver : resolvers) {
            resolved = resolver.resolve(context, resolved);
        }
        return resolved;
    }
}
