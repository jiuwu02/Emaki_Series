package emaki.jiuwu.craft.corelib.script.bridge;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.graalvm.polyglot.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.script.host.ScriptCallbackRunner;

public final class ExpressionScriptBinding {

    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]+");

    private final Supplier<ScriptCallbackRunner> runner;
    private final Consumer<String> errors;
    private final Consumer<String> warns;
    private final List<String> nameSink;

    ExpressionScriptBinding(@NotNull Supplier<ScriptCallbackRunner> runner,
            @NotNull Consumer<String> errors,
            @NotNull Consumer<String> warns,
            @NotNull List<String> nameSink) {
        this.runner = runner;
        this.errors = errors;
        this.warns = warns;
        this.nameSink = nameSink;
    }

    public void register(@Nullable Value payload) {
        if (payload == null || payload.isNull() || !payload.hasMembers()) {
            errors.accept("expressions: payload 必须是对象");
            return;
        }
        String name = Texts.toStringSafe(ScriptPayloads.textMember(payload, "name")).trim();
        if (Texts.isBlank(name)) {
            errors.accept("expressions: 必须提供字段 'name'");
            return;
        }
        if (!NAME_PATTERN.matcher(name).matches()) {
            errors.accept("expressions: 函数名 '" + name + "' 无效");
            return;
        }
        if (ExpressionEngine.isBuiltinFunctionName(name)) {
            errors.accept("expressions: 函数名 '" + name + "' 与内置函数冲突");
            return;
        }
        Integer arguments = ScriptPayloads.intMember(payload, "args");
        if (arguments == null || arguments < 1) {
            errors.accept("expressions: '" + name + "' 的字段 'args' 必须是 >= 1 的整数");
            return;
        }
        Value fn = ScriptPayloads.functionMember(payload, "fn");
        if (fn == null) {
            errors.accept("expressions: '" + name + "' 的字段 'fn' 必须是可调用函数");
            return;
        }
        ScriptCallbackRunner resolvedRunner = runner.get();
        if (resolvedRunner == null) {
            errors.accept("expressions: '" + name + "' -> 脚本宿主不可用");
            return;
        }
        if (!ExpressionEngine.registerDynamicFunction(name, new ScriptExpressionFunction(name, arguments, fn,
                resolvedRunner, warns))) {
            errors.accept("expressions: 函数名 '" + name + "' 已注册");
            return;
        }
        nameSink.add(name);
    }
}
