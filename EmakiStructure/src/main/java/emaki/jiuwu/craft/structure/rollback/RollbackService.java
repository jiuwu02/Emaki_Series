package emaki.jiuwu.craft.structure.rollback;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.runtime.CapabilityProbe;
import emaki.jiuwu.craft.structure.EmakiStructurePlugin;
import emaki.jiuwu.craft.structure.config.AppConfig;
import emaki.jiuwu.craft.structure.generate.BlockEntityWriter;
import emaki.jiuwu.craft.structure.generate.ChunkBatch;
import emaki.jiuwu.craft.structure.generate.GenerationJob;
import emaki.jiuwu.craft.structure.integration.furniture.FurniturePlacer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class RollbackService implements Runnable {

    private static final long PUMP_PERIOD_TICKS = 1L;
    private static final int MAX_BATCH_RETRIES = 8;
    private static final long RESTORE_TIMEOUT_NANOS = Duration.ofMinutes(5).toNanos();
    private static final long SHUTDOWN_DRAIN_NANOS = Duration.ofSeconds(5).toNanos();
    private static final String SUFFIX_PARTIAL = ".rollback-partial";
    private static final String SUFFIX_LOG = ".log";

    private record QueuedBatch(ChunkBatch batch, int retries) {
    }

    private static final class RestoreTask {
        private final String txid;
        private final Path file;
        private final boolean startup;
        private final Deque<QueuedBatch> queue = new ArrayDeque<>();
        private final long createdAtNanos = System.nanoTime();
        private int inFlight;
        private boolean failed;

        private RestoreTask(String txid, Path file, boolean startup) {
            this.txid = txid;
            this.file = file;
            this.startup = startup;
        }
    }

    private final EmakiStructurePlugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final Supplier<AppConfig> config;
    private final BlockEntityWriter blockEntityWriter;
    private final FurniturePlacer furniturePlacer;
    private final Path journalDir;
    private final Object lock = new Object();
    private final Deque<RestoreTask> tasks = new ArrayDeque<>();
    private final List<String> partialFiles = new ArrayList<>();
    private final AtomicInteger startupPending = new AtomicInteger();
    private final AtomicInteger startupTotal = new AtomicInteger();
    private volatile boolean recovering;
    private TaskToken pumpToken;

    public RollbackService(EmakiStructurePlugin plugin,
            ExecutionDispatcher dispatcher,
            Supplier<AppConfig> config,
            BlockEntityWriter blockEntityWriter,
            FurniturePlacer furniturePlacer,
            Path journalDir) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.config = config;
        this.blockEntityWriter = blockEntityWriter;
        this.furniturePlacer = furniturePlacer;
        this.journalDir = journalDir;
    }

    public void start() {
        if (pumpToken != null) {
            return;
        }
        pumpToken = dispatcher.runGlobalTimer(plugin, this, PUMP_PERIOD_TICKS, PUMP_PERIOD_TICKS);
    }

    public void stop() {
        if (pumpToken != null) {
            pumpToken.cancel();
            pumpToken = null;
        }
    }

    public GenerationJournal open(String txid) throws IOException {
        if (!config.get().rollback().enabled()) {
            return null;
        }
        return GenerationJournal.open(journalDir, txid,
                config.get().rollback().journalMaxBytes());
    }

    public boolean recovering() {
        return recovering;
    }

    public List<String> partialFiles() {
        synchronized (lock) {
            return List.copyOf(partialFiles);
        }
    }

    public int pendingRestoreCount() {
        synchronized (lock) {
            return tasks.size();
        }
    }

    public void settle(GenerationJob job) {
        GenerationJournal journal = job.journal();
        if (journal == null) {
            return;
        }
        if (job.outcome() == GenerationJob.Outcome.COMPLETED) {
            journal.commit();
            return;
        }
        List<GenerationJournal.Entry> entries = journal.entries();
        journal.closeQuietly();
        enqueue(job.id().toString(), journal.file(), entries, false);
    }

    public void recoverStartup() {
        if (!Files.isDirectory(journalDir)) {
            return;
        }
        boolean rollbackEnabled = config.get().rollback().enabled();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(journalDir)) {
            for (Path path : stream) {
                String name = path.getFileName().toString();
                if (name.endsWith(SUFFIX_PARTIAL)) {
                    registerPartial(name);
                    continue;
                }
                if (!name.endsWith(SUFFIX_LOG) || !Files.isRegularFile(path)) {
                    continue;
                }
                processStartupFile(path, name, rollbackEnabled);
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("journal 扫描失败: " + exception.getMessage());
            return;
        }
        int pending = startupPending.get();
        recovering = pending > 0;
        if (pending > 0) {
            plugin.messageService().info("console.rollback_recovering", Map.of("count", pending));
        }
        for (String partial : partialFiles()) {
            plugin.messageService().severe("console.rollback_partial", Map.of("file", partial));
        }
    }

    private void processStartupFile(Path path, String name, boolean rollbackEnabled) {
        JournalReader.Parsed parsed;
        try {
            parsed = JournalReader.read(path);
        } catch (JournalReader.CorruptedException exception) {
            if (rollbackEnabled) {
                markPartial(path, name);
            }
            return;
        } catch (IOException exception) {
            plugin.getLogger().warning("journal 读取失败 " + name + ": " + exception.getMessage());
            return;
        }
        if (parsed.committed() || parsed.entries().isEmpty()) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
            }
            return;
        }
        if (!rollbackEnabled) {
            return;
        }
        startupPending.incrementAndGet();
        startupTotal.incrementAndGet();
        enqueue(name.substring(0, name.length() - SUFFIX_LOG.length()), path,
                parsed.entries(), true);
    }

    private void enqueue(String txid, Path file, List<GenerationJournal.Entry> entries,
            boolean startup) {
        if (entries.isEmpty()) {
            deleteQuietly(file);
            if (startup) {
                finishStartupTask();
            }
            return;
        }
        RestoreTask task = new RestoreTask(txid, file, startup);
        List<String> missingWorlds = buildBatches(task, entries);
        if (!missingWorlds.isEmpty()) {
            plugin.messageService().severe("console.rollback_partial", Map.of(
                    "file", file.getFileName().toString()));
            markPartial(file, file.getFileName().toString());
            if (startup) {
                finishStartupTask();
            }
            return;
        }
        synchronized (lock) {
            tasks.addLast(task);
        }
    }

    private List<String> buildBatches(RestoreTask task, List<GenerationJournal.Entry> entries) {
        Map<String, World> worlds = new HashMap<>();
        List<String> missing = new ArrayList<>();
        for (GenerationJournal.Entry entry : entries) {
            String worldName = entry instanceof GenerationJournal.BlockSnapshot block
                    ? block.world() : ((GenerationJournal.EntitySnapshot) entry).world();
            if (worlds.containsKey(worldName)) {
                continue;
            }
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                missing.add(worldName);
            } else {
                worlds.put(worldName, world);
            }
        }
        if (!missing.isEmpty()) {
            return missing;
        }
        Map<ChunkRef, List<GenerationJournal.EntitySnapshot>> entitiesByChunk = new LinkedHashMap<>();
        Map<ChunkRef, List<GenerationJournal.BlockSnapshot>> blocksByChunk = new LinkedHashMap<>();
        for (GenerationJournal.Entry entry : entries) {
            if (entry instanceof GenerationJournal.EntitySnapshot entity) {
                ChunkRef ref = new ChunkRef(entity.world(), entity.x() >> 4, entity.z() >> 4);
                entitiesByChunk.computeIfAbsent(ref, ignored -> new ArrayList<>()).add(entity);
            } else {
                GenerationJournal.BlockSnapshot block = (GenerationJournal.BlockSnapshot) entry;
                ChunkRef ref = new ChunkRef(block.world(), block.x() >> 4, block.z() >> 4);
                blocksByChunk.computeIfAbsent(ref, ignored -> new ArrayList<>()).add(block);
            }
        }
        for (Map.Entry<ChunkRef, List<GenerationJournal.EntitySnapshot>> group : entitiesByChunk.entrySet()) {
            World world = worlds.get(group.getKey().world());
            GenerationJournal.EntitySnapshot head = group.getValue().get(0);
            Location location = new Location(world, head.x(), head.y(), head.z());
            task.queue.addLast(new QueuedBatch(
                    new EntityRestoreBatch(world, location, group.getValue(), furniturePlacer), 0));
        }
        for (Map.Entry<ChunkRef, List<GenerationJournal.BlockSnapshot>> group : blocksByChunk.entrySet()) {
            World world = worlds.get(group.getKey().world());
            GenerationJournal.BlockSnapshot head = group.getValue().get(0);
            Location location = new Location(world, head.x(), head.y(), head.z());
            task.queue.addLast(new QueuedBatch(
                    new BlockRestoreBatch(world, location, group.getValue(), blockEntityWriter), 0));
        }
        return List.of();
    }

    private record ChunkRef(String world, int chunkX, int chunkZ) {
    }

    @Override
    public void run() {
        int budget = Math.max(1, config.get().generate().maxBlocksPerTick());
        List<RestoreTask> snapshot;
        synchronized (lock) {
            snapshot = new ArrayList<>(tasks);
        }
        for (RestoreTask task : snapshot) {
            if (task.createdAtNanos + RESTORE_TIMEOUT_NANOS < System.nanoTime()) {
                failTask(task);
            }
            while (budget > 0) {
                QueuedBatch entry;
                synchronized (lock) {
                    entry = task.queue.pollFirst();
                    if (entry == null) {
                        break;
                    }
                    task.inFlight++;
                }
                if (!dispatch(task, entry)) {
                    onBatchSettled(task, entry, false);
                } else {
                    budget -= Math.max(1, entry.batch().blockCount());
                }
            }
        }
    }

    private boolean dispatch(RestoreTask task, QueuedBatch entry) {
        try {
            TaskToken token = dispatcher.runAtLocation(plugin, entry.batch().location(),
                    () -> executeBatch(task, entry));
            return token != null && token != TaskToken.UNAVAILABLE;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void executeBatch(RestoreTask task, QueuedBatch entry) {
        boolean ok = true;
        try {
            entry.batch().writeToRegion();
        } catch (RuntimeException exception) {
            ok = false;
        } finally {
            entry.batch().markFinished();
        }
        onBatchSettled(task, entry, ok);
    }

    private void onBatchSettled(RestoreTask task, QueuedBatch entry, boolean ok) {
        boolean settleNow;
        boolean failedFlag;
        synchronized (lock) {
            task.inFlight--;
            if (!ok) {
                if (entry.retries() + 1 > MAX_BATCH_RETRIES) {
                    task.failed = true;
                } else if (!task.failed) {
                    task.queue.addLast(new QueuedBatch(entry.batch(), entry.retries() + 1));
                }
            }
            if (entry.batch() instanceof BlockRestoreBatch blockBatch && blockBatch.failures() > 0) {
                task.failed = true;
            }
            if (entry.batch() instanceof EntityRestoreBatch entityBatch && entityBatch.failures() > 0) {
                task.failed = true;
            }
            failedFlag = task.failed;
            settleNow = task.inFlight == 0 && task.queue.isEmpty();
            if (settleNow) {
                tasks.remove(task);
            }
        }
        if (settleNow) {
            finishTask(task, failedFlag);
        }
    }

    private void failTask(RestoreTask task) {
        boolean settleNow;
        synchronized (lock) {
            task.failed = true;
            task.queue.clear();
            settleNow = task.inFlight == 0;
            if (settleNow) {
                tasks.remove(task);
            }
        }
        if (settleNow) {
            finishTask(task, true);
        }
    }

    private void finishTask(RestoreTask task, boolean failed) {
        if (failed) {
            markPartial(task.file, task.file.getFileName().toString());
        } else {
            deleteQuietly(task.file);
            if (task.startup) {
                plugin.getLogger().info("已回滚未完成事务 " + task.txid);
            }
        }
        if (task.startup) {
            finishStartupTask();
        }
    }

    private void finishStartupTask() {
        if (startupPending.decrementAndGet() == 0 && recovering) {
            recovering = false;
            if (config.get().rollback().enabled()) {
                plugin.messageService().info("console.rollback_recovered",
                        Map.of("count", startupTotal.get()));
            }
        }
    }

    private void markPartial(Path file, String name) {
        Path target = file.resolveSibling(name + SUFFIX_PARTIAL);
        try {
            if (!Files.exists(target)) {
                Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("rollback-partial 标记失败 " + name + ": "
                    + exception.getMessage());
        }
        registerPartial(name + SUFFIX_PARTIAL);
        plugin.debugLogger().log("structure", (UUID) null, "rollback_partial",
                Map.of("file", name + SUFFIX_PARTIAL));
    }

    private void registerPartial(String name) {
        synchronized (lock) {
            if (!partialFiles.contains(name)) {
                partialFiles.add(name);
            }
        }
    }

    private void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
    }

    public void drainBestEffort() {
        if (CapabilityProbe.detect(Bukkit.getServer()).folia()) {
            return;
        }
        if (!Bukkit.isPrimaryThread()) {
            return;
        }
        long deadline = System.nanoTime() + SHUTDOWN_DRAIN_NANOS;
        while (System.nanoTime() < deadline) {
            RestoreTask owner = null;
            QueuedBatch entry = null;
            synchronized (lock) {
                for (RestoreTask task : tasks) {
                    if (!task.queue.isEmpty()) {
                        owner = task;
                        entry = task.queue.pollFirst();
                        task.inFlight++;
                        break;
                    }
                }
            }
            if (entry == null) {
                return;
            }
            executeBatch(owner, entry);
        }
    }
}
