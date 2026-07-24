package emaki.jiuwu.craft.item.service;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

import emaki.jiuwu.craft.item.listener.InventoryRefreshClassifier;
import emaki.jiuwu.craft.item.model.RefreshFullReason;
import emaki.jiuwu.craft.item.model.RefreshScope;

public final class ItemRefreshMetrics {

    private final LongAdder events = new LongAdder();
    private final LongAdder skippedEvents = new LongAdder();
    private final LongAdder batches = new LongAdder();
    private final LongAdder rejectedBatches = new LongAdder();
    private final LongAdder coalesced = new LongAdder();
    private final LongAdder requestedLocal = new LongAdder();
    private final LongAdder requestedFull = new LongAdder();
    private final LongAdder actualUpdateLocal = new LongAdder();
    private final LongAdder actualUpdateFull = new LongAdder();
    private final LongAdder actualSetLocal = new LongAdder();
    private final LongAdder actualSetFull = new LongAdder();
    private final LongAdder cacheHits = new LongAdder();
    private final LongAdder cacheInvalid = new LongAdder();
    private final LongAdder updateScannedSlots = new LongAdder();
    private final LongAdder setScannedSlots = new LongAdder();
    private final LongAdder scannedSlots = new LongAdder();
    private final LongAdder changed = new LongAdder();
    private final LongAdder conflicts = new LongAdder();
    private final LongAdder ledgerDecodes = new LongAdder();
    private final LongAdder setCompiles = new LongAdder();
    private final LongAdder elapsedNanos = new LongAdder();
    private final EnumMap<RefreshFullReason, LongAdder> fullReasons = new EnumMap<>(RefreshFullReason.class);

    public ItemRefreshMetrics() {
        for (RefreshFullReason reason : RefreshFullReason.values()) {
            fullReasons.put(reason, new LongAdder());
        }
    }

    public void recordEvent(InventoryRefreshClassifier.Result result) {
        events.increment();
        if (result == null || result.scope() == RefreshScope.SKIP) {
            skippedEvents.increment();
        }
    }

    public void recordBatchCreated() {
        batches.increment();
    }

    public void recordBatchRejected() {
        rejectedBatches.increment();
    }

    public void recordCoalesced() {
        coalesced.increment();
    }

    public void recordResult(ItemRefreshResult result) {
        if (result == null) {
            return;
        }
        incrementScope(result.requestedScope(), requestedLocal, requestedFull);
        incrementScope(result.actualUpdateScope(), actualUpdateLocal, actualUpdateFull);
        incrementScope(result.actualSetScope(), actualSetLocal, actualSetFull);
        if (result.cacheHit()) {
            cacheHits.increment();
        }
        if (!result.cacheValid()) {
            cacheInvalid.increment();
        }
        updateScannedSlots.add(result.updateScannedSlots());
        setScannedSlots.add(result.setScannedSlots());
        scannedSlots.add(result.scannedSlots());
        changed.add(result.changed());
        conflicts.add(result.conflicts());
        ledgerDecodes.add(result.ledgerDecodes());
        setCompiles.add(result.setCompiles());
        elapsedNanos.add(result.elapsedNanos());
        for (RefreshFullReason reason : result.fullReasons()) {
            LongAdder counter = fullReasons.get(reason);
            if (counter != null) {
                counter.increment();
            }
        }
    }

    public Snapshot snapshot() {
        EnumMap<RefreshFullReason, Long> reasons = new EnumMap<>(RefreshFullReason.class);
        fullReasons.forEach((reason, counter) -> {
            long value = counter.sum();
            if (value > 0L) {
                reasons.put(reason, value);
            }
        });
        return new Snapshot(
                events.sum(), skippedEvents.sum(), batches.sum(), rejectedBatches.sum(), coalesced.sum(),
                requestedLocal.sum(), requestedFull.sum(), actualUpdateLocal.sum(), actualUpdateFull.sum(),
                actualSetLocal.sum(), actualSetFull.sum(), cacheHits.sum(), cacheInvalid.sum(),
                updateScannedSlots.sum(), setScannedSlots.sum(), scannedSlots.sum(),
                changed.sum(), conflicts.sum(), ledgerDecodes.sum(), setCompiles.sum(),
                elapsedNanos.sum(), reasons
        );
    }

    private void incrementScope(RefreshScope scope, LongAdder local, LongAdder full) {
        if (scope == RefreshScope.LOCAL) {
            local.increment();
        } else if (scope == RefreshScope.FULL) {
            full.increment();
        }
    }

    public record Snapshot(
            long events,
            long skippedEvents,
            long batches,
            long rejectedBatches,
            long coalesced,
            long requestedLocal,
            long requestedFull,
            long actualUpdateLocal,
            long actualUpdateFull,
            long actualSetLocal,
            long actualSetFull,
            long cacheHits,
            long cacheInvalid,
            long updateScannedSlots,
            long setScannedSlots,
            long scannedSlots,
            long changed,
            long conflicts,
            long ledgerDecodes,
            long setCompiles,
            long elapsedNanos,
            Map<RefreshFullReason, Long> fullReasons) {

        public Snapshot {
            fullReasons = fullReasons == null || fullReasons.isEmpty() ? Map.of() : Map.copyOf(fullReasons);
        }

        public long elapsedMillis() {
            return elapsedNanos / 1_000_000L;
        }
    }
}
