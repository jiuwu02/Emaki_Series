package emaki.jiuwu.craft.storage.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import emaki.jiuwu.craft.corelib.session.SessionData;

public final class PlayerStorage implements SessionData<PlayerStorage> {

    private final UUID playerId;
    private final Map<StorageKey, StorageEntry> entries = new HashMap<>();
    private final List<StorageKey> entryOrder = new ArrayList<>();
    private final Map<UUID, StorageReservation> reservations = new LinkedHashMap<>();

    private String playerName = "";
    private int grantedSlots;
    private int purchasedSlots;
    private long defaultStackLimit;
    private SortMode sortMode = SortMode.AMOUNT_DESC;
    private boolean autoPickupEnabled;
    private long nextTemplateId = 1L;

    private long revision;
    private long persistedRevision;

    public PlayerStorage(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID playerId() {
        return playerId;
    }

    public String playerName() {
        return playerName;
    }

    public void playerName(String playerName) {
        this.playerName = playerName == null ? "" : playerName;
    }

    public int grantedSlots() {
        return grantedSlots;
    }

    public void grantedSlots(int grantedSlots) {
        this.grantedSlots = grantedSlots;
    }

    public int purchasedSlots() {
        return Math.max(0, purchasedSlots);
    }

    public void purchasedSlots(int purchasedSlots) {
        this.purchasedSlots = Math.max(0, purchasedSlots);
    }

    public long defaultStackLimit() {
        return defaultStackLimit;
    }

    public void defaultStackLimit(long defaultStackLimit) {
        this.defaultStackLimit = Math.max(0L, defaultStackLimit);
    }

    public SortMode sortMode() {
        return sortMode;
    }

    public void sortMode(SortMode sortMode) {
        if (sortMode != null) {
            this.sortMode = sortMode;
        }
    }

    public boolean autoPickupEnabled() {
        return autoPickupEnabled;
    }

    public void autoPickupEnabled(boolean autoPickupEnabled) {
        this.autoPickupEnabled = autoPickupEnabled;
    }

    public long nextTemplateId() {
        return nextTemplateId;
    }

    public void nextTemplateId(long nextTemplateId) {
        this.nextTemplateId = Math.max(1L, nextTemplateId);
    }

    public long allocateTemplateId() {
        long allocated = nextTemplateId;
        nextTemplateId = allocated + 1L;
        return allocated;
    }

    public Map<StorageKey, StorageEntry> entries() {
        return Collections.unmodifiableMap(entries);
    }

    public List<StorageKey> entryOrder() {
        return Collections.unmodifiableList(entryOrder);
    }

    public int entryCount() {
        return entryOrder.size();
    }

    public StorageEntry entry(StorageKey key) {
        return key == null ? null : entries.get(key);
    }

    public StorageEntry entryAt(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= entryOrder.size()) {
            return null;
        }
        return entries.get(entryOrder.get(slotIndex));
    }

    public int indexOf(StorageKey key) {
        return key == null ? -1 : entryOrder.indexOf(key);
    }

    public int append(StorageEntry entry) {
        if (entry == null) {
            return -1;
        }
        StorageEntry existing = entries.get(entry.key());
        if (existing != null) {
            return entryOrder.indexOf(entry.key());
        }
        entries.put(entry.key(), entry);
        entryOrder.add(entry.key());
        return entryOrder.size() - 1;
    }

    public boolean remove(StorageKey key) {
        if (key == null || entries.remove(key) == null) {
            return false;
        }
        entryOrder.remove(key);
        return true;
    }

    public int pruneEmpty() {
        int removed = 0;
        for (int index = entryOrder.size() - 1; index >= 0; index--) {
            StorageKey key = entryOrder.get(index);
            StorageEntry entry = entries.get(key);
            if (entry == null || entry.empty()) {
                entries.remove(key);
                entryOrder.remove(index);
                removed++;
            }
        }
        return removed;
    }

    public void reorder(List<StorageKey> order) {
        if (order == null || order.isEmpty()) {
            return;
        }
        List<StorageKey> rebuilt = new ArrayList<>(entryOrder.size());
        Map<StorageKey, Boolean> seen = new LinkedHashMap<>();
        for (StorageKey key : order) {
            if (entries.containsKey(key) && seen.putIfAbsent(key, Boolean.TRUE) == null) {
                rebuilt.add(key);
            }
        }
        for (StorageKey key : entryOrder) {
            if (seen.putIfAbsent(key, Boolean.TRUE) == null) {
                rebuilt.add(key);
            }
        }
        entryOrder.clear();
        entryOrder.addAll(rebuilt);
    }

    @Override
    public long revision() {
        return revision;
    }

    public Map<UUID, StorageReservation> reservations() {
        return Collections.unmodifiableMap(reservations);
    }

    public void addReservation(StorageReservation reservation) {
        if (reservation != null && reservation.reservationId() != null) {
            reservations.put(reservation.reservationId(), reservation);
        }
    }

    public StorageReservation removeReservation(UUID reservationId) {
        return reservationId == null ? null : reservations.remove(reservationId);
    }

    public long reservedAmount(StorageKey key) {
        if (key == null || reservations.isEmpty()) {
            return 0L;
        }
        long reserved = 0L;
        for (StorageReservation reservation : reservations.values()) {
            reserved += reservation.heldAmount(key);
        }
        return reserved;
    }

    public int pruneExpiredReservations(long nowMillis) {
        if (reservations.isEmpty()) {
            return 0;
        }
        List<UUID> expired = new ArrayList<>();
        for (StorageReservation reservation : reservations.values()) {
            if (reservation.expired(nowMillis)) {
                expired.add(reservation.reservationId());
            }
        }
        expired.forEach(reservations::remove);
        return expired.size();
    }

    @Override
    public long persistedRevision() {
        return persistedRevision;
    }

    @Override
    public boolean dirty() {
        return revision > persistedRevision;
    }

    @Override
    public void markDirty() {
        revision++;
    }

    @Override
    public void markPersisted(long revision) {
        this.persistedRevision = Math.max(this.persistedRevision, revision);
    }

    @Override
    public void clearDirty() {
        persistedRevision = revision;
    }

    @Override
    public PlayerStorage copy() {
        PlayerStorage copy = new PlayerStorage(playerId);
        copy.playerName = playerName;
        copy.grantedSlots = grantedSlots;
        copy.purchasedSlots = purchasedSlots;
        copy.defaultStackLimit = defaultStackLimit;
        copy.sortMode = sortMode;
        copy.autoPickupEnabled = autoPickupEnabled;
        copy.nextTemplateId = nextTemplateId;
        copy.revision = revision;
        copy.persistedRevision = persistedRevision;
        for (StorageKey key : entryOrder) {
            StorageEntry entry = entries.get(key);
            if (entry == null) {
                continue;
            }
            copy.append(new StorageEntry(key, entry.amount(), entry.stackLimit(),
                    entry.searchText(), entry.sortName()));
        }
        copy.reservations.putAll(reservations);
        return copy;
    }
}
