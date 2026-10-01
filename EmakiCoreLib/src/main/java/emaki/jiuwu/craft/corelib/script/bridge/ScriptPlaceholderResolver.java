package emaki.jiuwu.craft.corelib.script.bridge;

import org.graalvm.polyglot.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderResolver;
import emaki.jiuwu.craft.corelib.script.host.ScriptCallbackException;
import emaki.jiuwu.craft.corelib.script.host.ScriptCallbackRunner;

final class ScriptPlaceholderResolver implements PlaceholderResolver {

    private final String id;
    private final Value resolve;
    private final ScriptCallbackRunner runner;
    private final java.util.function.Consumer<String> warns;

    ScriptPlaceholderResolver(@NotNull String id,
            @NotNull Value resolve,
            @NotNull ScriptCallbackRunner runner,
            @NotNull java.util.function.Consumer<String> warns) {
        this.id = id;
        this.resolve = resolve;
        this.runner = runner;
        this.warns = warns;
    }

    String id() {
        return id;
    }

    @Override
    public String resolve(@Nullable ActionContext context, @Nullable String text) {
        if (text == null) {
            return null;
        }
        ScriptPlaceholderContextExport export = new ScriptPlaceholderContextExport(context);
        try {
            Value result = runner.run(resolve, text, export);
            if (result == null || result.isNull()) {
                return text;
            }
            try {
                return Texts.toStringSafe(result.isString() ? result.asString() : result.toString());
            } catch (RuntimeException exception) {
                warn(exception.getMessage());
                return text;
            }
        } catch (ScriptCallbackException exception) {
            warn(exception.getMessage());
            return text;
        }
    }

    private void warn(@Nullable String detail) {
        warns.accept("placeholders: resolver '" + id + "' failed: " + Texts.toStringSafe(detail));
    }
}
