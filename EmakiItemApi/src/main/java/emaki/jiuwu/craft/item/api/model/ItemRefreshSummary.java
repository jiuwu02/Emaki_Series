package emaki.jiuwu.craft.item.api.model;

import java.util.List;

import org.jetbrains.annotations.NotNull;

/**
 * Actual work performed by one runtime inventory or set refresh.
 *
 * @param changedItems number of slots successfully changed
 * @param conflicts compare-before-write conflicts that were intentionally not overwritten
 * @param updateScannedSlots slots scanned by definition refresh
 * @param setScannedSlots slots scanned by set refresh
 * @param ledgerDecodes operation-ledger decodes performed
 * @param setCompiles set definitions compiled
 * @param cacheHit whether cached set state contributed
 * @param cacheValid whether the resulting cache remained valid
 * @param requestedScope runtime requested scope name
 * @param updateScope actual definition-update scope name
 * @param setScope actual set-refresh scope name
 * @param fullReasons runtime reasons that widened the refresh to full scope
 * @param effectiveTrigger trigger actually accepted by the runtime
 * @param elapsedNanos measured runtime duration
 */
public record ItemRefreshSummary(int changedItems,
                                 int conflicts,
                                 int updateScannedSlots,
                                 int setScannedSlots,
                                 int ledgerDecodes,
                                 int setCompiles,
                                 boolean cacheHit,
                                 boolean cacheValid,
                                 @NotNull String requestedScope,
                                 @NotNull String updateScope,
                                 @NotNull String setScope,
                                 @NotNull List<String> fullReasons,
                                 @NotNull String effectiveTrigger,
                                 long elapsedNanos) {

    public ItemRefreshSummary {
        changedItems = Math.max(0, changedItems);
        conflicts = Math.max(0, conflicts);
        updateScannedSlots = Math.max(0, updateScannedSlots);
        setScannedSlots = Math.max(0, setScannedSlots);
        ledgerDecodes = Math.max(0, ledgerDecodes);
        setCompiles = Math.max(0, setCompiles);
        requestedScope = requestedScope == null ? "SKIP" : requestedScope;
        updateScope = updateScope == null ? "SKIP" : updateScope;
        setScope = setScope == null ? "SKIP" : setScope;
        fullReasons = fullReasons == null ? List.of() : List.copyOf(fullReasons);
        effectiveTrigger = effectiveTrigger == null ? "" : effectiveTrigger;
        elapsedNanos = Math.max(0L, elapsedNanos);
    }

    /** {@return total slots scanned by both runtime layers} */
    public int scannedSlots() {
        return updateScannedSlots + setScannedSlots;
    }

    /** {@return whether every compare-before-write commit completed} */
    public boolean complete() {
        return conflicts == 0;
    }
}
