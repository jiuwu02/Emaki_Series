package emaki.jiuwu.craft.corelib.script.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import net.objecthunter.exp4j.function.Function;

@DisplayName("ExpressionEngine 动态函数注册表")
class ExpressionEngineDynamicFunctionTest {

    private static final String DOUBLE_NAME = "sc_test_double";
    private static final String TRIPLE_NAME = "sc_test_triple";

    @AfterEach
    void cleanup() {
        ExpressionEngine.unregisterDynamicFunction(DOUBLE_NAME);
        ExpressionEngine.unregisterDynamicFunction(TRIPLE_NAME);
    }

    private static Function doublingFunction(String name, int arguments) {
        return new Function(name, arguments) {

            @Override
            public double apply(double... args) {
                return args[0] * 2D;
            }
        };
    }

    @Test
    @DisplayName("注册后表达式求值生效，内置函数行为不变")
    void registeredFunctionEvaluatesAndBuiltinsStayIntact() {
        assertTrue(ExpressionEngine.registerDynamicFunction(DOUBLE_NAME, doublingFunction(DOUBLE_NAME, 1)));
        assertEquals(10.0D, ExpressionEngine.evaluate(DOUBLE_NAME + "(5)"), 1.0E-9);
        assertEquals(2.0D, ExpressionEngine.evaluate("ceil(1.2)"), 1.0E-9);
        assertEquals(3.0D, ExpressionEngine.evaluate("min(3, 4)"), 1.0E-9);
    }

    @Test
    @DisplayName("重名与内置名拒绝注册")
    void duplicateAndBuiltinNamesAreRejected() {
        assertTrue(ExpressionEngine.registerDynamicFunction(TRIPLE_NAME, doublingFunction(TRIPLE_NAME, 1)));
        assertFalse(ExpressionEngine.registerDynamicFunction(TRIPLE_NAME, doublingFunction(TRIPLE_NAME, 1)));
        assertFalse(ExpressionEngine.registerDynamicFunction("ceil", doublingFunction("ceil", 1)));
        assertFalse(ExpressionEngine.registerDynamicFunction("pow", doublingFunction("pow", 2)));
        assertFalse(ExpressionEngine.registerDynamicFunction("", null));
        assertFalse(ExpressionEngine.registerDynamicFunction(DOUBLE_NAME, null));
    }

    @Test
    @DisplayName("注销后表达式不再可用，且允许重新注册")
    void unregisterRestoresPriorBehaviour() {
        assertTrue(ExpressionEngine.registerDynamicFunction(DOUBLE_NAME, doublingFunction(DOUBLE_NAME, 1)));
        assertEquals(8.0D, ExpressionEngine.evaluate(DOUBLE_NAME + "(4)"), 1.0E-9);
        assertTrue(ExpressionEngine.unregisterDynamicFunction(DOUBLE_NAME));
        assertFalse(ExpressionEngine.unregisterDynamicFunction(DOUBLE_NAME));
        assertTrue(ExpressionEngine.evaluateNumericDetailed(DOUBLE_NAME + "(4)").hasIssues());
        assertTrue(ExpressionEngine.registerDynamicFunction(DOUBLE_NAME, doublingFunction(DOUBLE_NAME, 1)));
        assertEquals(8.0D, ExpressionEngine.evaluate(DOUBLE_NAME + "(4)"), 1.0E-9);
    }

    @Test
    @DisplayName("注册使缓存失效：先求值失败的表达式在注册后重新求值成功")
    void registrationInvalidatesCacheAfterEarlierFailure() {
        assertTrue(ExpressionEngine.evaluateNumericDetailed(DOUBLE_NAME + "(2)+1").hasIssues());
        assertTrue(ExpressionEngine.registerDynamicFunction(DOUBLE_NAME, doublingFunction(DOUBLE_NAME, 1)));
        assertEquals(5.0D, ExpressionEngine.evaluateNumericDetailed(DOUBLE_NAME + "(2)+1").value(), 1.0E-9);
    }

    @Test
    @DisplayName("未注册任何动态函数时求值与原行为等价")
    void emptyDynamicTableKeepsOriginalBehaviour() {
        assertEquals(1.0D, ExpressionEngine.evaluate("ceil(0.5)"), 1.0E-9);
        assertTrue(ExpressionEngine.evaluateNumericDetailed("no_such_fn_abc(1)").hasIssues());
    }
}
