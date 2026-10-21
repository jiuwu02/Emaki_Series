package emaki.jiuwu.craft.dummy.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class DummySessionTest {

    @Test
    void protocolJoinAloneStaysUnreconciled() {
        DummySession session = new DummySession("dummy_a");
        session.applyConnectStart();
        assertEquals(DummyState.CONNECTING, session.state());

        assertFalse(session.applyProtocolJoin(42));
        assertEquals(DummyState.PROTOCOL_JOINED, session.state());
        assertTrue(session.joinMismatch());
        assertFalse(session.isOnline());
    }

    @Test
    void bothSidesJoinExactlyOnceReconciles() {
        DummySession session = new DummySession("dummy_a");
        session.applyConnectStart();

        assertFalse(session.applyBukkitJoin(UUID.randomUUID()));
        assertTrue(session.joinMismatch());

        assertTrue(session.applyProtocolJoin(7));
        assertEquals(DummyState.JOINED, session.state());
        assertFalse(session.joinMismatch());
        assertTrue(session.isOnline());
        assertEquals(7, session.entityId());

        assertFalse(session.applyBukkitJoin(UUID.randomUUID()));
        assertFalse(session.applyProtocolJoin(7));
        assertTrue(session.isOnline());
    }

    @Test
    void quitDisconnectsAndResetsOnReconnectStart() {
        DummySession session = new DummySession("dummy_a");
        session.applyConnectStart();
        session.applyProtocolJoin(1);
        session.applyBukkitJoin(UUID.randomUUID());
        assertTrue(session.applyQuit());
        assertEquals(DummyState.DISCONNECTED, session.state());
        assertFalse(session.isOnline());
        assertTrue(session.joinMismatch());

        session.applyTransportClose();
        assertFalse(session.joinMismatch());

        session.applyConnectStart();
        assertEquals(DummyState.CONNECTING, session.state());
        assertEquals(0, session.entityId());
        assertFalse(session.protocolJoined());
        assertFalse(session.bukkitJoined());
    }

    @Test
    void transportCloseMovesJoinedSessionToDisconnected() {
        DummySession session = new DummySession("dummy_a");
        session.applyConnectStart();
        session.applyProtocolJoin(3);
        session.applyBukkitJoin(UUID.randomUUID());
        session.applyTransportClose();
        assertEquals(DummyState.DISCONNECTED, session.state());
        assertFalse(session.protocolJoined());
        assertTrue(session.joinMismatch());
        assertTrue(session.bukkitJoined());
    }

    @Test
    void walkTargetLifecycleAndDestroyClearActivity() {
        DummySession session = new DummySession("dummy_a");
        assertFalse(session.walkActive());
        session.walkTarget(10.5D, 64.0D, -20.5D);
        assertTrue(session.walkActive());
        assertEquals(10.5D, session.walkTargetX());
        session.walkFinished();
        assertFalse(session.walkActive());

        session.walkTarget(1.0D, 2.0D, 3.0D);
        session.destroy();
        assertEquals(DummyState.DESTROYED, session.state());
        assertFalse(session.walkActive());
    }

    @Test
    void reconnectAttemptsIncrementMonotonically() {
        DummySession session = new DummySession("dummy_a");
        assertEquals(0, session.reconnectAttempts());
        assertEquals(1, session.nextReconnectAttempt());
        assertEquals(2, session.nextReconnectAttempt());
        session.reconnectAttempts(0);
        assertEquals(0, session.reconnectAttempts());
    }
}
