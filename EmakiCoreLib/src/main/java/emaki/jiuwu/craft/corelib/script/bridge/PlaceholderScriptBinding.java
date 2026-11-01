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
            errors.accept("placeholders: payload 必须是对象");
            return;
        }
        String id = Texts.toStringSafe(ScriptPayloads.textMember(payload, "id")).trim();
        if (Texts.isBlank(id)) {
            errors.accept("placeholders: 必须提供字段 'id'");
            return;
        }
        Value resolve = ScriptPayloads.functionMember(payload, "resolve");
        if (resolve == null) {
            errors.accept("placeholders: '" + id + "' 的字段 'resolve' 必须是可调用函数");
            return;
        }
        PlaceholderRegistry target = registry.get();
        if (target == null) {
            errors.accept("placeholders: '" + id + "' -> 占位符注册表不可用");
            return;
        }
        ScriptCallbackRunner resolvedRunner = runner.get();
        if (resolvedRunner == null) {
            errors.accept("placeholders: '" + id + "' -> 脚本宿主不可用");
            return;
        }
        ScriptPlaceholderResolver resolver = new ScriptPlaceholderResolver(id, resolve, resolvedRunner, warns);
        target.register(resolver);
        resolverSink.add(resolver);
    }
}
