package emaki.jiuwu.craft.dungeon.rewardbox;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.dungeon.config.AppConfig;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class RewardBoxService {

    public static final NamespacedKey DROP_OWNER_KEY = new NamespacedKey("emakidungeon", "drop_owner");
    public static final String OWNER_DISCARD = "discard";

    private static final int MAX_PAGE_NODES = 32;
    private static final int CONTENT_SLOTS = 45;

    private final RewardBoxStore store;
    private final MessageService messages;
    private final Supplier<AppConfig> appConfig;

    private volatile int defaultPages = 1;
    private volatile int capacitySlots = 135;

    public RewardBoxService(RewardBoxStore store,
            MessageService messages,
            Supplier<AppConfig> appConfig) {
        this.store = store;
        this.messages = messages;
        this.appConfig = appConfig;
    }

    public void loadGuiConfig(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "gui" + File.separator + "rewardbox.yml");
        try {
            YamlFiles.copyResourceIfMissing(plugin, "gui/rewardbox.yml", file);
        } catch (IOException ignored) {
        }
        if (!file.isFile()) {
            return;
        }
        YamlSection section = YamlFiles.load(file);
        if (section == null) {
            return;
        }
        Integer pages = section.getInt("default-pages", 1);
        Integer capacity = section.getInt("capacity-slots", 135);
        defaultPages = pages == null || pages < 1 ? 1 : pages;
        capacitySlots = capacity == null || capacity < 1 ? 135 : capacity;
    }

    public RewardBoxStore store() {
        return store;
    }

    public boolean enabled() {
        return appConfig.get().rewardboxEnabled();
    }

    public int capacitySlots() {
        return capacitySlots;
    }

    public void storeBox(UUID playerId, List<ItemStack> items) {
        RewardBoxStore.Box box = store.load(playerId);
        append(playerId, box, items);
    }

    public List<ItemStack> deposit(UUID playerId, List<ItemStack> items) {
        List<ItemStack> accepted = new ArrayList<>();
        for (ItemStack stack : items) {
            if (stack != null && stack.getAmount() > 0) {
                accepted.add(stack);
            }
        }
        if (accepted.isEmpty()) {
            return List.of();
        }
        RewardBoxStore.Box box = store.load(playerId);
        boolean direct = enabled() ? box.autoFlow() : true;
        Player player = Bukkit.getPlayer(playerId);
        if (direct && player == null) {
            return accepted;
        }
        if (direct) {
            List<ItemStack> leftover = new ArrayList<>();
            for (ItemStack stack : accepted) {
                for (ItemStack rest : player.getInventory().addItem(stack).values()) {
                    leftover.add(rest);
                }
            }
            if (!enabled()) {
                return leftover;
            }
            List<ItemStack> still = append(playerId, box, leftover);
            return still;
        }
        return append(playerId, box, accepted);
    }

    private List<ItemStack> append(UUID playerId, RewardBoxStore.Box box, List<ItemStack> items) {
        List<ItemStack> rejected = new ArrayList<>();
        for (ItemStack stack : items) {
            if (box.items().size() >= capacitySlots) {
                rejected.add(stack);
                continue;
            }
            box.items().add(stack);
        }
        if (rejected.size() != items.size()) {
            store.saveAsync(playerId, box);
        }
        return rejected;
    }

    public void deliverOrDrop(UUID playerId, List<ItemStack> items,
            World fallbackWorld, Location fallbackLocation) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<ItemStack> leftover = deposit(playerId, items);
        if (leftover.isEmpty() || fallbackWorld == null || fallbackLocation == null) {
            return;
        }
        for (ItemStack stack : leftover) {
            Item dropped = fallbackWorld.dropItemNaturally(fallbackLocation, stack);
            dropped.getPersistentDataContainer().set(DROP_OWNER_KEY,
                    PersistentDataType.STRING, OWNER_DISCARD);
        }
    }

    public boolean isAutoFlow(UUID playerId) {
        return store.load(playerId).autoFlow();
    }

    public void setAutoFlow(UUID playerId, boolean autoFlow) {
        RewardBoxStore.Box box = store.load(playerId);
        box.setAutoFlow(autoFlow);
        store.saveAsync(playerId, box);
    }

    public int maxPages(Player player) {
        int pages = Math.max(1, defaultPages);
        for (int candidate = MAX_PAGE_NODES; candidate > pages; candidate--) {
            if (player.hasPermission("emakidungeon.rewardbox.pages." + candidate)) {
                pages = candidate;
                break;
            }
        }
        int capacityPages = (capacitySlots + CONTENT_SLOTS - 1) / CONTENT_SLOTS;
        return Math.min(pages, Math.max(1, capacityPages));
    }

    public int visibleCapacity(Player player) {
        return Math.min(maxPages(player) * CONTENT_SLOTS, capacitySlots);
    }

    public int contentSlots() {
        return CONTENT_SLOTS;
    }

    public void notifyStored(Player player, String itemLabel, int amount) {
        messages.send(player, "drop.box_stored", Map.of(
                "item", itemLabel,
                "amount", String.valueOf(amount)
        ));
    }
}
