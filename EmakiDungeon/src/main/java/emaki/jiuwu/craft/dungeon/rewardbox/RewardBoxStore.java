package emaki.jiuwu.craft.dungeon.rewardbox;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.data.DomainSaveResult;
import emaki.jiuwu.craft.corelib.api.data.DomainSnapshot;
import emaki.jiuwu.craft.corelib.api.data.PlayerDataLease;
import emaki.jiuwu.craft.corelib.api.data.PlayerDomainRepository;
import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.dungeon.log.DungeonLogTag;

public final class RewardBoxStore {

    public static final class Box {

        private final List<ItemStack> items;
        private boolean autoFlow;

        public Box(List<ItemStack> items, boolean autoFlow) {
            this.items = items == null ? new ArrayList<>() : items;
            this.autoFlow = autoFlow;
        }

        public List<ItemStack> items() {
            return items;
        }

        public boolean autoFlow() {
            return autoFlow;
        }

        public void setAutoFlow(boolean autoFlow) {
            this.autoFlow = autoFlow;
        }
    }

    private static final String CURRENT_VERSION = "1.0.0";
    private static final long DRAIN_TIMEOUT_MILLIS = 5000L;
    private static final String DATABASE_DOMAIN = "dungeon_rewardbox";
    private static final int DATABASE_SCHEMA_VERSION = 1;

    private final Plugin plugin;
    private final MessageService messages;
    private final ExecutionDispatcher dispatcher;
    private final File root;
    private final Map<UUID, Object> lanes = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Boolean>> pendingWrites = new ConcurrentHashMap<>();
    private final Map<UUID, Long> databaseRevisions = new ConcurrentHashMap<>();
    private final Set<UUID> databaseLeased = ConcurrentHashMap.newKeySet();
    private volatile PlayerDomainRepository databaseRepository;
    private volatile PlayerDataLease databaseLease;

    public RewardBoxStore(Plugin plugin, MessageService messages, ExecutionDispatcher dispatcher, File dataFolder) {
        this.plugin = plugin;
        this.messages = messages;
        this.dispatcher = dispatcher;
        this.root = new File(dataFolder, "rewardbox");
    }

    public void attachDatabase(PlayerDomainRepository databaseRepository, PlayerDataLease databaseLease) {
        this.databaseRepository = databaseRepository;
        this.databaseLease = databaseLease;
    }

    public void unload(UUID playerId) {
        if (playerId == null) {
            return;
        }
        databaseLeased.remove(playerId);
        PlayerDataLease lease = databaseLease;
        if (lease != null) {
            lease.release(playerId);
        }
    }

    public Box load(UUID playerId) {
        Box box = read(playerId);
        return box == null ? new Box(new ArrayList<>(), false) : box;
    }

    public boolean update(UUID playerId, Consumer<Box> mutator) {
        if (playerId == null || mutator == null) {
            return false;
        }
        synchronized (laneOf(playerId)) {
            Box box = read(playerId);
            if (box == null) {
                return false;
            }
            Map<String, Object> before = encode(box);
            mutator.accept(box);
            Map<String, Object> after = encode(box);
            if (before.equals(after)) {
                return true;
            }
            return persist(playerId, after);
        }
    }

    public CompletableFuture<Boolean> updateAsync(UUID playerId, Consumer<Box> mutator) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        if (playerId == null || mutator == null) {
            future.complete(Boolean.FALSE);
            return future;
        }
        TaskToken token = dispatcher.runAsync(plugin, () -> {
            boolean saved;
            try {
                saved = update(playerId, mutator);
            } catch (RuntimeException failure) {
                messages.warning(DungeonLogTag.REWARDBOX, "console.rewardbox_update_failed", Map.of(
                        "player", String.valueOf(playerId),
                        "cause", describe(failure)));
                saved = false;
            }
            future.complete(saved);
        });
        if (token == null || token.cancelled()) {
            messages.warning(DungeonLogTag.REWARDBOX, "console.rewardbox_update_rejected", Map.of(
                    "player", String.valueOf(playerId)));
            future.complete(Boolean.FALSE);
            return future;
        }
        pendingWrites.put(playerId, future);
        return future;
    }

    public CompletableFuture<Boolean> drainWrites() {
        List<CompletableFuture<Boolean>> pending = List.copyOf(pendingWrites.values());
        if (pending.isEmpty()) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }
        CompletableFuture<?>[] handles = pending.toArray(new CompletableFuture<?>[0]);
        return CompletableFuture.allOf(handles)
                .orTimeout(DRAIN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                .handle((ignored, throwable) -> {
                    int unsettled = 0;
                    for (CompletableFuture<Boolean> future : pending) {
                        if (!Boolean.TRUE.equals(future.getNow(Boolean.FALSE))) {
                            unsettled++;
                        }
                    }
                    if (unsettled == 0) {
                        pendingWrites.values().removeAll(pending);
                        return Boolean.TRUE;
                    }
                    messages.warning(DungeonLogTag.REWARDBOX, "console.rewardbox_drain_unsettled", Map.of(
                            "count", String.valueOf(unsettled),
                            "reason", describe(throwable)));
                    return Boolean.FALSE;
                });
    }

    private Box read(UUID playerId) {
        YamlSection section;
        PlayerDomainRepository repository = databaseRepository;
        if (repository != null) {
            section = loadDatabaseSection(playerId, repository);
            if (section == null) {
                return null;
            }
        } else {
            File file = fileOf(playerId);
            if (!file.isFile()) {
                return new Box(new ArrayList<>(), false);
            }
            section = YamlFiles.load(file);
        }
        if (section == null || section.isEmpty()) {
            return new Box(new ArrayList<>(), false);
        }
        List<ItemStack> items = new ArrayList<>();
        List<?> rawItems = section.getList("items");
        if (rawItems != null) {
            for (Object raw : rawItems) {
                ItemStack stack = decode(String.valueOf(raw));
                if (stack != null && stack.getAmount() > 0) {
                    items.add(stack);
                }
            }
        }
        return new Box(items, section.getBoolean("auto-flow", false));
    }

    private Map<String, Object> encode(Box box) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("version", CURRENT_VERSION);
        values.put("auto-flow", box.autoFlow());
        List<String> encoded = new ArrayList<>(box.items().size());
        for (ItemStack stack : box.items()) {
            encoded.add(Base64.getEncoder().encodeToString(stack.serializeAsBytes()));
        }
        values.put("items", encoded);
        return values;
    }

    private boolean persist(UUID playerId, Map<String, Object> values) {
        PlayerDomainRepository repository = databaseRepository;
        if (repository != null) {
            return persistDatabase(playerId, values, repository);
        }
        File file = fileOf(playerId);
        try {
            root.mkdirs();
            YamlFiles.save(file, values);
            return true;
        } catch (IOException exception) {
            messages.warning(DungeonLogTag.REWARDBOX, "console.rewardbox_save_failed", Map.of(
                    "player", String.valueOf(playerId),
                    "file", String.valueOf(file.getAbsolutePath()),
                    "error", String.valueOf(exception.getMessage())));
            return false;
        }
    }

    private YamlSection loadDatabaseSection(UUID playerId, PlayerDomainRepository repository) {
        CompletableFuture<DomainSnapshot> future = acquireDatabaseLease(playerId)
                .thenCompose(ignored -> repository.load(playerId, DATABASE_DOMAIN));
        Throwable failure = failureOf(future);
        if (failure != null) {
            messages.warning(DungeonLogTag.REWARDBOX, "console.player_data_load_failed", Map.of(
                    "player", String.valueOf(playerId),
                    "error", describe(failure)));
            return null;
        }
        DomainSnapshot snapshot = future.getNow(null);
        if (snapshot != null) {
            databaseRevisions.put(playerId, snapshot.revision());
        }
        return decodeDatabasePayload(snapshot);
    }

    private boolean persistDatabase(UUID playerId, Map<String, Object> values, PlayerDomainRepository repository) {
        byte[] payload = YamlFiles.dump(values).getBytes(StandardCharsets.UTF_8);
        long expectedRevision = databaseRevisions.getOrDefault(playerId, 0L);
        CompletableFuture<DomainSaveResult> future = repository
                .save(playerId, DATABASE_DOMAIN, playerId.toString(), payload, DATABASE_SCHEMA_VERSION, expectedRevision);
        Throwable failure = failureOf(future);
        DomainSaveResult result = failure == null ? future.getNow(null) : null;
        if (failure == null && result != null && result.outcome() == DomainSaveResult.Outcome.COMMITTED) {
            databaseRevisions.put(playerId, result.revision());
            return true;
        }
        messages.warning(DungeonLogTag.REWARDBOX, "console.rewardbox_save_failed", Map.of(
                "player", String.valueOf(playerId),
                "file", "database:" + DATABASE_DOMAIN,
                "error", failure != null ? describe(failure)
                        : "数据库保存未提交: " + (result == null ? "unknown" : result.outcome())));
        return false;
    }

    private YamlSection decodeDatabasePayload(DomainSnapshot snapshot) {
        if (snapshot == null || snapshot.payload().length == 0) {
            return new MapYamlSection();
        }
        return YamlFiles.load(new String(snapshot.payload(), StandardCharsets.UTF_8));
    }

    private CompletableFuture<Void> acquireDatabaseLease(UUID playerId) {
        PlayerDataLease lease = databaseLease;
        if (lease == null || databaseLeased.contains(playerId) || Bukkit.getPlayer(playerId) == null) {
            return CompletableFuture.completedFuture(null);
        }
        return attemptLease(lease, playerId, 5).thenRun(() -> databaseLeased.add(playerId));
    }

    private CompletableFuture<Void> attemptLease(PlayerDataLease lease, UUID playerId, int attemptsLeft) {
        return lease.acquire(playerId).thenCompose(acquired -> {
            if (Boolean.TRUE.equals(acquired)) {
                return CompletableFuture.completedFuture(null);
            }
            if (attemptsLeft <= 1) {
                return CompletableFuture.failedFuture(new IllegalStateException("数据租约获取失败: " + playerId));
            }
            return CompletableFuture.runAsync(() -> { },
                            CompletableFuture.delayedExecutor(2L, TimeUnit.SECONDS))
                    .thenCompose(ignored -> attemptLease(lease, playerId, attemptsLeft - 1));
        });
    }

    private Throwable failureOf(CompletableFuture<?> future) {
        if (!awaitUntil(future, System.nanoTime() + TimeUnit.SECONDS.toNanos(5L))) {
            return new IllegalStateException("数据库操作超时");
        }
        if (!future.isCompletedExceptionally()) {
            return null;
        }
        try {
            future.getNow(null);
            return null;
        } catch (CompletionException | CancellationException exception) {
            return exception.getCause() == null ? exception : exception.getCause();
        }
    }

    private boolean awaitUntil(CompletableFuture<?> future, long deadline) {
        if (future.isDone()) {
            return true;
        }
        long remainingNanos = deadline - System.nanoTime();
        if (remainingNanos <= 0L) {
            return false;
        }
        CountDownLatch latch = new CountDownLatch(1);
        future.whenComplete((ignored, throwable) -> latch.countDown());
        try {
            return future.isDone() || latch.await(remainingNanos, TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private Object laneOf(UUID playerId) {
        return lanes.computeIfAbsent(playerId, id -> new Object());
    }


    private String describe(Throwable throwable) {
        if (throwable == null) {
            return "none";
        }
        return throwable.getClass().getSimpleName()
                + (throwable.getMessage() == null ? "" : ": " + throwable.getMessage());
    }

    private File fileOf(UUID playerId) {
        return new File(root, playerId + ".yml");
    }

    private static ItemStack decode(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(raw));
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
