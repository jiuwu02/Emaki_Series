package emaki.jiuwu.craft.corelib.script.bridge;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.graalvm.polyglot.Value;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.action.select.TargetConditionRegistry;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRegistration;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.script.host.ScriptCallbackRunner;

public final class TargetConditionScriptBinding {

    private final Supplier<TargetConditionRegistry> registry;
    private final Plugin owner;
    private final Supplier<ScriptCallbackRunner> runner;
    private final Function<String, String> reasonResolver;
    private final Consumer<String> errors;
    private final Consumer<String> warns;
    private final List<CoreTargetRegistration> handleSink;

    TargetConditionScriptBinding(@NotNull Supplier<TargetConditionRegistry> registry,
            @Nullable Plugin owner,
            @NotNull Supplier<ScriptCallbackRunner> runner,
            @NotNull Function<String, String> reasonResolver,
            @NotNull Consumer<String> errors,
            @NotNull Consumer<String> warns,
            @NotNull List<CoreTargetRegistration> handleSink) {
        this.registry = registry;
        this.owner = owner;
        this.runner = runner;
        this.reasonResolver = reasonResolver;
        this.errors = errors;
        this.warns = warns;
        this.handleSink = handleSink;
    }

    public void register(@Nullable Value payload) {
        if (payload == null || payload.isNull() || !payload.hasMembers()) {
            errors.accept("target_conditions: payload must be an object");
            return;
        }
        String id = Texts.normalizeId(Texts.toStringSafe(ScriptPayloads.textMember(payload, "id")));
        if (Texts.isBlank(id)) {
            errors.accept("target_conditions: field 'id' is required");
            return;
        }
        String description = Texts.toStringSafe(ScriptPayloads.textMember(payload, "description"));
        Value test = ScriptPayloads.functionMember(payload, "test");
        if (test == null) {
            errors.accept("target_conditions: field 'test' of '" + id + "' must be a callable function");
            return;
        }
        TargetConditionRegistry target = registry.get();
        if (target == null) {
            errors.accept("target_conditions: '" + id + "' -> target condition registry is unavailable");
            return;
        }
        ScriptCallbackRunner resolvedRunner = runner.get();
        if (resolvedRunner == null) {
            errors.accept("target_conditions: '" + id + "' -> script host is unavailable");
            return;
        }
        CoreTargetRegistration registration = target.register(owner,
                new ScriptTargetCondition(id, description, test, resolvedRunner, warns));
        if (registration == null || !registration.successful()) {
            String reason = registration == null ? "no_registration" : registration.reasonKey();
            errors.accept("target_conditions: '" + id + "' -> " + reasonResolver.apply(reason));
            return;
        }
        handleSink.add(registration);
    }
}
