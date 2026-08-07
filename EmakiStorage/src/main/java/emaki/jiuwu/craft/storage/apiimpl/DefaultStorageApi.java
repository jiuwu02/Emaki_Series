package emaki.jiuwu.craft.storage.apiimpl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.ApiStatus;
import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.FailureKind;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.corelib.execution.TaskHandle;
import emaki.jiuwu.craft.storage.EmakiStoragePlugin;
import emaki.jiuwu.craft.storage.api.EmakiStorageApi;
import emaki.jiuwu.craft.storage.api.StorageOperations;
import emaki.jiuwu.craft.storage.api.model.ReservationHandle;
import emaki.jiuwu.craft.storage.api.model.StorageAmount;
import emaki.jiuwu.craft.storage.api.model.StorageBatchRequest;
import emaki.jiuwu.craft.storage.api.model.StorageBatchResult;
import emaki.jiuwu.craft.storage.api.model.StorageEntrySnapshot;
import emaki.jiuwu.craft.storage.model.StorageResult;
import emaki.jiuwu.craft.storage.api.model.StorageSnapshot;
import emaki.jiuwu.craft.storage.log.StorageLogEntry;
import emaki.jiuwu.craft.storage.log.StorageOperationSource;
import emaki.jiuwu.craft.storage.log.StorageOperationType;
import emaki.jiuwu.craft.storage.model.PlayerStorage;
import emaki.jiuwu.craft.storage.model.StorageEntry;
import emaki.jiuwu.craft.storage.model.StorageKey;
import emaki.jiuwu.craft.storage.service.StorageTransactionService;

/** Runtime bridge backing {@link EmakiStorageApi}. */
public final class DefaultStorageApi implements EmakiStorageApi.Bridge {

    private final EmakiStoragePlugin plugin;
    private final StorageOperations operations = new Operations();

    public DefaultStorageApi(EmakiStoragePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull ApiStatus status() {
        if (!plugin.isEnabled()) {
            return ApiStatus.notInstalled();
        }
        String version = plugin.getPluginMeta().getVersion();
        return isReady()
                ? ApiStatus.ready(plugin.getName(), version, version)
                : ApiStatus.loading(plugin.getName(), version, version);
    }

    @Override
    public @NotNull StorageOperations operations() {
        return operations;
    }

    /**
     * {@return whether the module can serve API calls right now}
     *
     * <p>{@code contentReady()} comes first on purpose: the services below are non-null from
     * initialize() onward, so without it this returned true while a reload was rebuilding cost tiers
     * and the GUI template.</p>
     */
    private boolean isReady() {
        return plugin.isEnabled()
                && plugin.contentReady()
                && plugin.dataStore() != null
                && plugin.transactionService() != null
                && plugin.capacityService() != null
                && plugin.storageGuiService() != null
                && plugin.sessionManager() != null
                && plugin.threadOwnership() != null;
    }

    private StorageSnapshot snapshot(PlayerStorage storage, @Nullable Player player) {
        var capacity = plugin.capacityService().capacityOf(storage, player,
                plugin.storageGuiService().slotsPerPage());
        List<StorageEntrySnapshot> entries = new ArrayList<>(storage.entryCount());
        List<StorageKey> order = storage.entryOrder();
        for (int index = 0; index < order.size(); index++) {
            StorageEntry entry = storage.entry(order.get(index));
            if (entry == null) {
                continue;
            }
            entries.add(entry.toSnapshot(index,
                    plugin.capacityService().effectiveStackLimit(storage, entry),
                    storage.reservedAmount(entry.key())));
        }
        return new StorageSnapshot(storage.playerId(), entries, capacity,
                storage.defaultStackLimit(), storage.sortMode().id());
    }

    private CompletableFuture<EmakiResult<StorageSnapshot>> snapshotAsync(PlayerStorage storage) {
        Player player = plugin.onlinePlayer(storage.playerId());
        Supplier<EmakiResult<StorageSnapshot>> operation = () -> {
            try {
                return EmakiResult.success(snapshot(storage, player));
            } catch (RuntimeException failure) {
                return EmakiResult.internalError("storage.snapshot_failed");
            }
        };
        if (player != null && player.isOnline()) {
            return plugin.runOwnerWriteAsync(player, operation,
                    () -> EmakiResult.failure(FailureKind.UNAVAILABLE, "storage.owner_unavailable"));
        }
        return runAsync(operation,
                () -> EmakiResult.failure(FailureKind.UNAVAILABLE, "storage.async_unavailable"));
    }

    private <T> CompletableFuture<T> runAsync(Supplier<T> operation, Supplier<T> onReject) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            TaskHandle scheduled = plugin.executionDispatcher().runAsync(plugin, () -> {
                try {
                    future.complete(operation.get());
                } catch (Throwable failure) {
                    future.completeExceptionally(failure);
                }
            });
            if (scheduled == null) {
                future.complete(onReject.get());
            }
        } catch (Throwable failure) {
            future.completeExceptionally(failure);
        }
        return future;
    }

    private <T> CompletableFuture<EmakiResult<T>> mutate(@Nullable UUID playerId,
            Function<PlayerStorage, EmakiResult<T>> mutation,
            String errorReason) {
        if (playerId == null) {
            return completed(EmakiResult.invalidInput("storage.invalid_player_id"));
        }
        if (!isReady()) {
            return completed(EmakiResult.unavailable());
        }
        Player player = plugin.onlinePlayer(playerId);
        if (player == null || !player.isOnline()) {
            return completed(EmakiResult.targetOffline());
        }
        return plugin.runOwnerWriteAsync(player, () -> {
            PlayerStorage storage = plugin.dataStore().cached(playerId);
            if (storage == null) {
                return EmakiResult.<T>failure(FailureKind.UNAVAILABLE, "storage.data_unavailable");
            }
            return mutation.apply(storage);
        }, () -> EmakiResult.<T>failure(FailureKind.UNAVAILABLE, "storage.owner_unavailable"))
                .exceptionally(failure -> EmakiResult.<T>internalError(errorReason));
    }

    private static EmakiResult<StorageAmount> mapAmount(StorageResult result) {
        if (result == null) {
            return EmakiResult.internalError("storage.operation_failed");
        }
        StorageAmount amount = new StorageAmount(result.requestedAmount(), result.appliedAmount());
        String reasonKey = result.reasonKey() == null || result.reasonKey().isBlank()
                ? "storage.operation_failed"
                : result.reasonKey();
        return switch (result.status()) {
            case SUCCESS -> EmakiResult.success(amount);
            case PARTIAL -> EmakiResult.partial(amount, reasonKey);
            case CANCELLED -> EmakiResult.failure(FailureKind.CANCELLED, reasonKey);
            case UNAVAILABLE -> EmakiResult.unavailable();
            case FAILED -> EmakiResult.failure(failureKind(reasonKey), reasonKey);
        };
    }

    private static FailureKind failureKind(String reasonKey) {
        return switch (reasonKey) {
            case "invalid_item", "invalid_amount", "invalid_delta" -> FailureKind.INVALID_INPUT;
            case "entry_missing", "slot_empty", "empty_slot" -> FailureKind.NOT_FOUND;
            default -> FailureKind.REJECTED;
        };
    }

    private static <T> CompletableFuture<EmakiResult<T>> completed(EmakiResult<T> result) {
        return CompletableFuture.completedFuture(result);
    }

    private final class Operations implements StorageOperations {

        @Override
        public @NotNull CompletableFuture<EmakiResult<StorageSnapshot>> readSnapshotAsync(@Nullable UUID playerId) {
            if (playerId == null) {
                return completed(EmakiResult.invalidInput("storage.invalid_player_id"));
            }
            if (!isReady()) {
                return completed(EmakiResult.unavailable());
            }
            PlayerStorage cached = plugin.dataStore().cached(playerId);
            if (cached != null) {
                return snapshotAsync(cached);
            }
            return plugin.dataStore().beginSessionAsync(playerId, "")
                    .thenCompose(loaded -> {
                        PlayerStorage resolved = loaded == null ? plugin.dataStore().cached(playerId) : loaded;
                        if (resolved == null) {
                            return completed(EmakiResult.<StorageSnapshot>failure(
                                    FailureKind.UNAVAILABLE, "storage.snapshot_unavailable"));
                        }
                        return snapshotAsync(resolved);
                    })
                    .exceptionally(failure -> EmakiResult.internalError("storage.snapshot_failed"));
        }

        @Override
        public @NotNull CompletableFuture<EmakiResult<StorageAmount>> depositAsync(@Nullable UUID playerId,
                @Nullable ItemStack template, long amount) {
            if (template == null || template.getType().isAir()) {
                return completed(EmakiResult.invalidInput("invalid_item"));
            }
            if (amount <= 0L) {
                return completed(EmakiResult.invalidInput("invalid_amount"));
            }
            return mutate(playerId, storage -> {
                Player player = plugin.onlinePlayer(playerId);
                var capacity = plugin.capacityService().capacityOf(storage, player,
                        plugin.storageGuiService().slotsPerPage());
                StorageResult result = plugin.transactionService().depositDirect(storage, player, capacity,
                        template, amount, StorageOperationSource.API);
                return mapAmount(result);
            }, "storage.deposit_failed");
        }

        @Override
        public @NotNull CompletableFuture<EmakiResult<StorageAmount>> withdrawAsync(@Nullable UUID playerId,
                @Nullable ItemStack template, long amount) {
            if (template == null || template.getType().isAir()) {
                return completed(EmakiResult.invalidInput("invalid_item"));
            }
            if (amount <= 0L) {
                return completed(EmakiResult.invalidInput("invalid_amount"));
            }
            return mutate(playerId, storage -> mapAmount(plugin.transactionService().withdraw(storage,
                    plugin.onlinePlayer(playerId), StorageKey.of(template), amount,
                    StorageOperationSource.API)), "storage.withdraw_failed");
        }

        @Override
        public @NotNull CompletableFuture<EmakiResult<Long>> countOfAsync(@Nullable UUID playerId,
                @Nullable ItemStack template) {
            if (template == null || template.getType().isAir()) {
                return completed(EmakiResult.invalidInput("invalid_item"));
            }
            return readSnapshotAsync(playerId).thenApply(result -> switch (result) {
                case EmakiResult.Success<StorageSnapshot> success ->
                    EmakiResult.success(count(success.value(), template));
                case EmakiResult.Partial<StorageSnapshot> partial ->
                    EmakiResult.partial(count(partial.value(), template), partial.reasonKey());
                case EmakiResult.Failure<StorageSnapshot> failure -> failure.retypeFailure();
            });
        }

        @Override
        public @NotNull CompletableFuture<EmakiResult<Integer>> grantSlotsAsync(@Nullable UUID playerId, int amount) {
            return mutate(playerId, storage -> {
                long updated = (long) storage.grantedSlots() + amount;
                if (updated < Integer.MIN_VALUE || updated > Integer.MAX_VALUE) {
                    return EmakiResult.invalidInput("storage.granted_slots_overflow");
                }
                storage.grantedSlots((int) updated);
                storage.markDirty();
                plugin.operationLog().record(StorageLogEntry.raw(playerId, StorageOperationType.ADMIN_GIVE,
                        null, (amount >= 0 ? "+" : "") + amount + "slots",
                        storage.grantedSlots(), StorageOperationSource.API, null));
                return EmakiResult.success(storage.grantedSlots());
            }, "storage.grant_slots_failed");
        }

        @Override
        public @NotNull CompletableFuture<EmakiResult<Long>> setStackLimitAsync(@Nullable UUID playerId, long limit) {
            if (limit < 0L) {
                return completed(EmakiResult.invalidInput("invalid_amount"));
            }
            return mutate(playerId, storage -> {
                storage.defaultStackLimit(limit);
                storage.markDirty();
                plugin.operationLog().record(StorageLogEntry.raw(playerId, StorageOperationType.ADMIN_SET,
                        null, "=" + limit, limit, StorageOperationSource.API, "field=default_stack_limit"));
                return EmakiResult.success(limit);
            }, "storage.set_stack_limit_failed");
        }

        @Override
        public @NotNull CompletableFuture<EmakiResult<Long>> setSlotStackLimitAsync(@Nullable UUID playerId,
                int slotIndex, long limit) {
            if (slotIndex < 0) {
                return completed(EmakiResult.invalidInput("storage.invalid_slot_index"));
            }
            if (limit < 0L) {
                return completed(EmakiResult.invalidInput("invalid_amount"));
            }
            return mutate(playerId, storage -> {
                StorageEntry entry = storage.entryAt(slotIndex);
                if (entry == null) {
                    return EmakiResult.notFound("slot_empty");
                }
                entry.stackLimit(limit);
                storage.markDirty();
                plugin.operationLog().record(StorageLogEntry.raw(playerId, StorageOperationType.ADMIN_SET,
                        plugin.textIndexer().identifierOf(entry.key()), "=" + limit, limit,
                        StorageOperationSource.API, "field=slot_stack_limit slot=" + slotIndex));
                return EmakiResult.success(limit);
            }, "storage.set_slot_stack_limit_failed");
        }

        @Override
        public @NotNull EmakiResult<Unit> openGui(@Nullable Player player) {
            if (player == null) {
                return EmakiResult.invalidInput("storage.invalid_player");
            }
            if (!player.isOnline()) {
                return EmakiResult.targetOffline();
            }
            if (!isReady()) {
                return EmakiResult.unavailable();
            }
            if (!plugin.ownsWriteTarget(player)) {
                return EmakiResult.wrongThread();
            }
            return plugin.sessionManager().openOwn(player)
                    ? EmakiResult.ok()
                    : EmakiResult.failure(FailureKind.UNAVAILABLE, "storage.gui_unavailable");
        }

        @Override
        public @NotNull CompletableFuture<EmakiResult<StorageBatchResult>> applyBatchAsync(
                @Nullable UUID playerId, @Nullable StorageBatchRequest request) {
            EmakiResult<StorageBatchResult> invalid = validateBatch(request);
            if (invalid != null) {
                return completed(invalid);
            }
            return mutate(playerId, storage -> {
                Player player = plugin.onlinePlayer(playerId);
                var capacity = plugin.capacityService().capacityOf(storage, player,
                        plugin.storageGuiService().slotsPerPage());
                StorageTransactionService.BatchOutcome outcome = plugin.transactionService().applyBatch(
                        storage, player, capacity, request.ops(), request.allOrNothing(),
                        StorageOperationSource.API);
                return mapBatch(request, outcome);
            }, "storage.batch_failed");
        }

        @Override
        public @NotNull CompletableFuture<EmakiResult<Map<ItemStack, Long>>> countAllAsync(
                @Nullable UUID playerId, @Nullable Collection<ItemStack> templates) {
            if (templates == null || templates.isEmpty()) {
                return completed(EmakiResult.invalidInput("invalid_item"));
            }
            List<ItemStack> requested = new ArrayList<>(templates.size());
            for (ItemStack template : templates) {
                if (template == null || template.getType().isAir()) {
                    return completed(EmakiResult.invalidInput("invalid_item"));
                }
                requested.add(template);
            }
            return readSnapshotAsync(playerId).thenApply(result -> switch (result) {
                case EmakiResult.Success<StorageSnapshot> success ->
                    EmakiResult.success(countAll(success.value(), requested));
                case EmakiResult.Partial<StorageSnapshot> partial ->
                    EmakiResult.partial(countAll(partial.value(), requested), partial.reasonKey());
                case EmakiResult.Failure<StorageSnapshot> failure -> failure.retypeFailure();
            });
        }

        @Override
        public @NotNull CompletableFuture<EmakiResult<ReservationHandle>> reserveAsync(@Nullable UUID playerId,
                @Nullable StorageBatchRequest request, @Nullable Duration ttl) {
            EmakiResult<ReservationHandle> invalid = validateBatch(request);
            if (invalid != null) {
                return completed(invalid);
            }
            if (ttl == null || ttl.isZero() || ttl.isNegative()) {
                return completed(EmakiResult.invalidInput("invalid_ttl"));
            }
            return mutate(playerId, storage -> {
                Player player = plugin.onlinePlayer(playerId);
                var capacity = plugin.capacityService().capacityOf(storage, player,
                        plugin.storageGuiService().slotsPerPage());
                return plugin.transactionService().reserve(storage, capacity, request.ops(), ttl)
                        .map(reservationId -> {
                            storage.markDirty();
                            return EmakiResult.success(new ReservationHandle(reservationId, playerId));
                        })
                        .orElseGet(() -> EmakiResult.failure(FailureKind.REJECTED, "insufficient_stock"));
            }, "storage.reserve_failed");
        }

        @Override
        public @NotNull CompletableFuture<EmakiResult<StorageBatchResult>> commitAsync(
                @Nullable ReservationHandle handle) {
            if (handle == null || handle.reservationId() == null || handle.playerId() == null) {
                return completed(EmakiResult.invalidInput("storage.invalid_reservation"));
            }
            return mutate(handle.playerId(), storage -> {
                Player player = plugin.onlinePlayer(handle.playerId());
                var capacity = plugin.capacityService().capacityOf(storage, player,
                        plugin.storageGuiService().slotsPerPage());
                StorageTransactionService.CommitOutcome commit = plugin.transactionService().commitReservation(
                        storage, player, capacity, handle.reservationId(), StorageOperationSource.API);
                if (commit.outcome() == null) {
                    return EmakiResult.notFound("reservation_missing");
                }
                return mapBatch(commit.allOrNothing(), commit.outcome());
            }, "storage.commit_failed");
        }

        @Override
        public @NotNull CompletableFuture<EmakiResult<Unit>> releaseAsync(@Nullable ReservationHandle handle) {
            if (handle == null || handle.reservationId() == null || handle.playerId() == null) {
                return completed(EmakiResult.invalidInput("storage.invalid_reservation"));
            }
            return mutate(handle.playerId(), storage -> {
                if (storage.removeReservation(handle.reservationId()) == null) {
                    return EmakiResult.<Unit>notFound("reservation_missing");
                }
                storage.markDirty();
                return EmakiResult.ok();
            }, "storage.release_failed");
        }

        private <T> EmakiResult<T> validateBatch(StorageBatchRequest request) {
            if (request == null || request.empty()) {
                return EmakiResult.invalidInput("batch_empty");
            }
            if (!isReady()) {
                return EmakiResult.unavailable();
            }
            int maximum = plugin.transactionService().batchMaxOps();
            if (request.ops().size() > maximum) {
                return EmakiResult.invalidInput("batch_too_large");
            }
            return null;
        }

        private long count(StorageSnapshot snapshot, ItemStack template) {
            StorageKey key = StorageKey.of(template);
            ItemStack normalized = key.toItemStack();
            for (StorageEntrySnapshot entry : snapshot.entries()) {
                if (entry.template().equals(normalized)) {
                    return entry.amount();
                }
            }
            return 0L;
        }

        private Map<ItemStack, Long> countAll(StorageSnapshot snapshot, List<ItemStack> templates) {
            Map<ItemStack, Long> counts = new LinkedHashMap<>(templates.size());
            for (ItemStack template : templates) {
                counts.put(template, count(snapshot, template));
            }
            return Map.copyOf(counts);
        }
    }

    private static EmakiResult<StorageBatchResult> mapBatch(StorageBatchRequest request,
            StorageTransactionService.BatchOutcome outcome) {
        return mapBatch(request.allOrNothing(), outcome);
    }

    /**
     * Maps a transaction-layer batch outcome onto the public result.
     *
     * <p>An all-or-nothing batch is never reported as {@code Partial}: it either applied every op or
     * applied none, so a partial result would tell the caller something that cannot have happened.
     */
    private static EmakiResult<StorageBatchResult> mapBatch(boolean allOrNothing,
            StorageTransactionService.BatchOutcome outcome) {
        if (outcome.cancelled()) {
            return EmakiResult.failure(FailureKind.CANCELLED, "batch_cancelled");
        }
        int opCount = outcome.requested().size();
        List<StorageAmount> amounts = new ArrayList<>(opCount);
        for (int index = 0; index < opCount; index++) {
            long applied = index < outcome.applied().size() ? outcome.applied().get(index) : 0L;
            amounts.add(new StorageAmount(outcome.requested().get(index), applied));
        }
        StorageBatchResult result = new StorageBatchResult(amounts, outcome.failedIndex(),
                outcome.reasonKey());
        if (!outcome.failed()) {
            return EmakiResult.success(result);
        }
        return allOrNothing
                ? EmakiResult.failure(failureKind(outcome.reasonKey()), outcome.reasonKey())
                : EmakiResult.partial(result, outcome.reasonKey());
    }
}
