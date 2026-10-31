package emaki.jiuwu.craft.dungeon.drop;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;
import emaki.jiuwu.craft.dungeon.instance.WorldCommand;
import emaki.jiuwu.craft.dungeon.instance.WorldCommandTarget;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceRegistry;
import java.util.LinkedHashMap;

public final class RewardChestService implements Listener {

    public static final String CHEST_METADATA = "emd_reward_chest";

    private final Plugin plugin;
    private final InstanceRegistry registry;
    private final MessageService messages;

    private final Map<String, List<ItemStack>> pools = new ConcurrentHashMap<>();
    private final Map<String, Map<UUID, List<ItemStack>>> staging = new ConcurrentHashMap<>();
    private final Map<String, Boolean> chestPlaced = new ConcurrentHashMap<>();

    public RewardChestService(Plugin plugin, InstanceRegistry registry, MessageService messages) {
        this.plugin = plugin;
        this.registry = registry;
        this.messages = messages;
    }

    public void addPool(DungeonInstance instance, List<ItemStack> drops) {
        if (instance == null || drops == null || drops.isEmpty()) {
            return;
        }
        List<ItemStack> pool = pools.computeIfAbsent(instance.instanceId(),
                key -> new ArrayList<>());
        List<ItemStack> visual = new ArrayList<>(drops.size());
        synchronized (pool) {
            for (ItemStack stack : drops) {
                if (stack == null || stack.getAmount() <= 0) {
                    continue;
                }
                pool.add(stack.clone());
                visual.add(stack.clone());
            }
        }
        if (visual.isEmpty()) {
            return;
        }
        boolean first = chestPlaced.putIfAbsent(instance.instanceId(), Boolean.TRUE) == null;
        instance.commands().submit(new WorldCommand() {
            @Override
            public int blockCost() {
                return 1;
            }

            @Override
            public WorldCommandTarget target() {
                return WorldCommandTarget.LOCATION;
            }

            @Override
            public String worldName() {
                return instance.instanceId();
            }

            @Override
            public void execute() {
                World world = Bukkit.getWorld(instance.instanceId());
                if (world == null) {
                    return;
                }
                Block block = chestBlock(world, instance);
                if (block == null) {
                    return;
                }
                if (first || block.getType() != Material.CHEST) {
                    block.setType(Material.CHEST, false);
                    block.setMetadata(CHEST_METADATA,
                            new FixedMetadataValue(plugin, Boolean.TRUE));
                }
                if (block.getState() instanceof Chest chest) {
                    chest.getInventory().addItem(visual.toArray(new ItemStack[0]));
                }
            }
        });
    }

    public List<ItemStack> drainPool(String instanceId) {
        if (instanceId == null) {
            return List.of();
        }
        List<ItemStack> pool = pools.remove(instanceId);
        chestPlaced.remove(instanceId);
        if (pool == null) {
            return List.of();
        }
        synchronized (pool) {
            return new ArrayList<>(pool);
        }
    }

    public void clearVisual(DungeonInstance instance) {
        if (instance == null) {
            return;
        }
        instance.commands().submit(new WorldCommand() {
            @Override
            public int blockCost() {
                return 1;
            }

            @Override
            public WorldCommandTarget target() {
                return WorldCommandTarget.LOCATION;
            }

            @Override
            public String worldName() {
                return instance.instanceId();
            }

            @Override
            public void execute() {
                World world = Bukkit.getWorld(instance.instanceId());
                if (world == null) {
                    return;
                }
                Block block = chestBlock(world, instance);
                if (block != null && block.getState() instanceof Chest chest) {
                    chest.getInventory().clear();
                    block.removeMetadata(CHEST_METADATA, plugin);
                    block.setType(Material.AIR, false);
                }
            }
        });
    }

    public void stage(String instanceId, UUID owner, List<ItemStack> items) {
        if (instanceId == null || owner == null || items == null || items.isEmpty()) {
            return;
        }
        Map<UUID, List<ItemStack>> byPlayer = staging.computeIfAbsent(instanceId,
                key -> new ConcurrentHashMap<>());
        List<ItemStack> bucket = byPlayer.computeIfAbsent(owner, key -> new ArrayList<>());
        synchronized (bucket) {
            for (ItemStack stack : items) {
                if (stack == null || stack.getAmount() <= 0) {
                    continue;
                }
                bucket.add(stack.clone());
            }
        }
    }

    public Map<UUID, List<ItemStack>> drainStaging(String instanceId) {
        if (instanceId == null) {
            return Map.of();
        }
        Map<UUID, List<ItemStack>> byPlayer = staging.remove(instanceId);
        if (byPlayer == null) {
            return Map.of();
        }
        Map<UUID, List<ItemStack>> drained = new LinkedHashMap<>();
        for (Map.Entry<UUID, List<ItemStack>> entry : byPlayer.entrySet()) {
            List<ItemStack> bucket = entry.getValue();
            List<ItemStack> copy;
            synchronized (bucket) {
                copy = new ArrayList<>(bucket);
            }
            if (!copy.isEmpty()) {
                drained.put(entry.getKey(), copy);
            }
        }
        return drained;
    }

    public boolean clearStaging(String instanceId, UUID owner) {
        if (instanceId == null || owner == null) {
            return false;
        }
        Map<UUID, List<ItemStack>> byPlayer = staging.get(instanceId);
        if (byPlayer == null) {
            return false;
        }
        List<ItemStack> bucket = byPlayer.remove(owner);
        if (byPlayer.isEmpty()) {
            staging.remove(instanceId, byPlayer);
        }
        if (bucket == null) {
            return false;
        }
        synchronized (bucket) {
            return !bucket.isEmpty();
        }
    }

    public void remove(String instanceId) {
        if (instanceId != null) {
            pools.remove(instanceId);
            staging.remove(instanceId);
            chestPlaced.remove(instanceId);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getInventory().getHolder() instanceof BlockState state)) {
            return;
        }
        if (!state.getBlock().hasMetadata(CHEST_METADATA)) {
            return;
        }
        event.setCancelled(true);
        if (event.getPlayer() instanceof Player player) {
            messages.send(player, "drop.chest_locked", Map.of());
        }
    }

    private Block chestBlock(World world, DungeonInstance instance) {
        var runtime = instance.flowRuntime();
        var spawnPoint = runtime == null ? null : runtime.definition().spawnPoint();
        if (spawnPoint != null && spawnPoint.point() != null) {
            var point = spawnPoint.point();
            return world.getBlockAt((int) Math.floor(point.x()),
                    (int) Math.floor(point.y()), (int) Math.floor(point.z()));
        }
        return world.getBlockAt(world.getSpawnLocation());
    }
}
