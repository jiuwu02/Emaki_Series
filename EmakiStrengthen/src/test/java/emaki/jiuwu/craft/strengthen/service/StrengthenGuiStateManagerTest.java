package emaki.jiuwu.craft.strengthen.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class StrengthenGuiStateManagerTest {

    @Test
    void keepsCommittedSettlementsInFifoOrderUntilCompletion() {
        StrengthenGuiStateManager manager = new StrengthenGuiStateManager();
        UUID playerId = UUID.randomUUID();
        Player firstPlayer = player(playerId);
        Player rejoinedPlayer = player(playerId);
        AtomicInteger settlements = new AtomicInteger();

        var first = manager.addPendingSettlement(firstPlayer, "operation-1", _ -> {
            settlements.incrementAndGet();
            return true;
        });
        var second = manager.addPendingSettlement(firstPlayer, "operation-2", _ -> true);

        assertSame(first, manager.pendingSettlement(rejoinedPlayer));
        assertSame(rejoinedPlayer, first.player());
        assertEquals("operation-1", first.operationId());
        assertFalse(first.ready());

        first.markReady();
        assertTrue(first.ready());
        assertTrue(first.trySchedule());
        assertFalse(first.trySchedule());
        first.releaseSchedule();
        assertTrue(first.trySchedule());
        first.releaseSchedule();

        assertTrue(first.settle(rejoinedPlayer));
        assertEquals(1, settlements.get());
        manager.completePendingSettlement(first);
        assertSame(second, manager.pendingSettlement(rejoinedPlayer));

        manager.completePendingSettlement(second);
        assertNull(manager.pendingSettlement(rejoinedPlayer));
    }

    @Test
    void keepsPendingSettlementsWhenScheduleGateIsReleasedForRetry() {
        StrengthenGuiStateManager manager = new StrengthenGuiStateManager();
        Player player = player(UUID.randomUUID());
        var pending = manager.addPendingSettlement(player, "operation", _ -> false);
        pending.markReady();

        assertTrue(pending.trySchedule());
        pending.releaseSchedule();
        assertTrue(pending.trySchedule());
        pending.releaseSchedule();
        assertSame(pending, manager.pendingSettlement(player));
    }

    private static Player player(UUID playerId) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[] { Player.class },
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUniqueId" -> playerId;
                    case "isOnline" -> true;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == (arguments == null ? null : arguments[0]);
                    case "toString" -> "Player[" + playerId + "]";
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        return 0D;
    }
}
