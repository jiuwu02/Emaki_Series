package emaki.jiuwu.craft.cooking.service;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;

public record CookingCompletionOperation(
        String operationId,
        String completionKey,
        Status status,
        StationType stationType,
        StationCoordinates stationCoordinates,
        Map<String, Object> expectedState,
        String expectedStateDigest,
        CommitMode commitMode,
        Map<String, Object> committedState,
        String committedStateDigest,
        List<Unit> inputUnits,
        List<Unit> deliveryUnits,
        long createdAtMs,
        long updatedAtMs,
        String lastError) {

    public enum Status {
        PREPARED,
        INPUT_COMMITTED,
        DELIVERING,
        COMPLETED,
        QUARANTINED
    }

    public enum CommitMode {
        SAVE,
        DELETE
    }

    public enum UnitKind {
        PLAYER_INVENTORY_INPUT,
        ITEM_REWARD,
        ACTION
    }

    public enum UnitState {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED_RETRYABLE
    }

    public enum Semantics {
        AT_MOST_ONCE_AFTER_DURABLE_INTENT,
        AT_LEAST_ONCE
    }

    public CookingCompletionOperation {
        operationId = requireText(operationId, "operationId");
        completionKey = requireText(completionKey, "completionKey");
        status = Objects.requireNonNull(status, "status");
        stationType = Objects.requireNonNull(stationType, "stationType");
        stationCoordinates = Objects.requireNonNull(stationCoordinates, "stationCoordinates");
        expectedState = immutableMap(expectedState);
        expectedStateDigest = normalizedDigest(expectedStateDigest, expectedState);
        commitMode = Objects.requireNonNull(commitMode, "commitMode");
        committedState = immutableMap(committedState);
        committedStateDigest = normalizedDigest(committedStateDigest, committedState);
        inputUnits = immutableUnits(inputUnits);
        deliveryUnits = immutableUnits(deliveryUnits);
        createdAtMs = Math.max(0L, createdAtMs);
        updatedAtMs = Math.max(createdAtMs, updatedAtMs);
        lastError = normalizeError(lastError);
    }

    public StationCoordinates coordinates() {
        return stationCoordinates;
    }

    public boolean isTerminal() {
        return status == Status.COMPLETED || status == Status.QUARANTINED;
    }

    public boolean allInputsCompleted() {
        return inputUnits.stream().allMatch(Unit::isCompleted);
    }

    public boolean allDeliveriesCompleted() {
        return deliveryUnits.stream().allMatch(Unit::isCompleted);
    }

    public Optional<Unit> nextPendingInput() {
        return nextPending(inputUnits);
    }

    public Optional<Unit> nextPendingDelivery() {
        return nextPending(deliveryUnits);
    }

    public CookingCompletionOperation withStatus(Status nextStatus) {
        return copy(nextStatus, inputUnits, deliveryUnits, lastError, nextUpdatedAt());
    }

    public CookingCompletionOperation withInputUnit(Unit unit) {
        return copy(status, replaceUnit(inputUnits, unit), deliveryUnits, lastError, nextUpdatedAt());
    }

    public CookingCompletionOperation withDeliveryUnit(Unit unit) {
        return copy(status, inputUnits, replaceUnit(deliveryUnits, unit), lastError, nextUpdatedAt());
    }

    public CookingCompletionOperation withError(String error) {
        return copy(status, inputUnits, deliveryUnits, error, nextUpdatedAt());
    }

    public CookingCompletionOperation withUnits(List<Unit> inputs, List<Unit> deliveries) {
        return copy(status, inputs, deliveries, lastError, nextUpdatedAt());
    }

    CookingCompletionOperation withUnitsForRecovery(List<Unit> inputs, List<Unit> deliveries) {
        if (inputUnits.equals(inputs) && deliveryUnits.equals(deliveries)) {
            return this;
        }
        return copy(status, inputs, deliveries, lastError, nextUpdatedAt());
    }

    private CookingCompletionOperation copy(
            Status nextStatus,
            List<Unit> nextInputs,
            List<Unit> nextDeliveries,
            String nextError,
            long nextUpdatedAtMs) {
        return new CookingCompletionOperation(
                operationId,
                completionKey,
                nextStatus,
                stationType,
                stationCoordinates,
                expectedState,
                expectedStateDigest,
                commitMode,
                committedState,
                committedStateDigest,
                nextInputs,
                nextDeliveries,
                createdAtMs,
                nextUpdatedAtMs,
                nextError
        );
    }

    private long nextUpdatedAt() {
        return Math.max(System.currentTimeMillis(), updatedAtMs + 1L);
    }

    private static Optional<Unit> nextPending(List<Unit> units) {
        return units.stream()
                .filter(Unit::isPendingAttempt)
                .findFirst();
    }

    private static List<Unit> replaceUnit(List<Unit> units, Unit replacement) {
        Objects.requireNonNull(replacement, "unit");
        List<Unit> updated = new ArrayList<>(units.size());
        boolean replaced = false;
        for (Unit unit : units) {
            if (unit.unitId().equals(replacement.unitId())) {
                if (replaced) {
                    throw new IllegalStateException("Duplicate unit id: " + replacement.unitId());
                }
                updated.add(replacement);
                replaced = true;
            } else {
                updated.add(unit);
            }
        }
        if (!replaced) {
            throw new IllegalArgumentException("Unknown unit id: " + replacement.unitId());
        }
        return List.copyOf(updated);
    }

    private static List<Unit> immutableUnits(List<Unit> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<Unit> copy = List.copyOf(source);
        long distinctIds = copy.stream().map(Unit::unitId).distinct().count();
        if (distinctIds != copy.size()) {
            throw new IllegalArgumentException("Unit ids must be unique within a unit list");
        }
        return copy;
    }

    private static String normalizedDigest(String digest, Map<String, Object> state) {
        return digest == null || digest.isBlank()
                ? CookingCompletionStateDigest.digest(state)
                : digest.trim().toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeError(String value) {
        return value == null ? "" : value;
    }

    private static Map<String, Object> immutableMap(Map<?, ?> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() != null) {
                copy.put(String.valueOf(entry.getKey()), immutableValue(entry.getValue()));
            }
        }
        return copy.isEmpty() ? Map.of() : Collections.unmodifiableMap(copy);
    }

    private static Object immutableValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return immutableMap(map);
        }
        if (value instanceof Collection<?> collection) {
            List<Object> copy = new ArrayList<>(collection.size());
            for (Object entry : collection) {
                copy.add(immutableValue(entry));
            }
            return Collections.unmodifiableList(copy);
        }
        if (value != null && value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> copy = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                copy.add(immutableValue(Array.get(value, index)));
            }
            return Collections.unmodifiableList(copy);
        }
        return value;
    }

    public record Unit(
            String unitId,
            UnitKind kind,
            UnitState state,
            Semantics semantics,
            Map<String, Object> payload,
            int attempts,
            String lastError) {

        public Unit {
            unitId = requireText(unitId, "unitId");
            kind = Objects.requireNonNull(kind, "kind");
            state = Objects.requireNonNull(state, "state");
            semantics = Objects.requireNonNull(semantics, "semantics");
            payload = immutableMap(payload);
            attempts = Math.max(0, attempts);
            lastError = normalizeError(lastError);
        }

        public boolean isCompleted() {
            return state == UnitState.COMPLETED;
        }

        public boolean isPendingAttempt() {
            return state == UnitState.PENDING || state == UnitState.FAILED_RETRYABLE;
        }

        public Unit beginAttempt() {
            if (state == UnitState.COMPLETED) {
                return this;
            }
            if (state == UnitState.IN_PROGRESS) {
                throw new IllegalStateException("Unit is already in progress: " + unitId);
            }
            return new Unit(unitId, kind, UnitState.IN_PROGRESS, semantics, payload, attempts + 1, "");
        }

        public Unit complete() {
            if (state == UnitState.COMPLETED) {
                return this;
            }
            return new Unit(unitId, kind, UnitState.COMPLETED, semantics, payload, attempts, "");
        }

        public Unit fail(String error) {
            if (state == UnitState.COMPLETED) {
                return this;
            }
            return new Unit(unitId, kind, UnitState.FAILED_RETRYABLE, semantics, payload, attempts, error);
        }

        Unit pendingForRecovery() {
            if (state != UnitState.IN_PROGRESS) {
                return this;
            }
            return new Unit(unitId, kind, UnitState.PENDING, semantics, payload, attempts, lastError);
        }
    }
}
