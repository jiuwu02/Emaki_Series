package emaki.jiuwu.craft.storage.placeholder;

import java.util.Locale;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.storage.EmakiStoragePlugin;
import emaki.jiuwu.craft.storage.api.model.StorageCapacity;
import emaki.jiuwu.craft.storage.model.PlayerStorage;
import emaki.jiuwu.craft.storage.model.StorageEntry;
import emaki.jiuwu.craft.storage.model.StorageKey;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

/**
 * PlaceholderAPI expansion, registered only when PlaceholderAPI is present.
 *
 * <p>Every placeholder reads from the in-memory storage of an online player. Nothing here loads from
 * disk: a placeholder resolves on whatever thread PAPI calls it from, so blocking IO is not an
 * option, and an unloaded storage simply reports zero.
 */
public final class StoragePlaceholderExpansion extends PlaceholderExpansion {

    private static final String COUNT_PREFIX = "count_";

    private final EmakiStoragePlugin plugin;

    public StoragePlaceholderExpansion(EmakiStoragePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "emakistorage";
    }

    @Override
    public String getAuthor() {
        return "Emaki";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null || Texts.isBlank(params)) {
            return "";
        }
        PlayerStorage storage = plugin.dataStore().cached(player.getUniqueId());
        if (storage == null) {
            return "0";
        }
        String token = params.trim().toLowerCase(Locale.ROOT);
        StorageCapacity capacity = plugin.capacityService()
                .capacityOf(storage, player, plugin.storageGuiService().slotsPerPage());
        if (token.startsWith(COUNT_PREFIX)) {
            return String.valueOf(countOf(storage, token.substring(COUNT_PREFIX.length())));
        }
        return switch (token) {
            case "used_slots" -> String.valueOf(capacity.usedSlots());
            case "total_slots" -> String.valueOf(capacity.effectiveSlots());
            case "free_slots" -> String.valueOf(capacity.freeSlots());
            case "purchased_slots" -> String.valueOf(capacity.purchasedSlots());
            case "granted_slots" -> String.valueOf(capacity.grantedSlots());
            case "pages" -> String.valueOf(capacity.totalPages());
            case "stack_limit" -> String.valueOf(
                    plugin.capacityService().effectiveStackLimit(storage, null));
            case "sort_mode" -> storage.sortMode().id();
            case "total_amount" -> String.valueOf(totalAmount(storage));
            default -> "";
        };
    }

    private long countOf(PlayerStorage storage, String token) {
        if (token.isBlank()) {
            return 0L;
        }
        ItemSource source = ItemSourceUtil.parse(token);
        if (source == null) {
            return 0L;
        }
        ItemStack template = plugin.coreLib().itemSourceService().createItem(source, 1);
        if (template == null || template.getType().isAir()) {
            return 0L;
        }
        StorageEntry entry = storage.entry(StorageKey.of(template));
        return entry == null ? 0L : entry.amount();
    }

    private long totalAmount(PlayerStorage storage) {
        long total = 0L;
        for (StorageKey key : storage.entryOrder()) {
            StorageEntry entry = storage.entry(key);
            if (entry == null) {
                continue;
            }
            long next = total + entry.amount();
            if (next < total) {
                return Long.MAX_VALUE;
            }
            total = next;
        }
        return total;
    }
}
