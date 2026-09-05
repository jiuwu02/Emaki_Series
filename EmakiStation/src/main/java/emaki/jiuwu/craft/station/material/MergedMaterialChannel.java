package emaki.jiuwu.craft.station.material;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.station.api.model.ConsumedMaterial;
import emaki.jiuwu.craft.station.api.model.MaterialChannel;
import emaki.jiuwu.craft.station.definition.StationDefinition;
import emaki.jiuwu.craft.station.recipe.MaterialRequirement;
import emaki.jiuwu.craft.station.recipe.RecipeDefinition;
import emaki.jiuwu.craft.station.recipe.RecipeMatcher;
import emaki.jiuwu.craft.storage.api.model.ReservationHandle;

public final class MergedMaterialChannel {

    private static final Duration RESERVATION_TTL = Duration.ofSeconds(30L);
    private static final long MAX_BATCH_CEILING = 100_000L;

    public record Availability(Map<ItemSourceRef, Long> backpack,
            Map<ItemSourceRef, Long> storage,
            boolean backpackEnabled) {

        public Availability {
            backpack = backpack == null ? Map.of() : Map.copyOf(backpack);
            storage = storage == null ? Map.of() : Map.copyOf(storage);
        }

        public Availability(Map<ItemSourceRef, Long> backpack, Map<ItemSourceRef, Long> storage) {
            this(backpack, storage, true);
        }

        public static Availability empty() {
            return new Availability(Map.of(), Map.of(), false);
        }

        public long totalOf(ItemSourceRef source) {
            long carried = backpack.getOrDefault(source, 0L);
            long stored = storage.getOrDefault(source, 0L);
            try {
                return Math.addExact(carried, stored);
            } catch (ArithmeticException overflow) {
                return Long.MAX_VALUE;
            }
        }

        public long totalOf(MaterialRequirement requirement) {
            long total = 0L;
            for (ItemSourceRef source : requirement.sources()) {
                try {
                    total = Math.addExact(total, totalOf(source));
                } catch (ArithmeticException overflow) {
                    return Long.MAX_VALUE;
                }
            }
            return total;
        }

        public Map<ItemSourceRef, Long> combined() {
            Map<ItemSourceRef, Long> merged = new LinkedHashMap<>(backpack);
            storage.forEach((source, amount) -> merged.merge(source, amount, MergedMaterialChannel::plus));
            return merged;
        }
    }

    public record StackDebit(int slotIndex, ItemStack expected, String materialId,
            String requirementId, String countKey, ItemSourceRef source, int amount) {

        public StackDebit {
            expected = expected == null ? null : expected.clone();
            materialId = materialId == null ? "legacy" : materialId;
            requirementId = requirementId == null ? materialId : requirementId;
            countKey = countKey == null ? materialId : countKey;
        }

        public StackDebit(int slotIndex, ItemStack expected, ItemSourceRef source, int amount) {
            this(slotIndex, expected, "legacy", "legacy", "legacy", source, amount);
        }
    }

    public record SourceDebit(String materialId,
            String requirementId,
            String countKey,
            ItemSourceRef source,
            MaterialChannel channel,
            long amount) {
    }

    private record Candidate(MaterialChannel channel,
            ItemSourceRef source,
            int position,
            ItemStack stack,
            long amount) {
    }

    public record DebitPlan(Map<ItemSourceRef, Long> fromBackpack,
            Map<ItemSourceRef, Long> fromStorage,
            List<StackDebit> fromStacks,
            List<SourceDebit> allocations) {

        public DebitPlan {
            fromBackpack = fromBackpack == null ? Map.of() : Map.copyOf(fromBackpack);
            fromStorage = fromStorage == null ? Map.of() : Map.copyOf(fromStorage);
            fromStacks = fromStacks == null ? List.of() : List.copyOf(fromStacks);
            allocations = allocations == null ? List.of() : List.copyOf(allocations);
        }

        public DebitPlan(Map<ItemSourceRef, Long> fromBackpack, Map<ItemSourceRef, Long> fromStorage) {
            this(fromBackpack, fromStorage, List.of(), List.of());
        }

        public DebitPlan(Map<ItemSourceRef, Long> fromBackpack,
                Map<ItemSourceRef, Long> fromStorage,
                List<StackDebit> fromStacks) {
            this(fromBackpack, fromStorage, fromStacks, List.of());
        }

        public boolean touchesStorage() {
            return !fromStorage.isEmpty();
        }

        public boolean stackResolved() {
            return !fromStacks.isEmpty();
        }

        public boolean empty() {
            return fromBackpack.isEmpty() && fromStorage.isEmpty() && fromStacks.isEmpty();
        }
    }

    private final Plugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final BackpackChannel backpackChannel;
    private final StorageChannel storageChannel;
    private final Supplier<Boolean> storageAllowed;

    public MergedMaterialChannel(Plugin plugin,
            ExecutionDispatcher dispatcher,
            BackpackChannel backpackChannel,
            StorageChannel storageChannel,
            Supplier<Boolean> storageAllowed) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.backpackChannel = backpackChannel;
        this.storageChannel = storageChannel;
        this.storageAllowed = storageAllowed == null ? () -> Boolean.TRUE : storageAllowed;
    }

    public CompletableFuture<Availability> snapshotAsync(Player player,
            StationDefinition station,
            RecipeDefinition recipe) {
        if (player == null || station == null || recipe == null) {
            return CompletableFuture.completedFuture(Availability.empty());
        }
        List<ItemSourceRef> sources = StorageChannel.sourcesOf(recipe);
        Map<ItemSourceRef, Long> carried = station.backpackChannel()
                ? backpackChannel.countAll(player, sources)
                : Map.of();
        if (!storageUsable(station)) {
            return CompletableFuture.completedFuture(new Availability(carried, Map.of(), station.backpackChannel()));
        }
        return storageChannel.countSourcesAsync(player.getUniqueId(), sources)
                .thenApply(stored -> new Availability(carried, stored, station.backpackChannel()));
    }

    public DebitPlan plan(RecipeDefinition recipe, long batch, Availability availability) {
        if (recipe == null || availability == null || matcherDriven(recipe)) {
            return null;
        }
        Map<ItemSourceRef, Long> carried = new LinkedHashMap<>(availability.backpack());
        Map<ItemSourceRef, Long> stored = new LinkedHashMap<>(availability.storage());
        Map<ItemSourceRef, Long> fromBackpack = new LinkedHashMap<>();
        Map<ItemSourceRef, Long> fromStorage = new LinkedHashMap<>();
        List<SourceDebit> allocations = new ArrayList<>();
        for (MaterialRequirement requirement : recipe.requirements()) {
            long needed = requirement.totalFor(batch);
            for (ItemSourceRef source : requirement.sources()) {
                if (needed <= 0L) {
                    break;
                }
                long carriedTaken = take(carried, source, needed,
                        requirement.consume() ? fromBackpack : null);
                if (requirement.consume() && carriedTaken > 0L) {
                    allocations.add(new SourceDebit(requirement.materialId(), requirement.requirementId(),
                            requirement.countKey(), source, MaterialChannel.BACKPACK, carriedTaken));
                }
                needed -= carriedTaken;
                if (needed <= 0L) {
                    break;
                }
                long storedTaken = take(stored, source, needed,
                        requirement.consume() ? fromStorage : null);
                if (requirement.consume() && storedTaken > 0L) {
                    allocations.add(new SourceDebit(requirement.materialId(), requirement.requirementId(),
                            requirement.countKey(), source, MaterialChannel.STORAGE, storedTaken));
                }
                needed -= storedTaken;
            }
            if (needed > 0L) {
                return null;
            }
        }
        return new DebitPlan(fromBackpack, fromStorage, List.of(), allocations);
    }

    public DebitPlan plan(Player player, RecipeDefinition recipe, long batch, Availability availability) {
        if (player == null || recipe == null || availability == null) {
            return null;
        }
        List<Candidate> candidates = new ArrayList<>();
        if (availability.backpackEnabled()) {
            for (BackpackChannel.SlotStack slot : backpackChannel.snapshotStacks(player)) {
                ItemSourceRef source = backpackChannel.identify(slot.stack());
                candidates.add(new Candidate(MaterialChannel.BACKPACK, source, slot.slotIndex(), slot.stack(),
                        slot.stack().getAmount()));
            }
        }
        availability.storage().forEach((source, amount) -> {
            if (source != null && amount != null && amount > 0L) {
                candidates.add(new Candidate(MaterialChannel.STORAGE, source, -1, null, amount));
            }
        });
        List<Long> supplies = candidates.stream().map(Candidate::amount).toList();
        List<Long> demands = recipe.requirements().stream().map(requirement -> requirement.totalFor(batch)).toList();
        AllocationEngine.Result allocation = AllocationEngine.allocate(supplies, demands, (candidateIndex, requirementIndex) -> {
            Candidate candidate = candidates.get(candidateIndex);
            MaterialRequirement requirement = recipe.requirements().get(requirementIndex);
            if (candidate.channel() == MaterialChannel.STORAGE) {
                return !requirement.hasMatcher() && matchesSource(requirement, candidate.source());
            }
            return requirement.matches(backpackChannel.contextOf(player, candidate.stack()));
        });
        if (!allocation.satisfied()) {
            return null;
        }
        return debitPlanOf(recipe, candidates, allocation);
    }

    public long maxBatch(RecipeDefinition recipe, Availability availability) {
        if (recipe == null || availability == null) {
            return 0L;
        }
        return RecipeMatcher.maxBatch(recipe, availability.combined());
    }

    public long ownedOf(Player player, MaterialRequirement requirement, Availability availability) {
        if (requirement == null) {
            return 0L;
        }
        if (!requirement.hasMatcher()) {
            return availability == null ? 0L : availability.totalOf(requirement);
        }
        if (player == null) {
            return 0L;
        }
        long owned = 0L;
        for (BackpackChannel.SlotStack slotStack : backpackChannel.snapshotStacks(player)) {
            if (requirement.matches(backpackChannel.contextOf(player, slotStack.stack()))) {
                owned = plus(owned, slotStack.stack().getAmount());
            }
        }
        return owned;
    }

    public long maxBatch(Player player, RecipeDefinition recipe, Availability availability) {
        if (player == null || recipe == null || availability == null || recipe.requirements().isEmpty()) {
            return 0L;
        }
        if (plan(player, recipe, 1L, availability) == null) {
            return 0L;
        }
        long low = 1L;
        long high = MAX_BATCH_CEILING;
        long best = 1L;
        while (low <= high) {
            long middle = low + (high - low) / 2L;
            if (plan(player, recipe, middle, availability) != null) {
                best = middle;
                low = middle + 1L;
            } else {
                high = middle - 1L;
            }
        }
        return best;
    }

    public static boolean matcherDriven(RecipeDefinition recipe) {
        if (recipe == null) {
            return false;
        }
        for (MaterialRequirement requirement : recipe.requirements()) {
            if (requirement.hasMatcher()) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesSource(MaterialRequirement requirement, ItemSourceRef candidate) {
        for (ItemSourceRef source : requirement.sources()) {
            if (ItemSourceUtil.matches(source, candidate)) {
                return true;
            }
        }
        return false;
    }

    private DebitPlan debitPlanOf(RecipeDefinition recipe,
            List<Candidate> candidates,
            AllocationEngine.Result allocation) {
        Map<ItemSourceRef, Long> fromStorage = new LinkedHashMap<>();
        List<StackDebit> stackDebits = new ArrayList<>();
        List<SourceDebit> allocations = new ArrayList<>();
        for (AllocationEngine.Assignment assignment : allocation.assignments()) {
            Candidate candidate = candidates.get(assignment.candidateIndex());
            MaterialRequirement requirement = recipe.requirements().get(assignment.requirementIndex());
            if (!requirement.consume() || assignment.amount() <= 0L) {
                continue;
            }
            SourceDebit debit = new SourceDebit(requirement.materialId(), requirement.requirementId(),
                    requirement.countKey(), candidate.source(), candidate.channel(), assignment.amount());
            allocations.add(debit);
            if (candidate.channel() == MaterialChannel.STORAGE) {
                fromStorage.merge(candidate.source(), assignment.amount(), MergedMaterialChannel::plus);
                continue;
            }
            if (candidate.source() == null || candidate.stack() == null || assignment.amount() > Integer.MAX_VALUE) {
                return null;
            }
            stackDebits.add(new StackDebit(candidate.position(), candidate.stack(), requirement.materialId(),
                    requirement.requirementId(), requirement.countKey(), candidate.source(), (int) assignment.amount()));
        }
        return new DebitPlan(Map.of(), fromStorage, stackDebits, allocations);
    }

    public void runOnOwner(Player player, Runnable work) {
        if (player == null || work == null) {
            return;
        }
        dispatcher.runEntity(plugin, player, work);
    }

    public CompletableFuture<EmakiResult<List<ConsumedMaterial>>> debitAsync(Player player, DebitPlan plan) {
        if (player == null || plan == null) {
            return CompletableFuture.completedFuture(
                    EmakiResult.invalidInput("station.submit_bad_request"));
        }
        if (plan.empty()) {
            return CompletableFuture.completedFuture(EmakiResult.success(List.of()));
        }
        if (plan.stackResolved() && !plan.touchesStorage()) {
            List<ConsumedMaterial> consumed = consumeBackpack(player, plan);
            return CompletableFuture.completedFuture(consumed == null
                    ? EmakiResult.rejected("station.insufficient_materials")
                    : EmakiResult.success(consumed));
        }
        if (!plan.touchesStorage()) {
            List<ConsumedMaterial> consumed = backpackChannel.consume(player, plan.fromBackpack());
            return CompletableFuture.completedFuture(consumed == null
                    ? EmakiResult.rejected("station.insufficient_materials")
                    : EmakiResult.success(consumed));
        }
        if (storageChannel.reservationSupported()) {
            return debitWithReservation(player, plan);
        }
        return debitWithoutReservation(player, plan);
    }

    private CompletableFuture<EmakiResult<List<ConsumedMaterial>>> debitWithReservation(Player player,
            DebitPlan plan) {
        return storageChannel.reserveAsync(player.getUniqueId(), plan.fromStorage(), RESERVATION_TTL)
                .thenCompose(reserved -> {
                    if (reserved.isFailure()) {
                        return CompletableFuture.completedFuture(reserved.retypeFailure());
                    }
                    ReservationHandle handle = reserved.orElse(null);
                    if (handle == null) {
                        return CompletableFuture.completedFuture(
                                EmakiResult.<List<ConsumedMaterial>>internalError(
                                        "station.storage_reservation_failed"));
                    }
                    return onOwnerThread(player, () -> {
                        List<ConsumedMaterial> carried = consumeBackpack(player, plan);
                        if (carried == null) {

                            return storageChannel.releaseAsync(handle).thenApply(ignored ->
                                    EmakiResult.rejected("station.insufficient_materials"));
                        }
                        return storageChannel.commitAsync(handle).thenCompose(committed ->
                                onOwnerThread(player, () -> {
                                    if (committed.isFailure()) {
                                        refundBackpack(player, carried);
                                        return CompletableFuture.completedFuture(
                                                committed.<List<ConsumedMaterial>>retypeFailure());
                                    }
                                    return CompletableFuture.completedFuture(
                                            EmakiResult.success(withStorage(carried, plan)));
                                }));
                    });
                });
    }

    private CompletableFuture<EmakiResult<List<ConsumedMaterial>>> debitWithoutReservation(Player player,
            DebitPlan plan) {
        return storageChannel.consumeAmountsAsync(player.getUniqueId(), plan.fromStorage())
                .thenCompose(stored -> {
                    if (stored.isFailure()) {
                        return CompletableFuture.completedFuture(stored.retypeFailure());
                    }
                    return onOwnerThread(player, () -> {
                        List<ConsumedMaterial> carried = consumeBackpack(player, plan);
                        if (carried != null) {
                            return CompletableFuture.completedFuture(
                                    EmakiResult.success(withStorage(carried, plan)));
                        }

                        Map<ItemSourceRef, Long> giveBack = new LinkedHashMap<>(plan.fromStorage());
                        return storageChannel.depositAsync(player.getUniqueId(), giveBack)
                                .thenCompose(accepted -> onOwnerThread(player, () -> {
                                    giveBack.forEach((source, amount) -> {
                                        long returned = accepted.getOrDefault(source, 0L);
                                        if (returned < amount) {
                                            backpackChannel.refund(player, source, amount - returned);
                                        }
                                    });
                                    return CompletableFuture.completedFuture(
                                            EmakiResult.<List<ConsumedMaterial>>rejected(
                                                    "station.insufficient_materials"));
                                }));
                    });
                });
    }

    private List<ConsumedMaterial> consumeBackpack(Player player, DebitPlan plan) {
        if (!plan.fromStacks().isEmpty()) {
            return backpackChannel.consumeStacks(player, plan.fromStacks());
        }
        return backpackChannel.consume(player, plan.fromBackpack());
    }

    private void refundBackpack(Player player, List<ConsumedMaterial> materials) {
        for (ConsumedMaterial material : materials) {
            backpackChannel.refund(player, material, material.amount());
        }
    }

    private static List<ConsumedMaterial> withStorage(List<ConsumedMaterial> carried, DebitPlan plan) {
        List<ConsumedMaterial> all = new ArrayList<>(carried);
        for (SourceDebit debit : plan.allocations()) {
            if (debit.channel() == MaterialChannel.STORAGE && debit.amount() > 0L) {
                all.add(new ConsumedMaterial(debit.materialId(), debit.requirementId(), debit.countKey(),
                        debit.source(), -1, MaterialChannel.STORAGE, debit.amount(), 0L));
            }
        }
        if (plan.allocations().isEmpty()) {
            plan.fromStorage().forEach((source, amount) ->
                    all.add(new ConsumedMaterial(source, amount, MaterialChannel.STORAGE)));
        }
        return all;
    }

    private boolean storageUsable(StationDefinition station) {
        return station.storageChannel()
                && Boolean.TRUE.equals(storageAllowed.get())
                && storageChannel.usable();
    }

    private static long take(Map<ItemSourceRef, Long> pool,
            ItemSourceRef source,
            long needed,
            Map<ItemSourceRef, Long> into) {
        long have = pool.getOrDefault(source, 0L);
        if (have <= 0L) {
            return 0L;
        }
        long taken = Math.min(have, needed);
        pool.put(source, have - taken);
        if (into != null) {
            into.merge(source, taken, MergedMaterialChannel::plus);
        }
        return taken;
    }

    private static long plus(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private <T> CompletableFuture<T> onOwnerThread(Player player,
            Supplier<CompletableFuture<T>> work) {
        CompletableFuture<T> future = new CompletableFuture<>();
        dispatcher.runEntity(plugin, player, () -> {
            try {
                work.get().whenComplete((value, error) -> {
                    if (error != null) {
                        future.completeExceptionally(error);
                    } else {
                        future.complete(value);
                    }
                });
            } catch (RuntimeException failure) {
                future.completeExceptionally(failure);
            }
        }, () -> future.completeExceptionally(
                new IllegalStateException("player retired before station debit ran")));
        return future;
    }
}
