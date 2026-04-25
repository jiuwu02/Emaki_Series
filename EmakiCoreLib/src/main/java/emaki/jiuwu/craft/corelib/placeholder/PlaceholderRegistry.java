package emaki.jiuwu.craft.corelib.placeholder;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import emaki.jiuwu.craft.corelib.action.ActionContext;

public final class PlaceholderRegistry {

    // CopyOnWriteArrayList 保证并发安全：register 可能在异步线程调用，resolve 在主线程遍历
    private final List<PlaceholderResolver> resolvers = new CopyOnWriteArrayList<>();

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
