package emaki.jiuwu.craft.corelib.script.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.script.host.ScriptHost;
import emaki.jiuwu.craft.corelib.script.host.ScriptHostSettings;

@DisplayName("表达式函数包装桥")
class ScriptExpressionFunctionTest {

    private static final String DOUBLE_NAME = "sc_expr_double";
    private static final String BOOM_NAME = "sc_expr_boom";
    private static final String TEXT_NAME = "sc_expr_text";

    @TempDir
    Path dataDirectory;

    private ScriptHost host;
    private final List<String> errors = new ArrayList<>();
    private final List<String> warns = new ArrayList<>();
    private final List<String> names = new ArrayList<>();

    @AfterEach
    void tearDown() {
        if (host != null) {
            host.close();
            host = null;
        }
        ExpressionEngine.unregisterDynamicFunction(DOUBLE_NAME);
        ExpressionEngine.unregisterDynamicFunction(BOOM_NAME);
        ExpressionEngine.unregisterDynamicFunction(TEXT_NAME);
    }

    private void loadScripts(String... files) throws IOException {
        Path scripts = dataDirectory.resolve("scripts");
        Files.createDirectories(scripts);
        for (int index = 0; index < files.length; index += 2) {
            Files.writeString(scripts.resolve(files[index]), files[index + 1]);
        }
        ExpressionScriptBinding binding = new ExpressionScriptBinding(
                () -> host == null ? null : host.callbackRunner(),
                errors::add,
                warns::add,
                names);
        host = new ScriptHost(null, scripts, new ScriptHostSettings(true, 500, false),
                Map.of("expressions", binding));
        host.load();
    }

    @Test
    @DisplayName("JS 函数注册后参与表达式求值")
    void jsFunctionParticipatesInEvaluation() throws IOException {
        loadScripts("double.js",
                "expressions.register({name: '" + DOUBLE_NAME + "', args: 1, "
                        + "fn: function (a) { return a * 2; }});");
        assertTrue(errors.isEmpty(), () -> "unexpected errors: " + errors);
        assertEquals(List.of(DOUBLE_NAME), names);
        assertEquals(42.0D, ExpressionEngine.evaluate(DOUBLE_NAME + "(21)"), 1.0E-9);
    }

    @Test
    @DisplayName("JS 函数抛异常按 NaN 语义转为求值失败并告警")
    void throwingFunctionYieldsFailureSemantics() throws IOException {
        loadScripts("boom.js",
                "expressions.register({name: '" + BOOM_NAME + "', args: 1, "
                        + "fn: function (a) { throw new Error('boom'); }});");
        assertTrue(errors.isEmpty(), () -> "unexpected errors: " + errors);
        assertTrue(ExpressionEngine.evaluateNumericDetailed(BOOM_NAME + "(1)").hasIssues());
        assertEquals(1, warns.size());
        assertTrue(warns.getFirst().contains(BOOM_NAME), () -> "warn should name the function: " + warns);
    }

    @Test
    @DisplayName("非数值返回值按 NaN 语义处理")
    void nonNumericResultYieldsFailureSemantics() throws IOException {
        loadScripts("text.js",
                "expressions.register({name: '" + TEXT_NAME + "', args: 1, "
                        + "fn: function (a) { return 'not-a-number'; }});");
        assertTrue(errors.isEmpty(), () -> "unexpected errors: " + errors);
        assertTrue(ExpressionEngine.evaluateNumericDetailed(TEXT_NAME + "(1)").hasIssues());
        assertTrue(warns.getFirst().contains(TEXT_NAME), () -> "warn should name the function: " + warns);
    }

    @Test
    @DisplayName("重名注册、内置名注册与非法 payload 被显式拒绝")
    void invalidPayloadsAreRejectedExplicitly() throws IOException {
        loadScripts("builtin.js",
                        "expressions.register({name: 'ceil', args: 1, fn: function (a) { return a; }});",
                "first.js",
                        "expressions.register({name: '" + DOUBLE_NAME + "', args: 1, fn: function (a) { return a; }});",
                "missing.js",
                        "expressions.register({name: 'sc_expr_missing', args: 1});",
                "second.js",
                        "expressions.register({name: '" + DOUBLE_NAME + "', args: 1, fn: function (a) { return a; }});");
        assertEquals(1, names.size());
        assertEquals(3, errors.size());
        assertTrue(errors.get(0).contains("built-in"), () -> String.valueOf(errors));
        assertTrue(errors.get(1).contains("'fn'"), () -> String.valueOf(errors));
        assertTrue(errors.get(2).contains("already registered"), () -> String.valueOf(errors));
    }
}
