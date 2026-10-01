package emaki.jiuwu.craft.corelib.script.bridge;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.graalvm.polyglot.Value;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.action.pipeline.registry.StageRegistry;
import emaki.jiuwu.craft.corelib.api.action.CoreStageRegistration;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.script.host.ScriptCallbackRunner;

public final class ActionStageScriptBinding {

    private final Supplier<StageRegistry> registry;
    private final Plugin owner;
    private final Supplier<ScriptCallbackRunner> runner;
    private final long defaultTimeoutMillis;
    private final Function<String, String> reasonResolver;
    private final Consumer<String> errors;
    private final List<ScriptActionStage> stageSink;
    private final List<CoreStageRegistration> handleSink;

    ActionStageScriptBinding(@NotNull Supplier<StageRegistry> registry,
            @Nullable Plugin owner,
            @NotNull Supplier<ScriptCallbackRunner> runner,
            long defaultTimeoutMillis,
            @NotNull Function<String, String> reasonResolver,
            @NotNull Consumer<String> errors,
            @NotNull List<ScriptActionStage> stageSink,
            @NotNull List<CoreStageRegistration> handleSink) {
        this.registry = registry;
        this.owner = owner;
        this.runner = runner;
        this.defaultTimeoutMillis = defaultTimeoutMillis;
        this.reasonResolver = reasonResolver;
        this.errors = errors;
        this.stageSink = stageSink;
        this.handleSink = handleSink;
    }

    public void register(@Nullable Value payload) {
        PayloadResult<ActionStagePayload> parsed = ActionStagePayload.parse(payload, defaultTimeoutMillis);
        if (parsed.failed() || parsed.value() == null) {
            errors.accept(Texts.toStringSafe(parsed.error()));
            return;
        }
        StageRegistry target = registry.get();
        ActionStagePayload data = parsed.value();
        if (target == null) {
            errors.accept("actions: '" + data.id() + "' -> stage registry is unavailable");
            return;
        }
        ScriptCallbackRunner resolvedRunner = runner.get();
        if (resolvedRunner == null) {
            errors.accept("actions: '" + data.id() + "' -> script host is unavailable");
            return;
        }
        ScriptActionStage stage = new ScriptActionStage(data.id(), data.description(), data.category(),
                data.parameters(), data.timeoutMillis(), data.execute(), resolvedRunner);
        CoreStageRegistration registration = target.registerAction(owner, stage);
        if (registration == null || !registration.successful()) {
            String reason = registration == null ? "no_registration" : registration.reasonKey();
            errors.accept("actions: '" + data.id() + "' -> " + reasonResolver.apply(reason));
            return;
        }
        stageSink.add(stage);
        handleSink.add(registration);
    }
}
