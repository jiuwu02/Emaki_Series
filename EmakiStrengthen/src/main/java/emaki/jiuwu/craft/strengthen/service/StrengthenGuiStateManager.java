package emaki.jiuwu.craft.strengthen.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.session.PlayerSessionMap;

final class StrengthenGuiStateManager {

    static final class PendingSettlement {

        private final UUID playerId;
        private final String operationId;
        private final Predicate<Player> settlement;
        private final AtomicBoolean ready = new AtomicBoolean();
        private final AtomicBoolean scheduled = new AtomicBoolean();
        private volatile Player player;

        private PendingSettlement(Player player, String operationId, Predicate<Player> settlement) {
            this.player = player;
            this.playerId = player.getUniqueId();
            this.operationId = operationId == null ? "" : operationId;
            this.settlement = settlement;
        }

        public UUID playerId() {
            return playerId;
        }

        public String operationId() {
            return operationId;
        }

        public Player player() {
            return player;
        }

        public void updatePlayer(Player player) {
            if (player != null && playerId.equals(player.getUniqueId())) {
                this.player = player;
            }
        }

        public void markReady() {
            ready.set(true);
        }

        public boolean ready() {
            return ready.get();
        }

        public boolean trySchedule() {
            return scheduled.compareAndSet(false, true);
        }

        public void releaseSchedule() {
            scheduled.set(false);
        }

        public boolean settle(Player player) {
            return settlement.test(player);
        }
    }

    private final PlayerSessionMap<StrengthenGuiSession> sessions = new PlayerSessionMap<>(StrengthenGuiSession::player);
    private final Map<UUID, ConcurrentLinkedQueue<PendingSettlement>> pendingSettlements = new ConcurrentHashMap<>();

    public StrengthenGuiSession get(Player player) {
        return sessions.get(player);
    }

    public void put(StrengthenGuiSession session) {
        sessions.put(session);
    }

    public void remove(Player player) {
        sessions.remove(player);
    }

    public boolean remove(StrengthenGuiSession session) {
        return sessions.remove(session);
    }

    public boolean isCurrent(StrengthenGuiSession session) {
        return session != null && sessions.get(session.player()) == session;
    }

    public PendingSettlement addPendingSettlement(Player player,
            String operationId,
            Predicate<Player> settlement) {
        PendingSettlement pending = new PendingSettlement(player, operationId, settlement);
        pendingSettlements.computeIfAbsent(pending.playerId(), _ -> new ConcurrentLinkedQueue<>()).add(pending);
        return pending;
    }

    public PendingSettlement pendingSettlement(Player player) {
        if (player == null) {
            return null;
        }
        ConcurrentLinkedQueue<PendingSettlement> queue = pendingSettlements.get(player.getUniqueId());
        PendingSettlement pending = queue == null ? null : queue.peek();
        if (pending != null) {
            pending.updatePlayer(player);
        }
        return pending;
    }

    public boolean hasPendingSettlement(Player player) {
        return pendingSettlement(player) != null;
    }

    public void completePendingSettlement(PendingSettlement pending) {
        if (pending == null) {
            return;
        }
        ConcurrentLinkedQueue<PendingSettlement> queue = pendingSettlements.get(pending.playerId());
        if (queue == null) {
            return;
        }
        queue.remove(pending);
        if (queue.isEmpty()) {
            pendingSettlements.remove(pending.playerId(), queue);
        }
    }

    public void clear() {
        sessions.clear();
    }
}
