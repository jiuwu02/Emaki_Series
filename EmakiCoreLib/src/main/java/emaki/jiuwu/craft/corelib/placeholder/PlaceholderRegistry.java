package emaki.jiuwu.craft.corelib.placeholder;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import emaki.jiuwu.craft.corelib.action.ActionContext;

public final class PlaceholderRegistry {

    private final List<PlaceholderResolver> resolvers = new CopyOnWriteArrayList<>();

    public void register(PlaceholderResolver resolver) {
        if (resolver != null) {
            resolvers.add(resolver);
        }
    }

    public String resolve(ActionContext context, String text) {
        if (text == null || text.isEmpty() || !containsPlaceholderMarker(text)) {
            return text;
        }
        String resolved = text;
        for (PlaceholderResolver resolver : resolvers) {
            resolved = resolver.resolve(context, resolved);
        }
        return resolved;
    }

    private static boolean containsPlaceholderMarker(String text) {
        for (int i = 0, len = text.length(); i < len; i++) {
            char ch = text.charAt(i);
            if (ch == '{' || ch == '%' || ch == '<') {
                return true;
            }
        }
        return false;
    }
}
