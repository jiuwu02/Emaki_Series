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
        boolean wantPlayers = scopes.contains(Scope.PLAYERS);
        boolean wantChunks = scopes.contains(Scope.CONTAINERS) || scopes.contains(Scope.ENTITIES);
        return dispatcher.submitGlobal(plugin, () -> snapshotWork(wantPlayers, wantChunks))
                .thenCompose(work -> dispatchAll(work, tallies, dryRun))
                .thenApply(ignored -> freeze(tallies));
    }

    private WorkSnapshot snapshotWork(boolean wantPlayers, boolean wantChunks) {
        List<Player> players = new ArrayList<>();
        if (wantPlayers) {
            players.addAll(Bukkit.getOnlinePlayers());
        }
        List<ChunkRef> chunks = new ArrayList<>();
        if (wantChunks) {
            for (World world : Bukkit.getWorlds()) {
                for (Chunk chunk : world.getLoadedChunks()) {
                    chunks.add(new ChunkRef(world.getName(), chunk.getX(), chunk.getZ()));
                }
            }
        }
        return new WorkSnapshot(List.copyOf(players), List.copyOf(chunks));
    }

    private CompletableFuture<Void> dispatchAll(WorkSnapshot work, Map<Scope, Tally> tallies, boolean dryRun) {
        List<CompletableFuture<Void>> pending = new ArrayList<>();
        Tally playerTally = tallies.get(Scope.PLAYERS);
        if (playerTally != null) {
            for (int index = 0; index < work.players().size(); index++) {
                Player player = work.players().get(index);
                long delay = index / PLAYERS_PER_TICK;
                pending.add(dispatchPlayer(player, playerTally, dryRun, delay));
            }
        }
        Tally containerTally = tallies.get(Scope.CONTAINERS);
        Tally entityTally = tallies.get(Scope.ENTITIES);
        if (containerTally != null || entityTally != null) {
            for (int index = 0; index < work.chunks().size(); index++) {
                ChunkRef chunkRef = work.chunks().get(index);
                long delay = index / CHUNKS_PER_TICK;
                pending.add(dispatchChunk(chunkRef, containerTally, entityTally, dryRun, delay));
            }
        }
        return CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new));
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
                plugin.getLogger().warning("PDC convert failed for player " + player.getName()
                        + ": " + describe(throwable));
            } finally {
                done.complete(null);
            }
        };
        if (dispatcher.runEntityLater(plugin, player, convert, () -> done.complete(null), delayTicks) == null) {
            tally.failed().incrementAndGet();
            done.complete(null);
        }
        return done;
    }

    private CompletableFuture<Void> dispatchChunk(ChunkRef chunkRef,
            Tally containerTally,
            Tally entityTally,
            boolean dryRun,
            long delayTicks) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        World world = Bukkit.getWorld(chunkRef.worldName());
        if (world == null) {
            done.complete(null);
            return done;
        }
        Location anchor = new Location(world, (chunkRef.chunkX() << 4) + 8D, 0D, (chunkRef.chunkZ() << 4) + 8D);
        Runnable convert = () -> {
            try {
                if (world.isChunkLoaded(chunkRef.chunkX(), chunkRef.chunkZ())) {
                    Chunk chunk = world.getChunkAt(chunkRef.chunkX(), chunkRef.chunkZ());
                    if (containerTally != null) {
                        convertContainers(chunk, containerTally, dryRun);
                    }
                    if (entityTally != null) {
                        convertEntities(chunk, entityTally, dryRun);
                    }
                }
            } catch (Throwable throwable) {
                Tally target = containerTally != null ? containerTally : entityTally;
                target.failed().incrementAndGet();
                plugin.getLogger().warning("PDC convert failed for chunk " + chunkRef.worldName()
                        + " " + chunkRef.chunkX() + "," + chunkRef.chunkZ() + ": " + describe(throwable));
            } finally {
                done.complete(null);
            }
        };
        if (dispatcher.runAtLocationLater(plugin, anchor, convert, delayTicks) == null) {
            Tally target = containerTally != null ? containerTally : entityTally;
            target.failed().incrementAndGet();
            done.complete(null);
        }
        return done;
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

    private record WorkSnapshot(List<Player> players, List<ChunkRef> chunks) {
    }

    private record ChunkRef(String worldName, int chunkX, int chunkZ) {
    }

    private record Tally(AtomicInteger scanned, AtomicInteger migrated, AtomicInteger skipped, AtomicInteger failed) {

        Tally() {
            this(new AtomicInteger(), new AtomicInteger(), new AtomicInteger(), new AtomicInteger());
        }
    }
}
