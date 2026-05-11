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
        // 快速路径：如果文本不包含任何占位符标记，直接返回，跳过 resolver 链遍历
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
