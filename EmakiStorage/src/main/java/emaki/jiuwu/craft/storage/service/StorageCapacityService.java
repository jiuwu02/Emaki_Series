package emaki.jiuwu.craft.storage.service;

import java.util.Locale;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import emaki.jiuwu.craft.storage.api.model.StorageCapacity;
import emaki.jiuwu.craft.storage.config.AppConfig;
import emaki.jiuwu.craft.storage.model.PlayerStorage;
import emaki.jiuwu.craft.storage.model.StorageEntry;
import emaki.jiuwu.craft.storage.model.StorageKey;

/**
 * Resolves capacity, page bounds, the three-level stack limit and multi-slot spans.
 *
 * <p>Slot count is the single source of truth for capacity; page count is derived from it, never
 * the other way round. The four sources are summed then clamped:
 *
 * <pre>
 * effective = clamp(base_slots + permission tier + grantedSlots + purchasedSlots, 0, max_slots)
 * </pre>
 *
 * with the upper clamp skipped when {@code max_slots} is {@code 0} (unlimited).
 *
 * <p>When {@code behavior.multi_slot_stacking} is enabled one entry may occupy several slots, so
 * occupancy is the sum of entry spans rather than the entry count. Span is always <em>derived</em>
 * from {@code amount / limit} and never stored: that is what makes a withdrawal collapse a
 * two-slot entry back into one without any explicit merge step.
 */
public final class StorageCapacityService {

    private static final String SLOTS_PREFIX = "emakistorage.slots.";
    private static final String STACK_LIMIT_PREFIX = "emakistorage.stacklimit.";

    private volatile AppConfig config;

    public StorageCapacityService(AppConfig config) {
        this.config = config;
    }

    public void reconfigure(AppConfig config) {
        if (config != null) {
            this.config = config;
        }
    }

    /**
     * Reads the highest numeric permission tier a player holds.
     *
     * <p>Wildcards are deliberately <strong>not</strong> supported: some permission plugins expand
     * {@code emakistorage.slots.*} into "holds every n", which would silently hand the player the
     * maximum tier.
     *
     * @param player the player to inspect, may be {@code null} for offline targets
     * @param prefix the permission prefix to scan
     * @return the highest suffix value found, or {@code 0} when none
     */
    public static long highestPermissionTier(Player player, String prefix) {
        if (player == null) {
            return 0L;
        }
        long highest = 0L;
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue()) {
                continue;
            }
            String permission = info.getPermission();
            if (permission == null) {
                continue;
            }
            String normalized = permission.toLowerCase(Locale.ROOT);
            if (!normalized.startsWith(prefix)) {
                continue;
            }
            String suffix = normalized.substring(prefix.length());
            if (suffix.isEmpty() || suffix.indexOf('.') >= 0) {
                continue;
            }
            try {
                highest = Math.max(highest, Long.parseLong(suffix));
            } catch (NumberFormatException ignored) {
                // A non-numeric suffix such as '*' is skipped on purpose.
            }
        }
        return highest;
    }

    /** {@return the permission-granted slot bonus, clamped into {@code int} range} */
    public int permissionSlots(Player player) {
        long tier = highestPermissionTier(player, SLOTS_PREFIX);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, tier));
    }

    /** {@return the permission-granted default stack limit, or {@code 0} when none} */
    public long permissionStackLimit(Player player) {
        return highestPermissionTier(player, STACK_LIMIT_PREFIX);
    }

    /**
     * Computes the full capacity breakdown.
     *
     * @param storage      the player's storage
     * @param player       the online player, or {@code null} when offline
     * @param slotsPerPage how many storage slots the active GUI template renders per page
     * @return the breakdown, with {@code effectiveSlots} already clamped
     */
    public StorageCapacity capacityOf(PlayerStorage storage, Player player, int slotsPerPage) {
        AppConfig active = config;
        int base = Math.max(0, active.capacity().baseSlots());
        int permission = permissionSlots(player);
        int granted = storage == null ? 0 : storage.grantedSlots();
        int purchased = storage == null ? 0 : storage.purchasedSlots();
        int maxSlots = Math.max(0, active.capacity().maxSlots());

        long total = (long) base + permission + granted + purchased;
        long clamped = Math.max(0L, total);
        if (maxSlots > 0) {
            clamped = Math.min(clamped, maxSlots);
        }
        int effective = (int) Math.min(Integer.MAX_VALUE, clamped);
        int used = occupiedSlots(storage);
        return new StorageCapacity(base, permission, granted, purchased,
                effective, maxSlots, used, Math.max(1, slotsPerPage));
    }

    /** {@return whether one item kind may span several slots once its per-slot ceiling is full} */
    public boolean multiSlotStacking() {
        return config.behavior().multiSlotStacking();
    }

    /**
     * Resolves how many logical slots one entry occupies.
     *
     * <p>With multi-slot stacking off this is always {@code 1}, which is what makes the whole
     * feature a no-op on existing servers. An unlimited ceiling also stays at {@code 1}: a
     * percentage of infinity has no meaning, so there is nothing to spill into a second slot.
     *
     * @param storage the owning storage, used to resolve the three-level ceiling
     * @param entry   the entry to measure, may be {@code null}
     * @return the occupied slot count, never below {@code 1} for a present entry
     */
    public int slotSpan(PlayerStorage storage, StorageEntry entry) {
        if (entry == null) {
            return 0;
        }
        if (!multiSlotStacking()) {
            return 1;
        }
        long limit = effectiveStackLimit(storage, entry);
        return spanOf(entry.amount(), limit);
    }

    /**
     * {@return how many slots {@code amount} occupies under {@code effectiveLimit}}
     *
     * <p>Ceiling division without floating point: {@code (amount - 1) / limit + 1} cannot lose
     * precision the way {@code Math.ceil} on a {@code double} would once amounts pass 2^53.
     */
    public int spanOf(long amount, long effectiveLimit) {
        if (amount <= 0L) {
            return 1;
        }
        if (unlimited(effectiveLimit) || effectiveLimit <= 0L) {
            return 1;
        }
        long span = (amount - 1L) / effectiveLimit + 1L;
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, span));
    }

    /**
     * {@return how many logical slots the storage currently occupies}
     *
     * <p>With the toggle off this is {@code entryCount()} and stays O(1). With it on the spans of
     * every entry are summed, which is O(entries): occupancy must count spans, or a player could
     * fill 90 slots' worth of items while only 45 were charged against their capacity.
     */
    public int occupiedSlots(PlayerStorage storage) {
        if (storage == null) {
            return 0;
        }
        if (!multiSlotStacking()) {
            return storage.entryCount();
        }
        long occupied = 0L;
        for (StorageKey key : storage.entryOrder()) {
            StorageEntry entry = storage.entry(key);
            if (entry == null) {
                continue;
            }
            occupied += slotSpan(storage, entry);
        }
        return (int) Math.min(Integer.MAX_VALUE, occupied);
    }

    /**
     * {@return the amount visible in one slot of a possibly multi-slot entry}
     *
     * @param amount         the entry's total amount
     * @param effectiveLimit the resolved per-slot ceiling
     * @param spanIndex      the zero-based position inside the entry's span
     */
    public long sliceAmount(long amount, long effectiveLimit, int spanIndex) {
        if (spanIndex <= 0 || unlimited(effectiveLimit) || effectiveLimit <= 0L) {
            return unlimited(effectiveLimit) || effectiveLimit <= 0L
                    ? amount
                    : Math.min(amount, effectiveLimit);
        }
        long consumed = effectiveLimit * spanIndex;
        if (consumed >= amount) {
            return 0L;
        }
        return Math.min(effectiveLimit, amount - consumed);
    }

    /**
     * Resolves the total amount one entry may reach given the slots still available to it.
     *
     * <p>With the toggle off the ceiling is a single slot, which reproduces today's "full slot
     * refuses the surplus" behaviour exactly. With it on the entry may grow into the free slots as
     * well, which is what lets a 120-unit deposit land as 100 + 20 instead of storing 100 and
     * rejecting the rest.
     *
     * @param storage   the owning storage
     * @param entry     the existing entry, or {@code null} when sizing a brand new one
     * @param freeSlots how many slots are still unoccupied
     * @return the total amount ceiling, or {@link Long#MAX_VALUE} when unlimited
     */
    public long spanCeiling(PlayerStorage storage, StorageEntry entry, int freeSlots) {
        long limit = effectiveStackLimit(storage, entry);
        if (unlimited(limit)) {
            return Long.MAX_VALUE;
        }
        if (!multiSlotStacking()) {
            return limit;
        }
        long currentSpan = entry == null ? 0L : slotSpan(storage, entry);
        long slots = currentSpan + Math.max(0, freeSlots);
        if (slots <= 0L) {
            return limit;
        }
        // Saturating multiply: a large ceiling times many free slots must clamp, not wrap negative.
        long ceiling = slots > Long.MAX_VALUE / limit ? Long.MAX_VALUE : slots * limit;
        return Math.max(limit, ceiling);
    }

    /**
     * Resolves the effective per-slot ceiling for one entry.
     *
     * <p>Three levels, most specific first. A {@code 0} at entry or player level means "inherit
     * the next level"; a {@code 0} at config level means "unlimited".
     *
     * @param storage the owning storage
     * @param entry   the entry, may be {@code null} when sizing a not-yet-created entry
     * @return the ceiling, or {@link Long#MAX_VALUE} when unlimited
     */
    public long effectiveStackLimit(PlayerStorage storage, StorageEntry entry) {
        if (entry != null && entry.stackLimit() > 0L) {
            return entry.stackLimit();
        }
        if (storage != null && storage.defaultStackLimit() > 0L) {
            return storage.defaultStackLimit();
        }
        long configured = config.capacity().defaultStackLimit();
        return configured > 0L ? configured : Long.MAX_VALUE;
    }

    /** {@return whether the resolved ceiling means "no limit"} */
    public boolean unlimited(long effectiveLimit) {
        return effectiveLimit >= Long.MAX_VALUE;
    }

    /**
     * Clamps a requested page into the reachable range.
     *
     * <p>Three boundaries matter: page 1 is always reachable so an empty warehouse can still be
     * opened and receive its first item; a page holding no entry at all cannot be paged into; and
     * when the current page becomes empty (the last entry on page 3 was withdrawn) the caller
     * falls back to the last reachable page.
     *
     * @param requestedPage the zero-based page the player asked for
     * @param capacity      the current capacity breakdown
     * @return the clamped zero-based page
     */
    public int clampPage(int requestedPage, StorageCapacity capacity) {
        int lastReachable = Math.min(capacity.reachablePages(), capacity.totalPages());
        int maxIndex = Math.max(0, lastReachable - 1);
        if (requestedPage < 0) {
            return 0;
        }
        return Math.min(requestedPage, maxIndex);
    }

    /**
     * {@return whether paging to a target page is allowed}
     *
     * @param targetPage the zero-based destination
     * @param capacity   the current capacity breakdown
     */
    public boolean canPageTo(int targetPage, StorageCapacity capacity) {
        if (targetPage < 0) {
            return false;
        }
        if (targetPage == 0) {
            return true;
        }
        int lastReachable = Math.min(capacity.reachablePages(), capacity.totalPages());
        return targetPage <= lastReachable - 1;
    }

    /**
     * {@return whether a brand new entry can be created}
     *
     * <p>Only slot headroom matters here. Under the mixed deposit strategy an occupied slot also
     * accepts deposits, so "is there a visually empty slot" is not a precondition — the checks are
     * slot headroom for a new kind, and the stack ceiling for an existing kind.
     */
    public boolean hasFreeSlot(StorageCapacity capacity) {
        return capacity.usedSlots() < capacity.effectiveSlots();
    }

    /** {@return the configured hard ceiling, {@code 0} meaning unlimited} */
    public int maxSlots() {
        return Math.max(0, config.capacity().maxSlots());
    }
}
