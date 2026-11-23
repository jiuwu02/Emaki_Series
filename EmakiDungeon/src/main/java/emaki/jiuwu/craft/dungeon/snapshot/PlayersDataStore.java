package emaki.jiuwu.craft.dungeon.snapshot;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.data.DomainSaveResult;
import emaki.jiuwu.craft.corelib.api.data.DomainSnapshot;
import emaki.jiuwu.craft.corelib.api.data.PlayerDataLease;
import emaki.jiuwu.craft.corelib.api.data.PlayerDomainRepository;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import java.util.function.Consumer;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.dungeon.log.DungeonLogTag;

public final class PlayersDataStore {

    public static final class PlayerData {

        private String restoreInstance = "";
        private String partyId = "";
        private boolean globalChat;
        private Map<String, Object> limits = Map.of();

        public String restoreInstance() {
            return restoreInstance;
        }

        public void setRestoreInstance(String restoreInstance) {
            this.restoreInstance = restoreInstance == null ? "" : restoreInstance;
        }

        public String partyId() {
            return partyId;
        }

        public void setPartyId(String partyId) {
            this.partyId = partyId == null ? "" : partyId;
        }

        public boolean globalChat() {
            return globalChat;
        }

        public void setGlobalChat(boolean globalChat) {
            this.globalChat = globalChat;
        }

        public Map<String, Object> limits() {
            return limits;
        }

        public void setLimits(Map<String, Object> limits) {
            this.limits = limits == null ? Map.of() : limits;
        }
    }

    private static final String DATABASE_DOMAIN = "dungeon_player";
    private static final int DATABASE_SCHEMA_VERSION = 1;

    private final Plugin plugin;
    private final MessageService messages;
    private final ExecutionDispatcher dispatcher;
    private final File root;
    private final boolean globalChatDefault;
    private final Object ioLock = new Object();
    private final Map<UUID, Long> databaseRevisions = new ConcurrentHashMap<>();
    private final Set<UUID> databaseLeased = ConcurrentHashMap.newKeySet();
    private volatile PlayerDomainRepository databaseRepository;
    private volatile PlayerDataLease databaseLease;

    public PlayersDataStore(Plugin plugin, MessageService messages, ExecutionDispatcher dispatcher, File dataFolder, boolean globalChatDefault) {
        this.plugin = plugin;
        this.messages = messages;
        this.dispatcher = dispatcher;
        this.root = new File(dataFolder, "players");
        this.globalChatDefault = globalChatDefault;
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

    public File root() {
        return root;
    }

    public PlayerData read(UUID playerId) {
        PlayerData data = readLoaded(playerId);
        return data == null ? newDefaultData() : data;
    }

    private PlayerData newDefaultData() {
        PlayerData data = new PlayerData();
        data.globalChat = globalChatDefault;
        return data;
    }

    private PlayerData readLoaded(UUID playerId) {
        PlayerData data = new PlayerData();
        data.globalChat = globalChatDefault;
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
                return data;
            }
            section = YamlFiles.load(file);
        }
        if (section == null || section.isEmpty()) {
            return data;
        }
        data.restoreInstance = section.getString("restore-instance", "");
        data.partyId = section.getString("party", "");
        data.globalChat = section.getBoolean("global-chat", globalChatDefault);
        YamlSection limits = section.getSection("limits");
        if (limits != null && !limits.isEmpty()) {
            data.limits = new LinkedHashMap<>(limits.asMap());
        }
        return data;
    }

    public void writeAsync(UUID playerId, PlayerData data) {
        dispatcher.runAsync(plugin, () -> {
            synchronized (ioLock) {
                writeSync(playerId, data);
            }
        });
    }

    public void updateAsync(UUID playerId, Consumer<PlayerData> mutator) {
        if (playerId == null || mutator == null) {
            return;
        }
        dispatcher.runAsync(plugin, () -> {
            synchronized (ioLock) {
                PlayerData data = readLoaded(playerId);
                if (data == null) {
                    return;
                }
                mutator.accept(data);
                writeSync(playerId, data);
            }
        });
    }

    public boolean writeSync(UUID playerId, PlayerData data) {
        if (playerId == null || data == null) {
            return false;
        }
        Map<String, Object> values = serialize(data);
        PlayerDomainRepository repository = databaseRepository;
        if (repository != null) {
            return writeDatabase(playerId, values, repository);
        }
        File file = fileOf(playerId);
        try {
            YamlFiles.save(file, values);
            return true;
        } catch (IOException exception) {
            messages.warning(DungeonLogTag.STORAGE, "console.player_data_save_failed", Map.of(
                    "file", String.valueOf(file.getAbsolutePath()),
                    "error", String.valueOf(exception.getMessage())));
            return false;
        }
    }

    private Map<String, Object> serialize(PlayerData data) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("restore-instance", data.restoreInstance);
        values.put("party", data.partyId);
        values.put("global-chat", data.globalChat);
        if (!data.limits.isEmpty()) {
            values.put("limits", new LinkedHashMap<>(data.limits));
        }
        return values;
    }

    private YamlSection loadDatabaseSection(UUID playerId, PlayerDomainRepository repository) {
        CompletableFuture<DomainSnapshot> future = acquireDatabaseLease(playerId)
                .thenCompose(ignored -> repository.load(playerId, DATABASE_DOMAIN));
        Throwable failure = failureOf(future);
        if (failure != null) {
            messages.warning(DungeonLogTag.STORAGE, "console.player_data_load_failed", Map.of(
                    "player", String.valueOf(playerId),
                    "error", String.valueOf(failure)));
            return null;
        }
        DomainSnapshot snapshot = future.getNow(null);
        if (snapshot != null) {
            databaseRevisions.put(playerId, snapshot.revision());
        }
        return decodeDatabasePayload(snapshot);
    }

    private boolean writeDatabase(UUID playerId, Map<String, Object> values, PlayerDomainRepository repository) {
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
        messages.warning(DungeonLogTag.STORAGE, "console.player_data_save_failed", Map.of(
                "file", "database:" + DATABASE_DOMAIN,
                "error", failure != null ? String.valueOf(failure)
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

    private File fileOf(UUID playerId) {
        return new File(root, playerId + ".yml");
    }
}
