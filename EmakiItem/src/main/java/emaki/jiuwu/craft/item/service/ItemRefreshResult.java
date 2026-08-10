package emaki.jiuwu.craft.item.service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.item.model.RefreshFullReason;
import emaki.jiuwu.craft.item.model.RefreshScope;

public record ItemRefreshResult(
        RefreshScope requestedScope,
        RefreshScope actualUpdateScope,
        RefreshScope actualSetScope,
        Set<RefreshFullReason> fullReasons,
        boolean cacheHit,
        boolean cacheValid,
        int updateScannedSlots,
        int setScannedSlots,
        int changed,
        int conflicts,
        int ledgerDecodes,
        int setCompiles,
        String effectiveTrigger,
        long elapsedNanos) {

    public ItemRefreshResult {
        requestedScope = requestedScope == null ? RefreshScope.SKIP : requestedScope;
        actualUpdateScope = actualUpdateScope == null ? RefreshScope.SKIP : actualUpdateScope;
        actualSetScope = actualSetScope == null ? RefreshScope.SKIP : actualSetScope;
        fullReasons = fullReasons == null || fullReasons.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(fullReasons));
        updateScannedSlots = Math.max(0, updateScannedSlots);
        setScannedSlots = Math.max(0, setScannedSlots);
        changed = Math.max(0, changed);
        conflicts = Math.max(0, conflicts);
        ledgerDecodes = Math.max(0, ledgerDecodes);
        setCompiles = Math.max(0, setCompiles);
        effectiveTrigger = Texts.toStringSafe(effectiveTrigger);
        elapsedNanos = Math.max(0L, elapsedNanos);
    }

    public static ItemRefreshResult empty(RefreshScope requestedScope, Set<RefreshFullReason> fullReasons) {
        return new ItemRefreshResult(requestedScope, RefreshScope.SKIP, RefreshScope.SKIP, fullReasons,
                false, false, 0, 0, 0, 0, 0, 0, "", 0L);
    }

    public int scannedSlots() {
        return updateScannedSlots + setScannedSlots;
    }

    public ItemRefreshResult combine(ItemRefreshResult other) {
        if (other == null) {
            return this;
        }
        LinkedHashSet<RefreshFullReason> reasons = new LinkedHashSet<>(fullReasons);
        reasons.addAll(other.fullReasons);
        return new ItemRefreshResult(
                broader(requestedScope, other.requestedScope),
                broader(actualUpdateScope, other.actualUpdateScope),
                broader(actualSetScope, other.actualSetScope),
                reasons,
                cacheHit || other.cacheHit,
                cacheValid && other.cacheValid,
                updateScannedSlots + other.updateScannedSlots,
                setScannedSlots + other.setScannedSlots,
                changed + other.changed,
                conflicts + other.conflicts,
                ledgerDecodes + other.ledgerDecodes,
                setCompiles + other.setCompiles,
                Texts.isNotBlank(effectiveTrigger) ? effectiveTrigger : other.effectiveTrigger,
                elapsedNanos + other.elapsedNanos
        );
    }

    private static RefreshScope broader(RefreshScope first, RefreshScope second) {
        return first.ordinal() >= second.ordinal() ? first : second;
    }
}
