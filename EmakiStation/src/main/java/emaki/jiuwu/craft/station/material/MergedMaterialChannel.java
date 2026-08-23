package emaki.jiuwu.craft.station.material;

import java.time.Duration;
import java.util.ArrayList;
import java.util.IdentityHashMap;
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
import emaki.jiuwu.craft.corelib.matcher.MaterialAllocation;
import emaki.jiuwu.craft.station.api.model.ConsumedMaterial;
import emaki.jiuwu.craft.station.api.model.MaterialChannel;
import emaki.jiuwu.craft.station.definition.StationDefinition;
import emaki.jiuwu.craft.station.recipe.MaterialRequirement;
import emaki.jiuwu.craft.station.recipe.RecipeDefinition;
import emaki.jiuwu.craft.station.recipe.RecipeMatcher;
import emaki.jiuwu.craft.storage.api.model.ReservationHandle;

public final class MergedMaterialChannel {

    private static final Duration RESERVATION_TTL = Duration.ofSeconds(30L);

    public record Availability(Map<ItemSourceRef, Long> backpack, Map<ItemSourceRef, Long> storage) {

        public Availability {
            backpack = backpack == null ? Map.of() : Map.copyOf(backpack);
            storage = storage == null ? Map.of() : Map.copyOf(storage);
        }

        public static Availability empty() {
            return new Availability(Map.of(), Map.of());
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

    public record StackDebit(int slotIndex, ItemStack expected, ItemSourceRef source, int amount) {

        public StackDebit {
            expected = expected == null ? null : expected.clone();
        }
    }

    public record DebitPlan(Map<ItemSourceRef, Long> fromBackpack,
            Map<ItemSourceRef, Long> fromStorage,
            List<StackDebit> fromStacks) {

        public DebitPlan {
            fromBackpack = fromBackpack == null ? Map.of() : Map.copyOf(fromBackpack);
            fromStorage = fromStorage == null ? Map.of() : Map.copyOf(fromStorage);
            fromStacks = fromStacks == null ? List.of() : List.copyOf(fromStacks);
        }

        public DebitPlan(Map<ItemSourceRef, Long> fromBackpack, Map<ItemSourceRef, Long> fromStorage) {
            this(fromBackpack, fromStorage, List.of());
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
            return CompletableFuture.completedFuture(new Availability(carried, Map.of()));
        }
        return storageChannel.countSourcesAsync(player.getUniqueId(), sources)
                .thenApply(stored -> new Availability(carried, stored));
    }

    public DebitPlan plan(RecipeDefinition recipe, long batch, Availability availability) {
        if (recipe == null || availability == null) {
            return null;
        }
        Map<ItemSourceRef, Long> carried = new LinkedHashMap<>(availability.backpack());
        Map<ItemSourceRef, Long> stored = new LinkedHashMap<>(availability.storage());
        Map<ItemSourceRef, Long> fromBackpack = new LinkedHashMap<>();
        Map<ItemSourceRef, Long> fromStorage = new LinkedHashMap<>();
        for (MaterialRequirement requirement : recipe.requirements()) {
            long needed = requirement.totalFor(batch);
            for (ItemSourceRef source : requirement.sources()) {
                if (needed <= 0L) {
                    break;
                }
                needed -= take(carried, source, needed,
                        requirement.consume() ? fromBackpack : null);
                if (needed <= 0L) {
                    break;
                }
                needed -= take(stored, source, needed,
                        requirement.consume() ? fromStorage : null);
            }
            if (needed > 0L) {
                return null;
            }
        }
        return new DebitPlan(fromBackpack, fromStorage);
    }

    public DebitPlan plan(Player player, RecipeDefinition recipe, long batch, Availability availability) {
        if (recipe == null) {
            return null;
        }
        if (!matcherDriven(recipe)) {
            return plan(recipe, batch, availability);
        }
        if (player == null) {
            return null;
        }
        List<BackpackChannel.SlotStack> snapshot = backpackChannel.snapshotStacks(player);
        MaterialAllocation allocation = RecipeMatcher.allocate(recipe,
                stacksOf(snapshot),
                stack -> backpackChannel.contextOf(player, stack),
                batch);
        if (!allocation.satisfied()) {
            return null;
        }
        return debitPlanOf(recipe, snapshot, allocation);
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
        if (!requirement.sources().isEmpty()) {
            return availability == null ? 0L : availability.totalOf(requirement);
        }
        if (player == null || !requirement.hasMatcher()) {
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
        if (recipe == null) {
            return 0L;
        }
        if (!matcherDriven(recipe)) {
            return maxBatch(recipe, availability);
        }
        if (player == null) {
            return 0L;
        }
        List<BackpackChannel.SlotStack> snapshot = backpackChannel.snapshotStacks(player);
        return RecipeMatcher.maxBatch(recipe,
                stacksOf(snapshot),
                stack -> backpackChannel.contextOf(player, stack),
                stackCeilingOf(snapshot));
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

    private static List<ItemStack> stacksOf(List<BackpackChannel.SlotStack> snapshot) {
        List<ItemStack> stacks = new ArrayList<>(snapshot.size());
        for (BackpackChannel.SlotStack slotStack : snapshot) {
            stacks.add(slotStack.stack());
        }
        return stacks;
    }

    private static long stackCeilingOf(List<BackpackChannel.SlotStack> snapshot) {
        long ceiling = 0L;
        for (BackpackChannel.SlotStack slotStack : snapshot) {
            ceiling = plus(ceiling, slotStack.stack().getAmount());
        }
        return ceiling;
    }

    private DebitPlan debitPlanOf(RecipeDefinition recipe,
            List<BackpackChannel.SlotStack> snapshot,
            MaterialAllocation allocation) {
        Map<ItemStack, Integer> slots = new IdentityHashMap<>();
        for (BackpackChannel.SlotStack slotStack : snapshot) {
            slots.put(slotStack.stack(), slotStack.slotIndex());
        }
        List<MaterialRequirement> requirements = recipe.requirements();
        List<StackDebit> debits = new ArrayList<>();
        for (MaterialAllocation.Assignment assignment : allocation.assignments()) {
            int index = assignment.requirementIndex();
            if (index < 0 || index >= requirements.size()) {
                return null;
            }
            if (!requirements.get(index).consume() || assignment.amount() <= 0) {
                continue;
            }
            Integer slotIndex = slots.get(assignment.stack());
            if (slotIndex == null) {
                return null;
            }
            ItemSourceRef source = backpackChannel.identify(assignment.stack());
            if (source == null) {
                return null;
            }
            debits.add(new StackDebit(slotIndex, assignment.stack(), source, assignment.amount()));
        }
        return new DebitPlan(Map.of(), Map.of(), debits);
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
        if (plan.stackResolved()) {
            List<ConsumedMaterial> consumed = backpackChannel.consumeStacks(player, plan.fromStacks());
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
                        List<ConsumedMaterial> carried =
                                backpackChannel.consume(player, plan.fromBackpack());
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
                    List<ConsumedMaterial> storedMaterials = stored.orElse(List.of());
                    return onOwnerThread(player, () -> {
                        List<ConsumedMaterial> carried =
                                backpackChannel.consume(player, plan.fromBackpack());
                        if (carried != null) {
                            List<ConsumedMaterial> all = new ArrayList<>(carried);
                            all.addAll(storedMaterials);
                            return CompletableFuture.completedFuture(EmakiResult.success(all));
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

    private void refundBackpack(Player player, List<ConsumedMaterial> materials) {
        for (ConsumedMaterial material : materials) {
            backpackChannel.refund(player, material.source(), material.amount());
        }
    }

    private static List<ConsumedMaterial> withStorage(List<ConsumedMaterial> carried, DebitPlan plan) {
        List<ConsumedMaterial> all = new ArrayList<>(carried);
        plan.fromStorage().forEach((source, amount) ->
                all.add(new ConsumedMaterial(source, amount, MaterialChannel.STORAGE)));
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
