package emaki.jiuwu.craft.dummy.session;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.dummy.config.AppConfig;
import emaki.jiuwu.craft.dummy.log.DummyLogger;
import emaki.jiuwu.craft.dummy.log.LogLevel;
import emaki.jiuwu.craft.dummy.transport.DummyTransport;
import emaki.jiuwu.craft.dummy.transport.TransportCallbacks;
import emaki.jiuwu.craft.dummy.transport.TransportEndpoint;
import emaki.jiuwu.craft.dummy.transport.TransportLink;

public final class DummyManager {

    public enum JoinOutcome {
        OK,
        DUPLICATE,
        LIMIT,
        INVALID_NAME,
        FAILED
    }

    public enum LeaveOutcome {
        LEFT,
        NOT_FOUND
    }

    public record JoinResult(JoinOutcome outcome, String detail) {
    }

    public record InteractOutcome(int code, String block) {
    }

    private static final double ARRIVAL_EPSILON = 0.15D;
    private static final double ATTACK_RANGE = 4.0D;
    private static final int MOVE_LOG_EVERY_STEPS = 10;

    private final JavaPlugin plugin;
    private final Supplier<AppConfig> config;
    private final DummyLogger logger;
    private final DummyTransport transport;
    private final Map<String, DummySession> sessions = new ConcurrentHashMap<>();
    private final Set<String> reconcileWarned = ConcurrentHashMap.newKeySet();

    public DummyManager(JavaPlugin plugin, Supplier<AppConfig> config, DummyLogger logger,
            DummyTransport transport) {
        this.plugin = plugin;
        this.config = config;
        this.logger = logger;
        this.transport = transport;
    }

    public Collection<DummySession> all() {
        return new ArrayList<>(sessions.values());
    }

    public DummySession find(String name) {
        if (name == null) {
            return null;
        }
        return sessions.get(name.toLowerCase(Locale.ROOT));
    }

    public int countActive() {
        int count = 0;
        for (DummySession session : sessions.values()) {
            if (session.state() != DummyState.DESTROYED) {
                count++;
            }
        }
        return count;
    }

    public int onlineCount() {
        int count = 0;
        for (DummySession session : sessions.values()) {
            if (session.isOnline()) {
                count++;
            }
        }
        return count;
    }

    public TransportEndpoint endpoint() {
        AppConfig appConfig = config.get();
        if ("velocity".equalsIgnoreCase(appConfig.connectionMode())) {
            return new TransportEndpoint(appConfig.velocityHost(), appConfig.velocityPort());
        }
        return new TransportEndpoint(appConfig.host(), appConfig.port());
    }

    public String nextName() {
        AppConfig appConfig = config.get();
        for (int index = 1; index < 1000; index++) {
            String candidate = appConfig.namePrefix() + index;
            if (find(candidate) == null) {
                return candidate;
            }
        }
        return appConfig.namePrefix() + System.currentTimeMillis();
    }

    public JoinResult join(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_]{3,16}")) {
            return new JoinResult(JoinOutcome.INVALID_NAME, name == null ? "" : name);
        }
        if (find(name) != null) {
            return new JoinResult(JoinOutcome.DUPLICATE, name);
        }
        AppConfig appConfig = config.get();
        if (countActive() >= appConfig.maxDummies()) {
            return new JoinResult(JoinOutcome.LIMIT, String.valueOf(appConfig.maxDummies()));
        }
        DummySession session = new DummySession(name);
        session.gracefulLeave(false);
        session.applyConnectStart();
        sessions.put(name.toLowerCase(Locale.ROOT), session);
        TransportEndpoint endpoint = endpoint();
        try {
            TransportLink link = transport.open(name, endpoint, callbacks(session));
            session.link(link);
        } catch (RuntimeException failure) {
            sessions.remove(name.toLowerCase(Locale.ROOT));
            session.destroy();
            logger.log(LogLevel.ERROR, name, "connect_failed endpoint="
                    + endpoint.host() + ":" + endpoint.port() + " reason=" + singleLine(failure.getMessage()));
            return new JoinResult(JoinOutcome.FAILED, singleLine(failure.getMessage()));
        }
        logger.log(LogLevel.ACTION, name, "join -> connecting endpoint="
                + endpoint.host() + ":" + endpoint.port());
        return new JoinResult(JoinOutcome.OK, name);
    }

    public LeaveOutcome leave(String name) {
        DummySession session = find(name);
        if (session == null) {
            return LeaveOutcome.NOT_FOUND;
        }
        session.gracefulLeave(true);
        session.destroy();
        TransportLink link = session.link();
        if (link != null && link.isActive()) {
            logger.log(LogLevel.ACTION, session.name(), "leave -> disconnect");
            link.disconnect("left");
        } else {
            logger.log(LogLevel.ACTION, session.name(), "leave -> already closed");
            sessions.remove(session.name().toLowerCase(Locale.ROOT));
        }
        return LeaveOutcome.LEFT;
    }

    public int stopAll() {
        int count = 0;
        for (DummySession session : all()) {
            if (session.state() != DummyState.DESTROYED) {
                leave(session.name());
                count++;
            }
        }
        return count;
    }

    public void closeTransport() {
        transport.shutdown();
    }

    public boolean say(String name, String text) {
        DummySession session = activeSession(name);
        if (session == null) {
            return false;
        }
        session.link().sendChat(text);
        logger.log(LogLevel.ACTION, session.name(), "chat -> " + singleLine(text));
        return true;
    }

    public boolean runCommand(String name, String command) {
        DummySession session = activeSession(name);
        if (session == null) {
            return false;
        }
        session.link().sendCommand(command);
        logger.log(LogLevel.ACTION, session.name(), "command -> " + command);
        return true;
    }

    public boolean startWalk(String name, double x, double y, double z) {
        DummySession session = activeSession(name);
        if (session == null) {
            return false;
        }
        session.walkTarget(x, y, z);
        logger.log(LogLevel.ACTION, session.name(), "walk -> " + formatBlock(x, y, z));
        return true;
    }

    public boolean look(String name, float yaw, float pitch) {
        DummySession session = activeSession(name);
        if (session == null) {
            return false;
        }
        session.link().sendLook(yaw, pitch);
        logger.log(LogLevel.ACTION, session.name(), "look -> yaw=" + yaw + " pitch=" + pitch);
        return true;
    }

    public InteractOutcome interact(String name, Double targetX, Double targetY, Double targetZ) {
        DummySession session = activeSession(name);
        if (session == null) {
            return new InteractOutcome(-1, "");
        }
        TransportLink link = session.link();
        if (!link.hasPose()) {
            return new InteractOutcome(-2, "");
        }
        double[] pose = link.pose();
        int blockX;
        int blockY;
        int blockZ;
        if (targetX != null && targetY != null && targetZ != null) {
            blockX = (int) Math.floor(targetX);
            blockY = (int) Math.floor(targetY);
            blockZ = (int) Math.floor(targetZ);
        } else {
            double[] forward = forwardVector((float) pose[3], (float) pose[4]);
            double eyeY = pose[1] + 1.62D;
            blockX = (int) Math.floor(pose[0] + forward[0] * 1.5D);
            blockY = (int) Math.floor(eyeY + forward[1] * 1.5D);
            blockZ = (int) Math.floor(pose[2] + forward[2] * 1.5D);
        }
        int face = dominantFace(pose, blockX, blockY, blockZ);
        link.sendInteractBlock(blockX, blockY, blockZ, face);
        String block = formatBlock(blockX, blockY, blockZ);
        logger.log(LogLevel.ACTION, session.name(), "interact -> block="
                + block + " face=" + face);
        return new InteractOutcome(0, block);
    }

    public int attack(String name) {
        DummySession session = activeSession(name);
        if (session == null) {
            return -2;
        }
        int entityId = session.link().sendAttackNearest(ATTACK_RANGE);
        if (entityId >= 0) {
            logger.log(LogLevel.ACTION, session.name(), "attack -> entity=" + entityId);
        }
        return entityId;
    }

    public int click(String name, int slot) {
        DummySession session = activeSession(name);
        if (session == null) {
            return -1;
        }
        int outcome = session.link().clickSlot(slot);
        if (outcome == 0) {
            logger.log(LogLevel.ACTION, session.name(), "click -> slot=" + slot);
            return 0;
        }
        return -2;
    }

    public void tickWalks() {
        AppConfig appConfig = config.get();
        double step = appConfig.movementStep();
        for (DummySession session : sessions.values()) {
            if (!session.walkActive() || session.state() == DummyState.DESTROYED) {
                continue;
            }
            TransportLink link = session.link();
            if (link == null || !link.isActive() || !link.hasPose()) {
                continue;
            }
            double[] pose = link.pose();
            double deltaX = session.walkTargetX() - pose[0];
            double deltaY = session.walkTargetY() - pose[1];
            double deltaZ = session.walkTargetZ() - pose[2];
            double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
            if (distance <= ARRIVAL_EPSILON) {
                session.walkFinished();
                logger.log(LogLevel.ACTION, session.name(), "arrive -> "
                        + formatBlock(session.walkTargetX(), session.walkTargetY(), session.walkTargetZ()));
                continue;
            }
            double ratio = Math.min(step, distance) / distance;
            double nextX = pose[0] + deltaX * ratio;
            double nextY = pose[1] + deltaY * ratio;
            double nextZ = pose[2] + deltaZ * ratio;
            float yaw = (float) Math.toDegrees(Math.atan2(-deltaX, deltaZ));
            float pitch = (float) Math.toDegrees(-Math.atan2(deltaY,
                    Math.sqrt(deltaX * deltaX + deltaZ * deltaZ)));
            link.sendMovement(nextX, nextY, nextZ, yaw, (float) pose[4]);
            int counter = session.walkStepCounter() + 1;
            session.walkStepCounter(counter);
            if (counter % MOVE_LOG_EVERY_STEPS == 0) {
                logger.log(LogLevel.ACTION, session.name(), "move -> " + formatBlock(nextX, nextY, nextZ)
                        + " yaw=" + yaw);
            }
        }
    }

    public void tickReconcile() {
        for (DummySession session : sessions.values()) {
            String key = session.name().toLowerCase(Locale.ROOT);
            if (session.joinMismatch() && reconcileWarned.add(key)) {
                logger.log(LogLevel.ERROR, session.name(), "join_reconcile_mismatch protocol_joined="
                        + session.protocolJoined() + " bukkit_joined=" + session.bukkitJoined());
            }
        }
    }

    public void markBukkitJoin(String name, UUID uniqueId) {
        DummySession session = find(name);
        if (session == null) {
            return;
        }
        if (session.applyBukkitJoin(uniqueId)) {
            logger.log(LogLevel.EVENT, session.name(), "joined bukkit_uuid=" + uniqueId);
        }
    }

    public void markBukkitQuit(String name) {
        DummySession session = find(name);
        if (session == null) {
            return;
        }
        session.applyQuit();
        reconcileWarned.remove(name.toLowerCase(Locale.ROOT));
    }

    public void scheduleReconnect(DummySession session) {
        AppConfig appConfig = config.get();
        if (!appConfig.reconnectEnabled() || session.gracefulLeave()
                || session.state() == DummyState.DESTROYED) {
            return;
        }
        int attempt = session.nextReconnectAttempt();
        int maxAttempts = appConfig.reconnectMaxAttempts();
        if (maxAttempts > 0 && attempt > maxAttempts) {
            logger.log(LogLevel.ERROR, session.name(),
                    "reconnect_gave_up attempts=" + maxAttempts);
            sessions.remove(session.name().toLowerCase(Locale.ROOT));
            session.destroy();
            return;
        }
        int delay = Math.max(1, appConfig.reconnectDelayTicks());
        logger.log(LogLevel.ACTION, session.name(), "reconnect_scheduled attempt=" + attempt
                + " delay=" + delay + "t");
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin,
                task -> reconnect(session), delay);
    }

    private void reconnect(DummySession session) {
        if (session.gracefulLeave() || session.state() == DummyState.DESTROYED
                || find(session.name()) != session) {
            return;
        }
        session.applyConnectStart();
        TransportEndpoint endpoint = endpoint();
        try {
            TransportLink link = transport.open(session.name(), endpoint, callbacks(session));
            session.link(link);
            logger.log(LogLevel.ACTION, session.name(), "reconnect -> connecting endpoint="
                    + endpoint.host() + ":" + endpoint.port());
        } catch (RuntimeException failure) {
            logger.log(LogLevel.ERROR, session.name(), "reconnect_failed reason="
                    + singleLine(failure.getMessage()));
            scheduleReconnect(session);
        }
    }

    public boolean inventoryCount(String name, int[] outCount) {
        DummySession session = find(name);
        if (session == null) {
            return false;
        }
        outCount[0] = session.inventoryCount();
        return true;
    }

    private DummySession activeSession(String name) {
        DummySession session = find(name);
        if (session == null || session.link() == null || !session.link().isActive()) {
            return null;
        }
        return session;
    }

    private TransportCallbacks callbacks(DummySession session) {
        return new TransportCallbacks() {
            @Override
            public void onProtocolPacket(String direction, String packetName) {
                logger.log(LogLevel.RECV, session.name(),
                        "protocol " + direction + " packet=" + packetName);
            }

            @Override
            public void onJoined(int entityId) {
                boolean reconciled = session.applyProtocolJoin(entityId);
                logger.log(LogLevel.RECV, session.name(), "join_game entity=" + entityId);
                if (reconciled) {
                    logger.log(LogLevel.EVENT, session.name(), "join_reconciled protocol+bukkit");
                }
            }

            @Override
            public void onTeleport(double x, double y, double z, float yaw, float pitch) {
                logger.log(LogLevel.RECV, session.name(), "teleport -> " + formatBlock(x, y, z)
                        + " yaw=" + yaw + " pitch=" + pitch);
            }

            @Override
            public void onServerMessage(String text, boolean overlay) {
                logger.log(LogLevel.RECV, session.name(),
                        (overlay ? "actionbar" : "message") + " | " + singleLine(text));
            }

            @Override
            public void onWindowOpened(int windowId, String title) {
                logger.log(LogLevel.RECV, session.name(),
                        "window_open id=" + windowId + " title=" + singleLine(title));
            }

            @Override
            public void onWindowContent(int windowId, int stateId, int itemCount) {
                if (windowId == 0) {
                    session.inventoryCount(itemCount);
                }
                logger.log(LogLevel.RECV, session.name(),
                        "window=" + windowId + " state=" + stateId + " items=" + itemCount);
            }

            @Override
            public void onWindowClosed(int windowId) {
                logger.log(LogLevel.RECV, session.name(), "window_closed id=" + windowId);
            }

            @Override
            public void onKicked(String reason) {
                logger.log(LogLevel.ERROR, session.name(), "kicked | " + singleLine(reason));
            }

            @Override
            public void onClosed(String reason) {
                boolean graceful = session.gracefulLeave();
                session.applyTransportClose();
                if (graceful) {
                    logger.log(LogLevel.ACTION, session.name(), "closed | " + singleLine(reason));
                    sessions.remove(session.name().toLowerCase(Locale.ROOT));
                    session.destroy();
                    return;
                }
                logger.log(LogLevel.ERROR, session.name(), "connection_lost | " + singleLine(reason));
                scheduleReconnect(session);
            }
        };
    }

    private static double[] forwardVector(float yaw, float pitch) {
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        double cosPitch = Math.cos(pitchRadians);
        return new double[] {
                -Math.sin(yawRadians) * cosPitch,
                -Math.sin(pitchRadians),
                Math.cos(yawRadians) * cosPitch
        };
    }

    private static int dominantFace(double[] pose, int blockX, int blockY, int blockZ) {
        double eyeY = pose[1] + 1.62D;
        double deltaX = pose[0] - (blockX + 0.5D);
        double deltaY = eyeY - (blockY + 0.5D);
        double deltaZ = pose[2] - (blockZ + 0.5D);
        double absX = Math.abs(deltaX);
        double absY = Math.abs(deltaY);
        double absZ = Math.abs(deltaZ);
        if (absX >= absY && absX >= absZ) {
            return deltaX > 0 ? 5 : 4;
        }
        if (absY >= absZ) {
            return deltaY > 0 ? 1 : 0;
        }
        return deltaZ > 0 ? 3 : 2;
    }

    private static String formatBlock(double x, double y, double z) {
        return String.format(Locale.ROOT, "%.1f,%.1f,%.1f", x, y, z);
    }

    private static String singleLine(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() > 300 ? normalized.substring(0, 300) : normalized;
    }
}
