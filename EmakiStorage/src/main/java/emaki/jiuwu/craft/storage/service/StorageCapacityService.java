package emaki.jiuwu.craft.storage.service;

import java.util.Locale;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import emaki.jiuwu.craft.storage.api.model.StorageCapacity;
import emaki.jiuwu.craft.storage.config.AppConfig;
import emaki.jiuwu.craft.storage.model.PlayerStorage;
import emaki.jiuwu.craft.storage.model.StorageEntry;
import emaki.jiuwu.craft.storage.model.StorageKey;

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

            }
        }
        return highest;
    }

    public int permissionSlots(Player player) {
        long tier = highestPermissionTier(player, SLOTS_PREFIX);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, tier));
    }

    public long permissionStackLimit(Player player) {
        return highestPermissionTier(player, STACK_LIMIT_PREFIX);
    }

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

    public boolean multiSlotStacking() {
        return config.behavior().multiSlotStacking();
    }

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

        long ceiling = slots > Long.MAX_VALUE / limit ? Long.MAX_VALUE : slots * limit;
        return Math.max(limit, ceiling);
    }

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

    public boolean unlimited(long effectiveLimit) {
        return effectiveLimit >= Long.MAX_VALUE;
    }

    public int clampPage(int requestedPage, StorageCapacity capacity) {
        int lastReachable = Math.min(capacity.reachablePages(), capacity.totalPages());
        int maxIndex = Math.max(0, lastReachable - 1);
        if (requestedPage < 0) {
            return 0;
        }
        return Math.min(requestedPage, maxIndex);
    }

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

    public boolean hasFreeSlot(StorageCapacity capacity) {
        return capacity.usedSlots() < capacity.effectiveSlots();
    }

    public int maxSlots() {
        return Math.max(0, config.capacity().maxSlots());
    }
}
