package emaki.jiuwu.craft.station.material;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.station.api.model.ConsumedMaterial;
import emaki.jiuwu.craft.station.api.model.MaterialChannel;
import emaki.jiuwu.craft.station.definition.StationDefinition;
import emaki.jiuwu.craft.station.recipe.MaterialRequirement;
import emaki.jiuwu.craft.station.recipe.RecipeDefinition;
import emaki.jiuwu.craft.station.recipe.RecipeMatcher;
import emaki.jiuwu.craft.storage.api.model.ReservationHandle;

/**
 * Treats a player's inventory and their warehouse as one pool of materials.
 *
 * <h2>Why the inventory is spent first</h2>
 * Inventory stock is the stock the player can see. Spending it before warehouse stock means the visible number
 * moves first, which matches what a player expects, and it keeps the warehouse — the slower, asynchronous,
 * capacity-limited side — out of the transaction entirely whenever the inventory alone can pay.
 *
 * <h2>How a two-backend debit stays recoverable</h2>
 * The inventory debit is synchronous and reversible; the warehouse debit is asynchronous and, once applied,
 * only reversible by depositing items back, which can itself be refused for lack of capacity. Committing the
 * warehouse first would therefore turn a recoverable failure into an unrecoverable one.
 *
 * <p>So the warehouse side is <em>reserved</em> rather than applied: the hold excludes those units from
 * anything else, the inventory is debited next, and only then is the hold committed. A failure at any step
 * releases the hold, which cannot fail for capacity because nothing has actually moved.
 *
 * <p>When the warehouse cannot reserve, the fallback applies the warehouse batch first and deposits it back on
 * a later failure. That path is documented as inferior for exactly the reason above; it exists so an older
 * EmakiStorage build still works rather than losing the warehouse side of every recipe.
 */
public final class MergedMaterialChannel {

    /** How long a warehouse hold survives without a commit. The commit follows within the same chain. */
    private static final Duration RESERVATION_TTL = Duration.ofSeconds(30L);

    /**
     * A point-in-time view of what a player can spend.
     *
     * @param backpack the units held in the player's inventory, per identity
     * @param storage  the units held in the player's warehouse, per identity
     */
    public record Availability(Map<ItemSourceRef, Long> backpack, Map<ItemSourceRef, Long> storage) {

        /**
         * Creates an availability snapshot with defensively copied maps.
         *
         * @param backpack the inventory counts; {@code null} becomes empty
         * @param storage  the warehouse counts; {@code null} becomes empty
         */
        public Availability {
            backpack = backpack == null ? Map.of() : Map.copyOf(backpack);
            storage = storage == null ? Map.of() : Map.copyOf(storage);
        }

        /** {@return an empty snapshot} */
        public static Availability empty() {
            return new Availability(Map.of(), Map.of());
        }

        /**
         * Reads the combined units available for one identity.
         *
         * @param source the identity to read
         * @return the inventory units plus the warehouse units
         */
        public long totalOf(ItemSourceRef source) {
            long carried = backpack.getOrDefault(source, 0L);
            long stored = storage.getOrDefault(source, 0L);
            try {
                return Math.addExact(carried, stored);
            } catch (ArithmeticException overflow) {
                return Long.MAX_VALUE;
            }
        }

        /**
         * Reads the combined units available for one requirement's whole "any of" set.
         *
         * @param requirement the requirement to size
         * @return the combined units across every accepted identity
         */
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

        /** {@return the combined counts per identity, for callers that want one flat map} */
        public Map<ItemSourceRef, Long> combined() {
            Map<ItemSourceRef, Long> merged = new LinkedHashMap<>(backpack);
            storage.forEach((source, amount) -> merged.merge(source, amount, MergedMaterialChannel::plus));
            return merged;
        }
    }

    /**
     * A planned split of one debit across the two backends.
     *
     * @param fromBackpack the units to take from the inventory, per identity
     * @param fromStorage  the units to take from the warehouse, per identity
     */
    public record DebitPlan(Map<ItemSourceRef, Long> fromBackpack, Map<ItemSourceRef, Long> fromStorage) {

        /**
         * Creates a plan with defensively copied maps.
         *
         * @param fromBackpack the inventory side; {@code null} becomes empty
         * @param fromStorage  the warehouse side; {@code null} becomes empty
         */
        public DebitPlan {
            fromBackpack = fromBackpack == null ? Map.of() : Map.copyOf(fromBackpack);
            fromStorage = fromStorage == null ? Map.of() : Map.copyOf(fromStorage);
        }

        /** {@return whether the warehouse is involved at all} */
        public boolean touchesStorage() {
            return !fromStorage.isEmpty();
        }

        /** {@return whether this plan takes nothing, which happens when every requirement is non-consuming} */
        public boolean empty() {
            return fromBackpack.isEmpty() && fromStorage.isEmpty();
        }
    }

    private final Plugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final BackpackChannel backpackChannel;
    private final StorageChannel storageChannel;
    private final Supplier<Boolean> storageAllowed;

    /**
     * Creates the merged channel.
     *
     * @param plugin          the owning plugin, used as the scheduling owner
     * @param dispatcher      CoreLib's execution dispatcher
     * @param backpackChannel the inventory side
     * @param storageChannel  the warehouse side
     * @param storageAllowed  whether the warehouse may be consulted at all right now
     */
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

    /**
     * Reads what a player can currently spend on one recipe.
     *
     * <p>The inventory side is counted synchronously on the caller's thread, which must be the player's owner
     * thread. The warehouse side costs one round trip for the whole requirement list rather than one per
     * identity.
     *
     * <p><strong>Thread:</strong> the player's owner thread. The future carries no completion-thread
     * guarantee.
     *
     * @param player  the player to inspect
     * @param station the station being used, which decides whether each side is permitted
     * @param recipe  the recipe whose identities matter
     * @return a future carrying the snapshot
     */
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

    /**
     * Plans how one submission would be paid for, inventory first.
     *
     * <p>Allocation is greedy with no backtracking, matching the rest of the module: when one identity could
     * satisfy two requirements it goes to whichever declared it first. Non-consuming requirements are checked
     * for coverage but never enter the plan.
     *
     * @param recipe       the recipe being submitted
     * @param batch        how many times to apply it
     * @param availability the snapshot to spend against
     * @return the plan, or {@code null} when the snapshot does not cover the recipe
     */
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

    /**
     * Computes how many batches a snapshot supports.
     *
     * <p>Each requirement is divided independently, which is exact when identities are not shared between
     * requirements and conservative when they are — the same trade the single-channel matcher makes.
     *
     * @param recipe       the recipe to size
     * @param availability the snapshot to size against
     * @return the largest supported batch, possibly zero
     */
    public long maxBatch(RecipeDefinition recipe, Availability availability) {
        if (recipe == null || availability == null) {
            return 0L;
        }
        return RecipeMatcher.maxBatch(recipe, availability.combined());
    }

    /**
     * Debits a plan from both backends, or from neither.
     *
     * <p>Ordering is the whole point of this method; see the class documentation. The returned receipt records
     * which backend each unit came from so a refund can return it to the same place.
     *
     * <p><strong>Thread:</strong> the player's owner thread. The future completes on the owner thread.
     *
     * @param player the player to debit
     * @param plan   the planned split
     * @return a future carrying the debited materials, or an explicit failure that took nothing
     */
    public CompletableFuture<EmakiResult<List<ConsumedMaterial>>> debitAsync(Player player, DebitPlan plan) {
        if (player == null || plan == null) {
            return CompletableFuture.completedFuture(
                    EmakiResult.invalidInput("station.submit_bad_request"));
        }
        if (plan.empty()) {
            return CompletableFuture.completedFuture(EmakiResult.success(List.of()));
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
                            // Nothing has moved in the warehouse yet, so dropping the hold restores the
                            // world exactly as it was.
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
                        // The warehouse side is already gone. Putting it back may be refused for capacity,
                        // in which case the units are handed to the player rather than dropped from the
                        // ledger; this is why the reservation path is preferred.
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

    /**
     * Takes as much as possible of one identity out of a pool.
     *
     * @param pool   the remaining counts, mutated in place
     * @param source the identity to draw on
     * @param needed how much is still required
     * @param into   where to record the draw, or {@code null} to check coverage without recording
     * @return how much was drawn
     */
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
