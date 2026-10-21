package emaki.jiuwu.craft.dummy.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ExpressionEngineTest {

    private static final class ScriptedContext implements ExpressionContext {
        double onlineCount = 1.0D;
        Map<String, Double> inventory = new HashMap<>();
        Map<String, Double> events = new HashMap<>();
        Set<String> audit = new HashSet<>();
        Set<String> received = new HashSet<>();

        @Override
        public double onlineCount() {
            return onlineCount;
        }

        @Override
        public double dummyOnline(String name) {
            return "dummy_a".equals(name) ? 1.0D : 0.0D;
        }

        @Override
        public double eventCount(String type) {
            return events.getOrDefault(type, 0.0D);
        }

        @Override
        public boolean auditContains(String text) {
            return audit.stream().anyMatch(line -> line.contains(text));
        }

        @Override
        public boolean recvContains(String name, String text) {
            return received.contains(name + "|" + text);
        }

        @Override
        public double inventoryCount(String name) {
            return inventory.getOrDefault(name, -1.0D);
        }
    }

    private final ScriptedContext context = new ScriptedContext();

    @Test
    void arithmeticFollowsPrecedence() throws ExpressionException {
        assertEquals(14.0D, ExpressionEngine.evaluate("2 + 3 * 4", context).number());
        assertEquals(3.0D, ExpressionEngine.evaluate("(2 + 4) / 2", context).number());
        assertEquals(-5.0D, ExpressionEngine.evaluate("0 - 5", context).number());
    }

    @Test
    void comparisonsAndLogicOperators() throws ExpressionException {
        assertTrue(ExpressionEngine.evaluate("5 > 3", context).isTruthy());
        assertTrue(ExpressionEngine.evaluate("2 <= 2", context).isTruthy());
        assertFalse(ExpressionEngine.evaluate("1 == 2", context).isTruthy());
        assertTrue(ExpressionEngine.evaluate("1 < 2 && 3 > 2", context).isTruthy());
        assertFalse(ExpressionEngine.evaluate("false || (1 > 2)", context).isTruthy());
        assertTrue(ExpressionEngine.evaluate("!false", context).isTruthy());
        assertTrue(ExpressionEngine.evaluate("'alpha' == 'alpha'", context).isTruthy());
        assertFalse(ExpressionEngine.evaluate("'alpha' == 'beta'", context).isTruthy());
    }

    @Test
    void functionsReadContextValues() throws ExpressionException {
        context.onlineCount = 4.0D;
        context.events.put("DungeonEnterEvent", 2.0D);
        context.inventory.put("dummy_a", 7.0D);
        context.audit.add("2026-10-21T00:00:00 [settle] event=complete");
        context.received.add("dummy_b|hello world");

        assertEquals(4.0D, ExpressionEngine.evaluate("online_count", context).number());
        assertEquals(4.0D, ExpressionEngine.evaluate("online_count()", context).number());
        assertEquals(2.0D,
                ExpressionEngine.evaluate("event_count('DungeonEnterEvent')", context).number());
        assertEquals(1.0D, ExpressionEngine.evaluate("dummy_online('dummy_a')", context).number());
        assertEquals(0.0D, ExpressionEngine.evaluate("dummy_online('ghost')", context).number());
        assertEquals(7.0D, ExpressionEngine.evaluate("inventory_count('dummy_a')", context).number());
        assertTrue(ExpressionEngine
                .evaluate("audit_contains('event=complete')", context).isTruthy());
        assertTrue(ExpressionEngine
                .evaluate("recv_contains('dummy_b', 'hello world')", context).isTruthy());
        assertFalse(ExpressionEngine
                .evaluate("recv_contains('dummy_b', 'missing')", context).isTruthy());
    }

    @Test
    void reportValuesCarryHumanReadableForms() throws ExpressionException {
        assertEquals("3", ExpressionEngine.evaluate("1 + 2", context).describe());
        assertEquals("true", ExpressionEngine.evaluate("2 > 1", context).describe());
        assertEquals("probe", ExpressionEngine.evaluate("'probe'", context).describe());
    }

    @Test
    void invalidInputsRaiseExpressionException() {
        assertThrows(ExpressionException.class,
                () -> ExpressionEngine.evaluate("unknown_var", context));
        assertThrows(ExpressionException.class,
                () -> ExpressionEngine.evaluate("unknown_fn('x')", context));
        assertThrows(ExpressionException.class,
                () -> ExpressionEngine.evaluate("1 +", context));
        assertThrows(ExpressionException.class,
                () -> ExpressionEngine.evaluate("'unterminated", context));
        assertThrows(ExpressionException.class,
                () -> ExpressionEngine.evaluate("online_count('extra')", context));
        assertThrows(ExpressionException.class,
                () -> ExpressionEngine.evaluate("", context));
        assertThrows(ExpressionException.class,
                () -> ExpressionEngine.evaluate("1 + 'text'", context));
    }
}
