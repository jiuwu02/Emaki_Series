package emaki.jiuwu.craft.cooking.service;

import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.cooking.service.CookingCompletionOperation.Semantics;
import emaki.jiuwu.craft.cooking.service.CookingCompletionOperation.Status;
import emaki.jiuwu.craft.cooking.service.CookingCompletionOperation.Unit;
import emaki.jiuwu.craft.cooking.service.CookingCompletionOperation.UnitState;

public final class CookingCompletionRecoveryPlanner {

    public enum NextStep {
        COMMIT_INPUTS,
        MARK_INPUT_COMMITTED,
        DELIVER_NEXT,
        MARK_COMPLETED,
        ARCHIVE,
        QUARANTINE,
        NONE
    }

    public NextStep nextStep(CookingCompletionOperation operation) {
        if (operation == null) {
            return NextStep.NONE;
        }
        return switch (operation.status()) {
            case PREPARED -> preparedStep(operation);
            case INPUT_COMMITTED, DELIVERING -> deliveryStep(operation);
            case COMPLETED -> NextStep.ARCHIVE;
            case QUARANTINED -> NextStep.QUARANTINE;
        };
    }

    public CookingCompletionOperation normalizeForRecovery(CookingCompletionOperation operation) {
        if (operation == null || operation.isTerminal()) {
            return operation;
        }
        List<Unit> inputs = normalizeUnits(operation.inputUnits());
        List<Unit> deliveries = normalizeUnits(operation.deliveryUnits());
        return operation.withUnitsForRecovery(inputs, deliveries);
    }

    private NextStep preparedStep(CookingCompletionOperation operation) {
        if (operation.allInputsCompleted()) {
            return NextStep.MARK_INPUT_COMMITTED;
        }
        return operation.nextPendingInput().isPresent()
                ? NextStep.COMMIT_INPUTS
                : NextStep.NONE;
    }

    private NextStep deliveryStep(CookingCompletionOperation operation) {
        if (!operation.allInputsCompleted()) {
            return NextStep.QUARANTINE;
        }
        if (operation.allDeliveriesCompleted()) {
            return NextStep.MARK_COMPLETED;
        }
        return operation.nextPendingDelivery().isPresent()
                ? NextStep.DELIVER_NEXT
                : NextStep.NONE;
    }

    private List<Unit> normalizeUnits(List<Unit> units) {
        List<Unit> normalized = new ArrayList<>(units.size());
        for (Unit unit : units) {
            if (unit.state() != UnitState.IN_PROGRESS) {
                normalized.add(unit);
            } else if (unit.semantics() == Semantics.AT_MOST_ONCE_AFTER_DURABLE_INTENT) {
                normalized.add(unit.complete());
            } else {
                normalized.add(unit.pendingForRecovery());
            }
        }
        return List.copyOf(normalized);
    }
}
