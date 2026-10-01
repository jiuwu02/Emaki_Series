package emaki.jiuwu.craft.corelib.script.bridge;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.graalvm.polyglot.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRegistry;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderResolver;
import emaki.jiuwu.craft.corelib.script.host.ScriptCallbackRunner;

public final class PlaceholderScriptBinding {

    private final Supplier<PlaceholderRegistry> registry;
    private final Supplier<ScriptCallbackRunner> runner;
    private final Consumer<String> errors;
    private final Consumer<String> warns;
    private final List<PlaceholderResolver> resolverSink;

    PlaceholderScriptBinding(@NotNull Supplier<PlaceholderRegistry> registry,
            @NotNull Supplier<ScriptCallbackRunner> runner,
            @NotNull Consumer<String> errors,
            @NotNull Consumer<String> warns,
            @NotNull List<PlaceholderResolver> resolverSink) {
        this.registry = registry;
        this.runner = runner;
        this.errors = errors;
        this.warns = warns;
        this.resolverSink = resolverSink;
    }

    public void register(@Nullable Value payload) {
        if (payload == null || payload.isNull() || !payload.hasMembers()) {
            errors.accept("placeholders: payload must be an object");
            return;
        }
        String id = Texts.toStringSafe(ScriptPayloads.textMember(payload, "id")).trim();
        if (Texts.isBlank(id)) {
            errors.accept("placeholders: field 'id' is required");
            return;
        }
        Value resolve = ScriptPayloads.functionMember(payload, "resolve");
        if (resolve == null) {
            errors.accept("placeholders: field 'resolve' of '" + id + "' must be a callable function");
            return;
        }
        PlaceholderRegistry target = registry.get();
        if (target == null) {
            errors.accept("placeholders: '" + id + "' -> placeholder registry is unavailable");
            return;
        }
        ScriptCallbackRunner resolvedRunner = runner.get();
        if (resolvedRunner == null) {
            errors.accept("placeholders: '" + id + "' -> script host is unavailable");
            return;
        }
        ScriptPlaceholderResolver resolver = new ScriptPlaceholderResolver(id, resolve, resolvedRunner, warns);
        target.register(resolver);
        resolverSink.add(resolver);
    }
}
