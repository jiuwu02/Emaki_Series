package emaki.jiuwu.craft.dungeon.orchestration;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public final class InstanceSlotAllocator {

    private final int globalMax;
    private final boolean folia;
    private final int heldWorldsMax;
    private final Function<String, Integer> poolMaxOf;

    private final AtomicInteger globalWorlds = new AtomicInteger();
    private final AtomicInteger globalHeld = new AtomicInteger();
    private final Map<String, AtomicInteger> dungeonWorlds = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> dungeonHeld = new ConcurrentHashMap<>();
    private final Set<UUID> busyParties = ConcurrentHashMap.newKeySet();

    public InstanceSlotAllocator(int globalMax,
            boolean folia,
            int heldWorldsMax,
            Function<String, Integer> poolMaxOf) {
        this.globalMax = globalMax;
        this.folia = folia;
        this.heldWorldsMax = heldWorldsMax;
        this.poolMaxOf = poolMaxOf;
    }

    public synchronized PoolReservation tryReservePool(String dungeonId, String worldName) {
        if (globalWorlds.get() >= globalMax) {
            return null;
        }
        if (worldCounter(dungeonId).get() >= poolMax(dungeonId)) {
            return null;
        }
        globalWorlds.incrementAndGet();
        worldCounter(dungeonId).incrementAndGet();
        return new PoolReservation(dungeonId, worldName);
    }

    public synchronized boolean confirmPool(PoolReservation reservation) {
        return reservation.markSettled();
    }

    public synchronized boolean cancelPool(PoolReservation reservation) {
        if (!reservation.markSettled()) {
            return false;
        }
        globalWorlds.decrementAndGet();
        worldCounter(reservation.dungeonId()).decrementAndGet();
        return true;
    }

    public synchronized void releaseWorld(String dungeonId) {
        globalWorlds.decrementAndGet();
        worldCounter(dungeonId).decrementAndGet();
    }

    public synchronized void markHeld(String dungeonId) {
        globalHeld.incrementAndGet();
        heldCounter(dungeonId).incrementAndGet();
    }

    public synchronized DenyReason tryAcquireParty(String dungeonId, UUID partyId, boolean reserveAvailable) {
        if (!busyParties.add(partyId)) {
            return DenyReason.DENY_PARTY_BUSY;
        }
        boolean globalOk = globalAvailable();
        boolean poolOk = reserveAvailable || worldCounter(dungeonId).get() < poolMax(dungeonId);
        if (globalOk && poolOk) {
            return null;
        }
        busyParties.remove(partyId);
        if (!globalOk) {
            return DenyReason.DENY_GLOBAL_FULL;
        }
        return DenyReason.DENY_DUNGEON_FULL;
    }

    public synchronized void releaseParty(UUID partyId) {
        busyParties.remove(partyId);
    }

    public boolean isPartyBusy(UUID partyId) {
        return partyId != null && busyParties.contains(partyId);
    }

    public synchronized void reset() {
        globalWorlds.set(0);
        globalHeld.set(0);
        dungeonWorlds.clear();
        dungeonHeld.clear();
        busyParties.clear();
    }

    public int globalWorlds() {
        return Math.max(0, globalWorlds.get());
    }

    public int globalHeldWorlds() {
        return Math.max(0, globalHeld.get());
    }

    public int worldsOf(String dungeonId) {
        AtomicInteger counter = dungeonWorlds.get(dungeonId);
        return counter == null ? 0 : Math.max(0, counter.get());
    }

    public int heldOf(String dungeonId) {
        AtomicInteger counter = dungeonHeld.get(dungeonId);
        return counter == null ? 0 : Math.max(0, counter.get());
    }

    private boolean globalAvailable() {
        if (folia && globalHeld.get() >= heldWorldsMax) {
            return false;
        }
        return globalWorlds.get() < globalMax;
    }

    private int poolMax(String dungeonId) {
        Integer poolMax = poolMaxOf.apply(dungeonId);
        return poolMax == null || poolMax < 1 ? 1 : poolMax;
    }

    private AtomicInteger worldCounter(String dungeonId) {
        return dungeonWorlds.computeIfAbsent(dungeonId, key -> new AtomicInteger());
    }

    private AtomicInteger heldCounter(String dungeonId) {
        return dungeonHeld.computeIfAbsent(dungeonId, key -> new AtomicInteger());
    }
}
