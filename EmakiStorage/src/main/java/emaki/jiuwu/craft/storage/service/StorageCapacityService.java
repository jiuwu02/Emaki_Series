package emaki.jiuwu.craft.storage.service;

import java.util.Locale;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import emaki.jiuwu.craft.storage.api.model.StorageCapacity;
import emaki.jiuwu.craft.storage.config.AppConfig;
import emaki.jiuwu.craft.storage.model.PlayerStorage;
import emaki.jiuwu.craft.storage.model.StorageEntry;

/**
 * Resolves capacity, page bounds and the three-level stack limit.
 *
 * <p>Slot count is the single source of truth for capacity; page count is derived from it, never
 * the other way round. The four sources are summed then clamped:
 *
 * <pre>
 * effective = clamp(base_slots + permission tier + grantedSlots + purchasedSlots, 0, max_slots)
 * </pre>
 *
 * with the upper clamp skipped when {@code max_slots} is {@code 0} (unlimited).
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
        int used = storage == null ? 0 : storage.entryCount();
        return new StorageCapacity(base, permission, granted, purchased,
                effective, maxSlots, used, Math.max(1, slotsPerPage));
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
