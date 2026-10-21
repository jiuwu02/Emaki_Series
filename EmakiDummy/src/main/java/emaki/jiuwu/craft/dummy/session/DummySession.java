package emaki.jiuwu.craft.dummy.session;

import java.util.UUID;

import emaki.jiuwu.craft.dummy.transport.TransportLink;

public final class DummySession {

    private final String name;
    private final long createdAt = System.currentTimeMillis();

    private volatile DummyState state = DummyState.CREATED;
    private volatile TransportLink link;
    private volatile boolean protocolJoined;
    private volatile boolean bukkitJoined;
    private volatile int entityId;
    private volatile boolean gracefulLeave;
    private volatile boolean walkActive;
    private volatile double walkTargetX;
    private volatile double walkTargetY;
    private volatile double walkTargetZ;
    private volatile int inventoryCount;
    private volatile UUID bukkitUniqueId;
    private int walkStepCounter;
    private int reconnectAttempts;

    public DummySession(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public long createdAt() {
        return createdAt;
    }

    public synchronized DummyState state() {
        return state;
    }

    public synchronized void state(DummyState state) {
        this.state = state;
    }

    public TransportLink link() {
        return link;
    }

    public synchronized void link(TransportLink link) {
        this.link = link;
    }

    public boolean protocolJoined() {
        return protocolJoined;
    }

    public boolean bukkitJoined() {
        return bukkitJoined;
    }

    public int entityId() {
        return entityId;
    }

    public synchronized boolean applyProtocolJoin(int entityId) {
        this.entityId = entityId;
        this.protocolJoined = true;
        if (state == DummyState.CONNECTING || state == DummyState.CREATED) {
            state = DummyState.PROTOCOL_JOINED;
        }
        return promoteJoined();
    }

    public synchronized boolean applyBukkitJoin(UUID uniqueId) {
        this.bukkitUniqueId = uniqueId;
        this.bukkitJoined = true;
        if (state == DummyState.CONNECTING || state == DummyState.CREATED) {
            state = DummyState.PROTOCOL_JOINED;
        }
        return promoteJoined();
    }

    public synchronized boolean joinMismatch() {
        return protocolJoined != bukkitJoined && state != DummyState.DESTROYED;
    }

    public synchronized boolean applyQuit() {
        bukkitJoined = false;
        if (state == DummyState.JOINED || state == DummyState.PROTOCOL_JOINED) {
            state = DummyState.DISCONNECTED;
            return true;
        }
        return false;
    }

    public synchronized void applyTransportClose() {
        protocolJoined = false;
        if (state == DummyState.JOINED || state == DummyState.PROTOCOL_JOINED) {
            state = DummyState.DISCONNECTED;
        }
    }

    public synchronized void applyConnectStart() {
        protocolJoined = false;
        bukkitJoined = false;
        entityId = 0;
        state = DummyState.CONNECTING;
    }

    public synchronized void destroy() {
        state = DummyState.DESTROYED;
        walkActive = false;
    }

    private boolean promoteJoined() {
        if (protocolJoined && bukkitJoined && state != DummyState.JOINED && state != DummyState.DESTROYED) {
            state = DummyState.JOINED;
            return true;
        }
        return false;
    }

    public boolean gracefulLeave() {
        return gracefulLeave;
    }

    public void gracefulLeave(boolean gracefulLeave) {
        this.gracefulLeave = gracefulLeave;
    }

    public boolean walkActive() {
        return walkActive;
    }

    public double walkTargetX() {
        return walkTargetX;
    }

    public double walkTargetY() {
        return walkTargetY;
    }

    public double walkTargetZ() {
        return walkTargetZ;
    }

    public synchronized void walkTarget(double x, double y, double z) {
        this.walkTargetX = x;
        this.walkTargetY = y;
        this.walkTargetZ = z;
        this.walkActive = true;
        this.walkStepCounter = 0;
    }

    public synchronized void walkFinished() {
        this.walkActive = false;
    }

    public int walkStepCounter() {
        return walkStepCounter;
    }

    public void walkStepCounter(int walkStepCounter) {
        this.walkStepCounter = walkStepCounter;
    }

    public int inventoryCount() {
        return inventoryCount;
    }

    public void inventoryCount(int inventoryCount) {
        this.inventoryCount = inventoryCount;
    }

    public UUID bukkitUniqueId() {
        return bukkitUniqueId;
    }

    public int reconnectAttempts() {
        return reconnectAttempts;
    }

    public synchronized void reconnectAttempts(int reconnectAttempts) {
        this.reconnectAttempts = reconnectAttempts;
    }

    public synchronized int nextReconnectAttempt() {
        this.reconnectAttempts++;
        return this.reconnectAttempts;
    }

    public synchronized boolean isOnline() {
        return state == DummyState.JOINED;
    }
}
