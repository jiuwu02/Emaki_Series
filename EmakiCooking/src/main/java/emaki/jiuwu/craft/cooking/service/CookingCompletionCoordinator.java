package emaki.jiuwu.craft.cooking.service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.async.AsyncFileService.DrainResult;
import emaki.jiuwu.craft.corelib.async.AsyncFileService.FileScope;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.TaskHandle;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.MapYamlSection;
import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.cooking.service.CookingCompletionOperation.Semantics;
import emaki.jiuwu.craft.cooking.service.CookingCompletionOperation.Status;
import emaki.jiuwu.craft.cooking.service.CookingCompletionOperation.Unit;
import emaki.jiuwu.craft.cooking.service.CookingCompletionOperation.UnitKind;
import emaki.jiuwu.craft.cooking.service.CookingCompletionOperation.UnitState;
import emaki.jiuwu.craft.cooking.service.CookingCompletionRecoveryPlanner.NextStep;

/** Coordinates durable station commit and idempotent/retryable completion delivery. */
public final class CookingCompletionCoordinator {

    private static final long RETRY_DELAY_TICKS = 20L;

    private final JavaPlugin plugin;
    private final CookingRewardService rewardService;
    private final CookingCompletionJournalStore journalStore;
    private final CookingDeliveryLedgerStore deliveryLedger;
    private final Logger logger;
    private final RetryScheduler retryScheduler;
    private final FrozenRewardExecutor frozenRewardExecutor;
    private final ExecutionDispatcher executionDispatcher;
    private final CookingCompletionRecoveryPlanner recoveryPlanner = new CookingCompletionRecoveryPlanner();
    private final Map<StationType, CookingStationStateAccess> stateAccesses = new EnumMap<>(StationType.class);
    private final ConcurrentMap<String, CookingCompletionOperation> operations = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> activeByStation = new ConcurrentHashMap<>();
    private final Set<String> advancing = ConcurrentHashMap.newKeySet();
    private final Set<String> retryScheduled = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    public CookingCompletionCoordinator(JavaPlugin plugin,
            CookingRewardService rewardService,
            FileScope fileScope,
            ExecutionDispatcher executionDispatcher) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.rewardService = Objects.requireNonNull(rewardService, "rewardService");
        this.journalStore = new CookingCompletionJournalStore(plugin, fileScope);
        this.deliveryLedger = new CookingDeliveryLedgerStore(plugin, fileScope);
        this.logger = plugin.getLogger();
        ExecutionDispatcher dispatcher = Objects.requireNonNull(executionDispatcher, "executionDispatcher");
        this.executionDispatcher = dispatcher;
        this.retryScheduler = (task, delayTicks) -> dispatcher.runGlobalLater(this.plugin, task, delayTicks);
        this.frozenRewardExecutor = this.rewardService::executeFrozen;
    }

    CookingCompletionCoordinator(CookingCompletionJournalStore journalStore,
            Logger logger,
            RetryScheduler retryScheduler,
            FrozenRewardExecutor frozenRewardExecutor) {
        this.plugin = null;
        this.rewardService = null;
        this.journalStore = Objects.requireNonNull(journalStore, "journalStore");
        this.deliveryLedger = CookingDeliveryLedgerStore.inMemory();
        this.logger = Objects.requireNonNull(logger, "logger");
        this.retryScheduler = Objects.requireNonNull(retryScheduler, "retryScheduler");
        this.frozenRewardExecutor = Objects.requireNonNull(frozenRewardExecutor, "frozenRewardExecutor");
        this.executionDispatcher = null;
    }

    public synchronized void register(CookingStationStateAccess access) {
        if (access != null) {
            stateAccesses.put(access.stationType(), access);
        }
    }

    public boolean hasActive(StationType stationType, StationCoordinates coordinates) {
        return stationType != null
                && coordinates != null
                && activeByStation.containsKey(stationKey(stationType, coordinates));
    }

    /**
     * Plans and freezes a completion, durably writes PREPARED, then starts the state machine.
     * Returns false when the station already owns a non-terminal completion.
     */
    public boolean submit(CookingCompletionRequest request) {
        if (!accepting.get() || request == null || request.stationType() == null || request.coordinates() == null) {
            return false;
        }
        String stationKey = stationKey(request.stationType(), request.coordinates());
        String operationId = request.operationId();
        if (activeByStation.putIfAbsent(stationKey, operationId) != null) {
            return false;
        }

        CookingCompletionOperation operation;
        try {
            operation = prepareOperation(request);
        } catch (Throwable error) {
            activeByStation.remove(stationKey, operationId);
            logger.warning("Failed to prepare cooking completion at " + stationKey + ": " + rootCauseMessage(error));
            return false;
        }

        journalStore.createIfAbsent(operation).whenComplete((created, error) -> {
            if (error != null || created == null) {
                activeByStation.remove(stationKey, operationId);
                logger.warning("Failed to persist PREPARED cooking completion " + operationId + ": "
                        + rootCauseMessage(error));
                return;
            }
            install(created);
            triggerAdvance(created.operationId());
        });
        return true;
    }

    /** Loads active journals, normalizes crash points, and resumes each operation. */
    public CompletableFuture<Void> recover() {
        if (!accepting.get()) {
            return CompletableFuture.completedFuture(null);
        }
        return journalStore.loadActive().thenCompose(loaded -> {
            List<CompletableFuture<?>> normalizations = new ArrayList<>();
            for (CookingCompletionOperation raw : loaded) {
                CookingCompletionOperation normalized = recoveryPlanner.normalizeForRecovery(raw);
                install(normalized);
                if (!normalized.equals(raw)) {
                    normalizations.add(save(normalized));
                }
            }
            return CompletableFuture.allOf(normalizations.toArray(CompletableFuture[]::new));
        }).thenRun(() -> operations.keySet().forEach(this::triggerAdvance));
    }

    public DrainResult sealAndDrain(long timeout, TimeUnit unit) {
        accepting.set(false);
        return journalStore.sealAndDrain(timeout, unit);
    }

    private CookingCompletionOperation prepareOperation(CookingCompletionRequest request) {
        CookingRewardService.PreparedReward preparedReward = rewardService.prepare(
                request.operationId(),
                request.recipe(),
                request.player(),
                request.rewardLocation(),
                request.dropResult(),
                request.inputs(),
                request.outputs(),
                request.actions(),
                request.phase(),
                request.placeholders()
        );
        List<Unit> inputs = freezePlayerInputs(request);
        List<Unit> deliveries = new ArrayList<>();
        for (CookingRewardService.FrozenRewardUnit rewardUnit : preparedReward.units()) {
            UnitKind kind = rewardUnit.kind() == CookingRewardService.RewardUnitKind.ITEM_REWARD
                    ? UnitKind.ITEM_REWARD
                    : UnitKind.ACTION;
            deliveries.add(new Unit(
                    rewardUnit.stableId(),
                    kind,
                    UnitState.PENDING,
                    Semantics.AT_LEAST_ONCE,
                    rewardUnit.payload(),
                    0,
                    ""
            ));
        }
        long now = System.currentTimeMillis();
        return new CookingCompletionOperation(
                request.operationId(),
                request.completionKey(),
                Status.PREPARED,
                request.stationType(),
                request.coordinates(),
                request.expectedState(),
                "",
                request.commitMode(),
                request.committedState(),
                "",
                inputs,
                deliveries,
                now,
                now,
                ""
        );
    }

    private List<Unit> freezePlayerInputs(CookingCompletionRequest request) {
        if (request.playerInputs().isEmpty()) {
            return List.of();
        }
        List<Unit> units = new ArrayList<>();
        int index = 0;
        for (CookingCompletionRequest.PlayerInventoryInput input : request.playerInputs()) {
            if (input == null) {
                continue;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("player_uuid", request.player() == null ? "" : request.player().getUniqueId().toString());
            payload.put("player_name", request.player() == null ? "" : request.player().getName());
            payload.put("item", input.item());
            payload.put("amount", input.amount());
            payload.put("description", input.description());
            payload.put("inventory_slot", "main_hand");
            units.add(new Unit(
                    request.operationId() + ":input:" + String.format(java.util.Locale.ROOT, "%04d", index++),
                    UnitKind.PLAYER_INVENTORY_INPUT,
                    UnitState.PENDING,
                    Semantics.AT_MOST_ONCE_AFTER_DURABLE_INTENT,
                    payload,
                    0,
                    ""
            ));
        }
        return List.copyOf(units);
    }

    private void install(CookingCompletionOperation operation) {
        if (operation == null || operation.isTerminal()) {
            return;
        }
        operations.put(operation.operationId(), operation);
        activeByStation.put(stationKey(operation.stationType(), operation.stationCoordinates()), operation.operationId());
    }

    private void triggerAdvance(String operationId) {
        if (!accepting.get() || Texts.isBlank(operationId) || !advancing.add(operationId)) {
            return;
        }
        advanceOne(operationId).whenComplete((progressed, error) -> {
            advancing.remove(operationId);
            if (error != null) {
                CookingCompletionOperation operation = operations.get(operationId);
                if (operation != null) {
                    save(operation.withError(rootCauseMessage(error))).whenComplete((_, saveError) -> scheduleRetry(operationId));
                }
                return;
            }
            if (Boolean.TRUE.equals(progressed) && operations.containsKey(operationId)) {
                triggerAdvance(operationId);
            }
        });
    }

    private CompletableFuture<Boolean> advanceOne(String operationId) {
        CookingCompletionOperation operation = operations.get(operationId);
        if (operation == null || operation.isTerminal()) {
            return CompletableFuture.completedFuture(false);
        }
        CookingStationStateAccess access = stateAccesses.get(operation.stationType());
        if (access == null) {
            return quarantine(operation, "No completion state access registered for " + operation.stationType())
                    .thenApply(_ -> false);
        }
        return switch (recoveryPlanner.nextStep(operation)) {
            case COMMIT_INPUTS -> commitNextInput(operation);
            case MARK_INPUT_COMMITTED -> commitStationState(operation, access);
            case DELIVER_NEXT -> deliverNext(operation, access);
            case MARK_COMPLETED -> completeAndArchive(operation, access);
            case ARCHIVE -> archive(operation).thenApply(_ -> false);
            case QUARANTINE -> quarantine(operation, operation.lastError()).thenApply(_ -> false);
            case NONE -> CompletableFuture.completedFuture(false);
        };
    }

    private CompletableFuture<Boolean> commitNextInput(CookingCompletionOperation operation) {
        StateRelation relation = relation(operation);
        if (relation == StateRelation.COMMITTED) {
            CookingCompletionOperation recovered = operation;
            for (Unit unit : operation.inputUnits()) {
                if (!unit.isCompleted()) {
                    recovered = recovered.withInputUnit(unit.complete());
                }
            }
            return save(recovered.withStatus(Status.INPUT_COMMITTED)).thenApply(_ -> true);
        }
        if (relation != StateRelation.EXPECTED) {
            return quarantine(operation, "Station state changed before inventory input commit")
                    .thenApply(_ -> false);
        }
        Optional<Unit> pending = operation.nextPendingInput();
        if (pending.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }
        Unit unit = pending.get();
        CookingCompletionOperation intent = operation.withInputUnit(unit.beginAttempt());
        return save(intent).thenCompose(saved -> consumePlayerInput(saved.inputUnits().stream()
                .filter(candidate -> candidate.unitId().equals(unit.unitId()))
                .findFirst()
                .orElseThrow()).thenCompose(success -> {
                    Unit current = saved.inputUnits().stream()
                            .filter(candidate -> candidate.unitId().equals(unit.unitId()))
                            .findFirst()
                            .orElseThrow();
                    CookingCompletionOperation updated = success
                            ? saved.withInputUnit(current.complete())
                            : saved.withInputUnit(current.fail("Required main-hand inventory input is unavailable"));
                    return save(updated).thenApply(_ -> {
                        if (!success) {
                            scheduleRetry(updated.operationId());
                        }
                        return success;
                    });
                }));
    }

    private CompletableFuture<Boolean> commitStationState(
            CookingCompletionOperation operation,
            CookingStationStateAccess access) {
        StateRelation relation = relation(operation);
        if (relation == StateRelation.COMMITTED) {
            return save(operation.withStatus(Status.INPUT_COMMITTED)).thenApply(_ -> true);
        }
        if (relation != StateRelation.EXPECTED) {
            return quarantine(operation, "Station state changed before durable completion commit")
                    .thenApply(_ -> false);
        }
        CompletionStage<Void> commit = operation.commitMode() == CookingCompletionOperation.CommitMode.DELETE
                ? access.delete(operation.stationCoordinates())
                : access.replace(operation.stationCoordinates(), operation.committedState());
        return commit.toCompletableFuture()
                .thenCompose(_ -> save(operation.withStatus(Status.INPUT_COMMITTED)))
                .thenApply(_ -> true);
    }

    private CompletableFuture<Boolean> deliverNext(
            CookingCompletionOperation operation,
            CookingStationStateAccess access) {
        if (relation(operation) != StateRelation.COMMITTED) {
            return quarantine(operation, "Committed station state no longer matches completion journal")
                    .thenApply(_ -> false);
        }
        if (operation.status() == Status.INPUT_COMMITTED) {
            return save(operation.withStatus(Status.DELIVERING)).thenApply(_ -> true);
        }
        Optional<Unit> pending = operation.nextPendingDelivery();
        if (pending.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }
        Unit unit = pending.get();
        CookingRewardService.RewardUnitKind kind = unit.kind() == UnitKind.ITEM_REWARD
                ? CookingRewardService.RewardUnitKind.ITEM_REWARD
                : CookingRewardService.RewardUnitKind.ACTION_BATCH;
        return deliveryLedger.isConfirmed(unit.unitId(), kind.name(), unit.payload()).thenCompose(alreadyConfirmed -> {
            if (Boolean.TRUE.equals(alreadyConfirmed)) {
                return save(operation.withDeliveryUnit(unit.complete())).thenApply(_ -> true);
            }
            CookingCompletionOperation intent = operation.withDeliveryUnit(unit.beginAttempt());
            return save(intent).thenCompose(saved -> {
                Unit current = saved.deliveryUnits().stream()
                        .filter(candidate -> candidate.unitId().equals(unit.unitId()))
                        .findFirst()
                        .orElseThrow();
                return deliveryLedger.recordIntent(current.unitId(), kind.name(), current.payload())
                        .thenCompose(_ -> frozenRewardExecutor.execute(kind, current.payload()))
                        .thenCompose(success -> {
                            if (!success) {
                                CookingCompletionOperation failed = saved.withDeliveryUnit(
                                        current.fail("Frozen reward unit returned failure"));
                                return save(failed).thenApply(_ -> {
                                    scheduleRetry(failed.operationId());
                                    return false;
                                });
                            }
                            return deliveryLedger.confirm(current.unitId(), kind.name(), current.payload())
                                    .thenCompose(confirmed -> {
                                        if (!Boolean.TRUE.equals(confirmed)) {
                                            CookingCompletionOperation failed = saved.withDeliveryUnit(
                                                    current.fail("Delivery receiver acknowledgement was not persisted"));
                                            return save(failed).thenApply(_ -> {
                                                scheduleRetry(failed.operationId());
                                                return false;
                                            });
                                        }
                                        return save(saved.withDeliveryUnit(current.complete())).thenApply(_ -> true);
                                    });
                        });
            });
        });
    }

    private CompletableFuture<Boolean> completeAndArchive(
            CookingCompletionOperation operation,
            CookingStationStateAccess access) {
        if (relation(operation) != StateRelation.COMMITTED) {
            return quarantine(operation, "Station state changed before completion archive")
                    .thenApply(_ -> false);
        }
        return ensureDeliveryAcknowledgements(operation)
                .thenCompose(_ -> save(operation.withStatus(Status.COMPLETED)))
                .thenCompose(this::archive)
                .thenApply(_ -> false);
    }

    private CompletableFuture<Void> ensureDeliveryAcknowledgements(CookingCompletionOperation operation) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (Unit unit : operation.deliveryUnits()) {
            chain = chain.thenCompose(_ -> {
                if (!unit.isCompleted()) {
                    return CompletableFuture.<Void>failedFuture(new IllegalStateException(
                            "Cooking delivery unit is not completed: " + unit.unitId()));
                }
                CookingRewardService.RewardUnitKind kind = unit.kind() == UnitKind.ITEM_REWARD
                        ? CookingRewardService.RewardUnitKind.ITEM_REWARD
                        : CookingRewardService.RewardUnitKind.ACTION_BATCH;
                return deliveryLedger.isConfirmed(unit.unitId(), kind.name(), unit.payload())
                        .thenCompose(confirmed -> Boolean.TRUE.equals(confirmed)
                                ? CompletableFuture.completedFuture(null)
                                : deliveryLedger.confirm(unit.unitId(), kind.name(), unit.payload())
                                        .thenCompose(persisted -> Boolean.TRUE.equals(persisted)
                                                ? CompletableFuture.completedFuture(null)
                                                : CompletableFuture.<Void>failedFuture(new IllegalStateException(
                                                        "Cooking delivery acknowledgement was not persisted: " + unit.unitId()))));
            });
        }
        return chain;
    }

    private CompletableFuture<Boolean> consumePlayerInput(Unit unit) {
        String playerUuid = Texts.toStringSafe(unit.payload().get("player_uuid"));
        Player player;
        try {
            player = Texts.isBlank(playerUuid) ? null : Bukkit.getPlayer(UUID.fromString(playerUuid));
        } catch (IllegalArgumentException exception) {
            player = null;
        }
        if (player == null || !player.isOnline()) {
            return CompletableFuture.completedFuture(false);
        }
        Map<String, Object> serialized = mapValue(unit.payload().get("item"));
        ItemStack template = StoredItemCodec.deserialize(serialized);
        int amount = Math.max(1, intValue(unit.payload().get("amount"), 1));
        if (template == null || template.getType().isAir()) {
            return CompletableFuture.completedFuture(false);
        }
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        try {
            Player target = player;
            if (executionDispatcher == null) {
                result.completeExceptionally(new IllegalStateException("Execution dispatcher is unavailable"));
                return result;
            }
            TaskHandle handle = executionDispatcher.runEntity(plugin, target, () -> {
                try {
                    ItemStack current = target.getInventory().getItemInMainHand();
                    if (current == null || current.getType().isAir() || !current.isSimilar(template) || current.getAmount() < amount) {
                        result.complete(false);
                        return;
                    }
                    int remaining = current.getAmount() - amount;
                    if (remaining <= 0) {
                        target.getInventory().setItemInMainHand(null);
                    } else {
                        current.setAmount(remaining);
                        target.getInventory().setItemInMainHand(current);
                    }
                    result.complete(true);
                } catch (Throwable error) {
                    result.completeExceptionally(error);
                }
            }, () -> result.complete(false));
            if (handle == null) {
                result.complete(false);
            }
        } catch (Throwable error) {
            result.completeExceptionally(error);
        }
        return result;
    }

    private StateRelation relation(CookingCompletionOperation operation) {
        CookingStationStateAccess access = stateAccesses.get(operation.stationType());
        if (access == null) {
            return StateRelation.OTHER;
        }
        Map<String, Object> current = access.snapshot(operation.stationCoordinates());
        String currentDigest = CookingCompletionStateDigest.digest(current == null ? Map.of() : current);
        if (currentDigest.equals(operation.expectedStateDigest())) {
            return StateRelation.EXPECTED;
        }
        if (currentDigest.equals(operation.committedStateDigest())) {
            return StateRelation.COMMITTED;
        }
        return StateRelation.OTHER;
    }

    private CompletableFuture<CookingCompletionOperation> save(CookingCompletionOperation operation) {
        return journalStore.save(operation).thenApply(saved -> {
            if (!saved.isTerminal()) {
                install(saved);
            }
            return saved;
        });
    }

    private CompletableFuture<CookingCompletionOperation> archive(CookingCompletionOperation operation) {
        return journalStore.archive(operation).thenApply(archived -> {
            remove(archived);
            return archived;
        });
    }

    private CompletableFuture<CookingCompletionOperation> quarantine(
            CookingCompletionOperation operation,
            String error) {
        String reason = Texts.isBlank(error) ? "Cooking completion recovery rejected operation" : error;
        logger.warning("Quarantining cooking completion " + operation.operationId() + ": " + reason);
        return journalStore.quarantine(operation, reason).thenApply(quarantined -> {
            remove(quarantined);
            return quarantined;
        });
    }

    private void remove(CookingCompletionOperation operation) {
        operations.remove(operation.operationId());
        activeByStation.remove(stationKey(operation.stationType(), operation.stationCoordinates()), operation.operationId());
        retryScheduled.remove(operation.operationId());
    }

    private void scheduleRetry(String operationId) {
        if (!accepting.get() || !operations.containsKey(operationId) || !retryScheduled.add(operationId)) {
            return;
        }
        try {
            retryScheduler.schedule(() -> {
                retryScheduled.remove(operationId);
                triggerAdvance(operationId);
            }, RETRY_DELAY_TICKS);
        } catch (Throwable error) {
            retryScheduled.remove(operationId);
        }
    }

    private String stationKey(StationType stationType, StationCoordinates coordinates) {
        return stationType.folderName() + ":" + coordinates.runtimeKey();
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Object plain = ConfigNodes.toPlainData(map);
        if (!(plain instanceof Map<?, ?> plainMap)) {
            return Map.of();
        }
        return Map.copyOf(MapYamlSection.normalizeMap(plainMap));
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(Texts.toStringSafe(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null
                && current.getCause() != null
                && (current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)) {
            current = current.getCause();
        }
        if (current == null) {
            return "unknown error";
        }
        String message = current.getMessage();
        return Texts.isBlank(message) ? current.getClass().getSimpleName() : message;
    }

    @FunctionalInterface
    interface RetryScheduler {
        void schedule(Runnable task, long delayTicks);
    }

    @FunctionalInterface
    interface FrozenRewardExecutor {
        CompletableFuture<Boolean> execute(CookingRewardService.RewardUnitKind kind, Map<String, Object> payload);
    }

    private enum StateRelation {
        EXPECTED,
        COMMITTED,
        OTHER
    }
}
