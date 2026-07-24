package emaki.jiuwu.craft.corelib.placeholder;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class PlaceholderRegistry {

    private final List<PlaceholderResolver> resolvers = new CopyOnWriteArrayList<>();
    private final Supplier<DebugLogger> debugLoggerSupplier;

    public PlaceholderRegistry() {
        this(null);
    }

    public PlaceholderRegistry(Supplier<DebugLogger> debugLoggerSupplier) {
        this.debugLoggerSupplier = debugLoggerSupplier == null ? () -> null : debugLoggerSupplier;
    }

    public void register(PlaceholderResolver resolver) {
        if (resolver != null) {
            resolvers.add(resolver);
        }
    }

    public void unregister(PlaceholderResolver resolver) {
        if (resolver != null) {
            resolvers.remove(resolver);
        }
    }

    public String resolve(ActionContext context, String text) {
        if (text == null || text.isEmpty() || !containsPlaceholderMarker(text)) {
            return text;
        }
        DebugLogger debugLogger = debugLogger();
        String resolved = PlaceholderRenderer.renderInternal(
                text,
                PlaceholderRenderer.contextVariables(context),
                debugLogger,
                context == null ? null : context.player(),
                "action_context"
        );
        for (PlaceholderResolver resolver : resolvers) {
            if (resolver instanceof PlaceholderApiResolver) {
                continue;
            }
            String before = resolved;
            resolved = resolver.resolve(context, resolved);
            debugResolver(context, debugLogger, resolver, before, resolved);
        }
        for (PlaceholderResolver resolver : resolvers) {
            if (!(resolver instanceof PlaceholderApiResolver)) {
                continue;
            }
            String before = resolved;
            resolved = resolver.resolve(context, resolved);
            debugResolver(context, debugLogger, resolver, before, resolved);
        }
        return resolved;
    }

    private DebugLogger debugLogger() {
        return debugLoggerSupplier.get();
    }

    private void debugResolver(ActionContext context,
            DebugLogger debugLogger,
            PlaceholderResolver resolver,
            String before,
            String after) {
        if (debugLogger == null || resolver == null || Objects.equals(before, after)) {
            return;
        }
        debugLogger.log(PlaceholderRenderer.DEBUG_PLACEHOLDER,
                context == null ? null : context.player(),
                "common.placeholder.resolver_changed",
                Map.of(
                        "resolver", resolver.getClass().getSimpleName(),
                        "before", Texts.toStringSafe(before),
                        "after", Texts.toStringSafe(after)
                ));
    }

    private static boolean containsPlaceholderMarker(String text) {
        for (int i = 0, len = text.length(); i < len; i++) {
            char ch = text.charAt(i);
            if (ch == '%' || ch == '<') {
                return true;
            }
        }
        return false;
    }
}
