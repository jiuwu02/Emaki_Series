package emaki.jiuwu.craft.item.service;

import java.util.LinkedHashSet;
import java.util.Set;

import emaki.jiuwu.craft.item.model.RefreshFullReason;
import emaki.jiuwu.craft.item.model.RefreshScope;

public final class ItemSetRefreshPlanner {

    public Decision decideInitial(Request request, CacheView cache) {
        LinkedHashSet<RefreshFullReason> reasons = new LinkedHashSet<>(request.fullReasons());
        if (request.forceFull()) {
            if (reasons.isEmpty()) {
                reasons.add(RefreshFullReason.EXPLICIT_FULL);
            }
            return Decision.full(reasons, cache != null, cache != null && cache.valid());
        }
        if (cache == null) {
            reasons.add(RefreshFullReason.CACHE_MISS);
            return Decision.full(reasons, false, false);
        }
        if (!cache.valid()) {
            reasons.add(RefreshFullReason.CACHE_INVALID);
            return Decision.full(reasons, true, false);
        }
        if (cache.itemGeneration() != request.itemGeneration()) {
            reasons.add(RefreshFullReason.ITEM_GENERATION_CHANGED);
            return Decision.full(reasons, true, true);
        }
        if (cache.setGeneration() != request.setGeneration()) {
            reasons.add(RefreshFullReason.SET_GENERATION_CHANGED);
            return Decision.full(reasons, true, true);
        }
        if (!request.dirtyScopeComplete()) {
            reasons.add(RefreshFullReason.DIRTY_SCOPE_INCOMPLETE);
            return Decision.full(reasons, true, true);
        }
        return new Decision(RefreshScope.LOCAL, reasons, true, true);
    }

    public Decision decideContribution(Decision initial, String cachedSignature, String currentSignature) {
        if (initial == null || initial.scope() != RefreshScope.LOCAL) {
            return initial;
        }
        if (!java.util.Objects.equals(cachedSignature, currentSignature)) {
            LinkedHashSet<RefreshFullReason> reasons = new LinkedHashSet<>(initial.fullReasons());
            reasons.add(RefreshFullReason.CONTRIBUTION_SIGNATURE_CHANGED);
            return Decision.full(reasons, initial.cacheHit(), initial.cacheValid());
        }
        return initial;
    }

    public SlotAction planSlot(boolean itemIdentityPresent,
            boolean itemDefinitionPresent,
            boolean membershipConfigured,
            boolean setStatePresent,
            boolean hasSetPresentation) {
        if (itemIdentityPresent && !itemDefinitionPresent) {
            return hasSetPresentation ? SlotAction.PRESERVE_MISSING_DEFINITION : SlotAction.NO_OP;
        }
        if (!itemDefinitionPresent) {
            return hasSetPresentation ? SlotAction.PRESERVE_MISSING_DEFINITION : SlotAction.NO_OP;
        }
        if (membershipConfigured) {
            return setStatePresent ? SlotAction.APPLY : SlotAction.PRESERVE_MISSING_DEFINITION;
        }
        return hasSetPresentation ? SlotAction.CLEAR : SlotAction.NO_OP;
    }

    public enum SlotAction {
        NO_OP,
        PRESERVE_MISSING_DEFINITION,
        APPLY,
        CLEAR
    }

    public record Request(
            boolean forceFull,
            boolean dirtyScopeComplete,
            long itemGeneration,
            long setGeneration,
            Set<RefreshFullReason> fullReasons) {

        public Request {
            fullReasons = fullReasons == null || fullReasons.isEmpty()
                    ? Set.of()
                    : java.util.Collections.unmodifiableSet(new LinkedHashSet<>(fullReasons));
        }
    }

    public record CacheView(boolean valid, long itemGeneration, long setGeneration) {
    }

    public record Decision(
            RefreshScope scope,
            Set<RefreshFullReason> fullReasons,
            boolean cacheHit,
            boolean cacheValid) {

        public Decision {
            scope = scope == null ? RefreshScope.FULL : scope;
            fullReasons = fullReasons == null || fullReasons.isEmpty()
                    ? Set.of()
                    : java.util.Collections.unmodifiableSet(new LinkedHashSet<>(fullReasons));
        }

        private static Decision full(Set<RefreshFullReason> reasons, boolean cacheHit, boolean cacheValid) {
            return new Decision(RefreshScope.FULL, reasons, cacheHit, cacheValid);
        }
    }
}
