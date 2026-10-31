package emaki.jiuwu.craft.corelib.pdc;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.pdc.PdcKeyMigration;
import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;

public final class PdcConvertScanner {

    public enum Scope {
        PLAYERS,
        CONTAINERS,
        ENTITIES
    }

    public record ScopeReport(int scanned, int migrated, int skipped, int failed) {
    }

    private static final int CHUNKS_PER_TICK = 16;
    private static final int PLAYERS_PER_TICK = 8;
    private static final int MAX_NESTED_DEPTH = 4;

    private final Plugin plugin;
    private final ExecutionDispatcher dispatcher;

    public PdcConvertScanner(Plugin plugin, ExecutionDispatcher dispatcher) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
    }

    public CompletableFuture<Map<Scope, ScopeReport>> convertAsync(Set<Scope> scopes, boolean dryRun) {
        Map<Scope, Tally> tallies = new EnumMap<>(Scope.class);
        for (Scope scope : scopes) {
            tallies.put(scope, new Tally());
        }
        List<CompletableFuture<Void>> phases = new ArrayList<>();
        phases.add(dispatchPlayers(tallies.get(Scope.PLAYERS), dryRun));
        phases.add(dispatchChunks(tallies.get(Scope.CONTAINERS), tallies.get(Scope.ENTITIES), dryRun));
        return CompletableFuture.allOf(phases.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> freeze(tallies));
    }

    private CompletableFuture<Void> dispatchPlayers(Tally tally, boolean dryRun) {
        if (tally == null) {
            return CompletableFuture.completedFuture(null);
        }
        return dispatcher.submitGlobal(plugin, () -> {
            List<CompletableFuture<Void>> pending = new ArrayList<>();
            int index = 0;
            for (Player player : Bukkit.getOnlinePlayers()) {
                pending.add(dispatchPlayer(player, tally, dryRun, index / PLAYERS_PER_TICK));
                index++;
            }
            return List.copyOf(pending);
        }).thenCompose(PdcConvertScanner::combine);
    }

    private CompletableFuture<Void> dispatchChunks(Tally containerTally, Tally entityTally, boolean dryRun) {
        if (containerTally == null && entityTally == null) {
            return CompletableFuture.completedFuture(null);
        }
        return dispatcher.submitGlobal(plugin, () -> {
            List<CompletableFuture<Void>> pending = new ArrayList<>();
            for (World world : Bukkit.getWorlds()) {
                pending.add(dispatchWorld(world, containerTally, entityTally, dryRun));
            }
            return List.copyOf(pending);
        }).thenCompose(PdcConvertScanner::combine);
    }

    private CompletableFuture<Void> dispatchWorld(World world,
            Tally containerTally,
            Tally entityTally,
            boolean dryRun) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        Runnable collect = () -> {
            List<CompletableFuture<Void>> pending = new ArrayList<>();
            try {
                int index = 0;
                for (Chunk chunk : world.getLoadedChunks()) {
                    pending.add(dispatchChunk(world, chunk.getX(), chunk.getZ(),
                            containerTally, entityTally, dryRun, index / CHUNKS_PER_TICK));
                    index++;
                }
            } catch (Throwable throwable) {
                failChunk(containerTally, entityTally, world.getName(), throwable);
                done.complete(null);
                return;
            }
            combine(pending).whenComplete((ignored, throwable) -> done.complete(null));
        };
        TaskToken token;
        try {
            token = dispatcher.runAtLocation(plugin, worldAnchor(world), collect);
        } catch (Throwable throwable) {
            failChunk(containerTally, entityTally, world.getName(), throwable);
            done.complete(null);
            return done;
        }
        if (token == null || token.cancelled()) {
            failChunk(containerTally, entityTally, world.getName(), null);
            done.complete(null);
        }
        return done;
    }

    private CompletableFuture<Void> dispatchPlayer(Player player, Tally tally, boolean dryRun, long delayTicks) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        Runnable convert = () -> {
            try {
                if (player.isOnline()) {
                    convertPlayer(player, tally, dryRun);
                }
            } catch (Throwable throwable) {
                tally.failed().incrementAndGet();
                plugin.getLogger().warning("[persistence] 玩家 " + player.getName() + " 的 PDC 转换失败"
                        + ": " + describe(throwable));
            } finally {
                done.complete(null);
            }
        };
        TaskToken token;
        try {
            token = dispatcher.runEntityLater(plugin, player, convert, () -> done.complete(null), delayTicks);
        } catch (Throwable throwable) {
            tally.failed().incrementAndGet();
            plugin.getLogger().warning("[persistence] 玩家 " + player.getName() + " 的 PDC 转换调度失败"
                    + ": " + describe(throwable));
            done.complete(null);
            return done;
        }
        if (token == null || token.cancelled()) {
            tally.failed().incrementAndGet();
            done.complete(null);
        }
        return done;
    }

    private CompletableFuture<Void> dispatchChunk(World world,
            int chunkX,
            int chunkZ,
            Tally containerTally,
            Tally entityTally,
            boolean dryRun,
            long delayTicks) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        String position = world.getName() + " " + chunkX + "," + chunkZ;
        Location anchor = new Location(world, (chunkX << 4) + 8D, 0D, (chunkZ << 4) + 8D);
        Runnable convert = () -> {
            try {
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    Tally target = activeChunkTally(containerTally, entityTally);
                    if (target != null) {
                        target.failed().incrementAndGet();
                    }
                    return;
                }
                Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                if (containerTally != null) {
                    convertContainers(chunk, containerTally, dryRun);
                }
                if (entityTally != null) {
                    convertEntities(chunk, entityTally, dryRun);
                }
            } catch (Throwable throwable) {
                failChunk(containerTally, entityTally, position, throwable);
            } finally {
                done.complete(null);
            }
        };
        TaskToken token;
        try {
            token = dispatcher.runAtLocationLater(plugin, anchor, convert, delayTicks);
        } catch (Throwable throwable) {
            failChunk(containerTally, entityTally, position, throwable);
            done.complete(null);
            return done;
        }
        if (token == null || token.cancelled()) {
            failChunk(containerTally, entityTally, position, null);
            done.complete(null);
        }
        return done;
    }

    private static Tally activeChunkTally(Tally containerTally, Tally entityTally) {
        return containerTally != null ? containerTally : entityTally;
    }

    private void failChunk(Tally containerTally, Tally entityTally, String position, Throwable throwable) {
        Tally target = activeChunkTally(containerTally, entityTally);
        if (target != null) {
            target.failed().incrementAndGet();
        }
        String detail = throwable == null ? "调度被拒绝" : describe(throwable);
        plugin.getLogger().warning("[persistence] 区块 " + position + " 的 PDC 转换失败: " + detail);
    }

    private static CompletableFuture<Void> combine(List<CompletableFuture<Void>> pending) {
        if (pending == null || pending.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new));
    }

    private static Location worldAnchor(World world) {
        return new Location(world, 0.5D, 0.0D, 0.5D);
    }

    private void convertPlayer(Player player, Tally tally, boolean dryRun) {
        record(tally, PdcKeyMigration.migrateAll(player.getPersistentDataContainer(), dryRun));
        convertInventory(player.getInventory(), tally, dryRun);
        convertInventory(player.getEnderChest(), tally, dryRun);
    }

    private void convertContainers(Chunk chunk, Tally tally, boolean dryRun) {
        for (BlockState state : chunk.getTileEntities(false)) {
            if (!(state instanceof Container container)) {
                continue;
            }
            convertInventory(container.getInventory(), tally, dryRun);
        }
    }

    private void convertEntities(Chunk chunk, Tally tally, boolean dryRun) {
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Player) {
                continue;
            }
            record(tally, PdcKeyMigration.migrateAll(entity.getPersistentDataContainer(), dryRun));
            convertEntityItems(entity, tally, dryRun);
        }
    }

    private void convertEntityItems(Entity entity, Tally tally, boolean dryRun) {
        if (entity instanceof Item dropped) {
            ItemStack stack = dropped.getItemStack();
            if (convertStack(stack, tally, dryRun, 0) && !dryRun) {
                dropped.setItemStack(stack);
            }
            return;
        }
        if (entity instanceof ItemFrame frame) {
            ItemStack stack = frame.getItem();
            if (convertStack(stack, tally, dryRun, 0) && !dryRun) {
                frame.setItem(stack, false);
            }
            return;
        }
        if (entity instanceof LivingEntity living) {
            convertEquipment(living.getEquipment(), tally, dryRun);
        }
        if (entity instanceof InventoryHolder holder) {
            convertInventory(holder.getInventory(), tally, dryRun);
        }
    }

    private void convertEquipment(EntityEquipment equipment, Tally tally, boolean dryRun) {
        if (equipment == null) {
            return;
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack;
            try {
                stack = equipment.getItem(slot);
            } catch (IllegalArgumentException unsupportedSlot) {
                continue;
            }
            if (convertStack(stack, tally, dryRun, 0) && !dryRun) {
                equipment.setItem(slot, stack, true);
            }
        }
    }

    private void convertInventory(Inventory inventory, Tally tally, boolean dryRun) {
        if (inventory == null) {
            return;
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (convertStack(stack, tally, dryRun, 0) && !dryRun) {
                inventory.setItem(slot, stack);
            }
        }
    }

    private boolean convertStack(ItemStack stack, Tally tally, boolean dryRun, int depth) {
        if (stack == null || stack.getType().isAir() || depth > MAX_NESTED_DEPTH) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        int migrated = PdcKeyMigration.migrateAll(meta.getPersistentDataContainer(), dryRun);
        boolean nestedChanged = convertNested(meta, tally, dryRun, depth);
        record(tally, migrated);
        if (migrated <= 0 && !nestedChanged) {
            return false;
        }
        return dryRun || stack.setItemMeta(meta);
    }

    private boolean convertNested(ItemMeta meta, Tally tally, boolean dryRun, int depth) {
        if (!(meta instanceof BlockStateMeta blockStateMeta) || !blockStateMeta.hasBlockState()) {
            return false;
        }
        BlockState state = blockStateMeta.getBlockState();
        if (!(state instanceof Container container)) {
            return false;
        }
        Inventory nested = container.getInventory();
        boolean changed = false;
        for (int slot = 0; slot < nested.getSize(); slot++) {
            ItemStack stack = nested.getItem(slot);
            if (!convertStack(stack, tally, dryRun, depth + 1)) {
                continue;
            }
            changed = true;
            if (!dryRun) {
                nested.setItem(slot, stack);
            }
        }
        if (changed && !dryRun) {
            blockStateMeta.setBlockState(state);
        }
        return changed;
    }

    private void record(Tally tally, int migrated) {
        tally.scanned().incrementAndGet();
        if (migrated > 0) {
            tally.migrated().addAndGet(migrated);
        } else {
            tally.skipped().incrementAndGet();
        }
    }

    private Map<Scope, ScopeReport> freeze(Map<Scope, Tally> tallies) {
        Map<Scope, ScopeReport> reports = new EnumMap<>(Scope.class);
        tallies.forEach((scope, tally) -> reports.put(scope, new ScopeReport(
                tally.scanned().get(),
                tally.migrated().get(),
                tally.skipped().get(),
                tally.failed().get())));
        return Map.copyOf(reports);
    }

    private String describe(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private record Tally(AtomicInteger scanned, AtomicInteger migrated, AtomicInteger skipped, AtomicInteger failed) {

        Tally() {
            this(new AtomicInteger(), new AtomicInteger(), new AtomicInteger(), new AtomicInteger());
        }
    }
}
