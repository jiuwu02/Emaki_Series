package emaki.jiuwu.craft.strengthen.enhancement.pity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.data.DomainSaveResult;
import emaki.jiuwu.craft.corelib.api.data.DomainSnapshot;
import emaki.jiuwu.craft.corelib.api.data.PlayerDomainRepository;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.debug.FailureRecorder;
import java.nio.charset.StandardCharsets;

public final class InMemoryPityStateStore implements PityStateStore {

    private static final String DATABASE_DOMAIN = "strengthen_pity";
    private static final int DATABASE_SCHEMA_VERSION = 1;
    private static final String PLAYER_SCOPE = "player";
    private static final String KEY_SCHEMA_VERSION = "schema_version";
    private static final String KEY_ENTRIES = "entries";
    private static final String KEY_COUNTER = "counter";
    private static final String KEY_LAST_TRIGGER_TIME = "last_trigger_time";
    private static final String KEY_TRIGGERED = "triggered";
    private static final long DATABASE_WAIT_NANOS = TimeUnit.SECONDS.toNanos(30L);

    private final Map<String, PityState> states = new ConcurrentHashMap<>();
    private final Map<UUID, Long> databaseRevisions = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();
    private final Path persistenceFile;
    private volatile boolean dirty;
    private volatile PlayerDomainRepository databaseRepository;

    public InMemoryPityStateStore() {
        this(null);
    }

    public InMemoryPityStateStore(@Nullable Path persistenceFile) {
        this.persistenceFile = persistenceFile;
    }

    public void attachDatabase(PlayerDomainRepository databaseRepository) {
        this.databaseRepository = databaseRepository;
    }

    @Override
    public @Nullable PityState load(@NotNull String scope, @NotNull String group, @NotNull String key) {
        PityState stored = states.get(composite(scope, group, key));
        return stored == null ? null : stored.copy();
    }

    @Override
    public void save(@NotNull String scope, @NotNull String group, @NotNull String key, @NotNull PityState state) {
        String composite = composite(scope, group, key);
        if (composite.isEmpty() || state == null) {
            return;
        }
        states.put(composite, state.copy());
        markDirty(composite);
    }

    @Override
    public void remove(@NotNull String scope, @NotNull String group, @NotNull String key) {
        String composite = composite(scope, group, key);
        states.remove(composite);
        markDirty(composite);
    }

    @Override
    public boolean exists(@NotNull String scope, @NotNull String group, @NotNull String key) {
        return states.containsKey(composite(scope, group, key));
    }

    public void clear() {
        for (String composite : Set.copyOf(states.keySet())) {
            markDirty(composite);
        }
        states.clear();
        dirty = true;
    }

    public synchronized void loadFromDisk() {
        loadFileStates();
        PlayerDomainRepository repository = databaseRepository;
        if (repository != null) {
            loadDatabaseStates(repository);
        }
    }

    private void loadFileStates() {
        if (persistenceFile == null || !Files.isRegularFile(persistenceFile)) {
            dirty = false;
            return;
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(persistenceFile, StandardOpenOption.READ)) {
            properties.load(input);
        } catch (IOException exception) {
            dirty = false;
            return;
        }
        for (String encoded : properties.stringPropertyNames()) {
            String composite = decodeKey(encoded);
            if (Texts.isBlank(composite)) {
                continue;
            }
            String[] parts = composite.split("\\|", 3);
            if (parts.length != 3) {
                continue;
            }
            if (databaseRepository != null && databaseUuid(composite) != null) {
                continue;
            }
            String[] values = properties.getProperty(encoded, "").split(",", 3);
            if (values.length != 3) {
                continue;
            }
            try {
                int counter = Math.max(0, Integer.parseInt(values[0]));
                long lastTrigger = Math.max(0L, Long.parseLong(values[1]));
                boolean triggered = Boolean.parseBoolean(values[2]);
                states.put(composite, new PityState(counter, lastTrigger, triggered));
            } catch (NumberFormatException ignored) {
                FailureRecorder.recordIgnoredFailure("EmakiStrengthen", "InMemoryPityStateStore.java#1", ignored);
            }
        }
        dirty = false;
    }

    private void loadDatabaseStates(PlayerDomainRepository repository) {
        long deadline = System.nanoTime() + DATABASE_WAIT_NANOS;
        try {
            List<UUID> players = await(repository.listPlayers(DATABASE_DOMAIN), deadline);
            List<CompletableFuture<DomainSnapshot>> loads = new ArrayList<>(players.size());
            for (UUID playerId : players) {
                loads.add(repository.load(playerId, DATABASE_DOMAIN));
            }
            for (int index = 0; index < loads.size(); index++) {
                installDatabaseRows(players.get(index), await(loads.get(index), deadline));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("强化保底数据库加载被中断", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("强化保底数据库加载失败", exception);
        }
    }

    private void installDatabaseRows(UUID playerId, DomainSnapshot snapshot) {
        if (snapshot == null || snapshot.payload().length == 0) {
            return;
        }
        databaseRevisions.put(playerId, snapshot.revision());
        YamlSection root = YamlFiles.load(new String(snapshot.payload(), StandardCharsets.UTF_8));
        YamlSection entries = root == null ? null : root.getSection(KEY_ENTRIES);
        if (entries == null) {
            return;
        }
        for (String tail : entries.getKeys(false)) {
            YamlSection entry = entries.getSection(tail);
            if (Texts.isBlank(tail) || entry == null) {
                continue;
            }
            states.put(PLAYER_SCOPE + "|" + tail, new PityState(
                    Math.max(0, entry.getInt(KEY_COUNTER, 0)),
                    Math.max(0L, toLong(entry.get(KEY_LAST_TRIGGER_TIME), 0L)),
                    Boolean.TRUE.equals(entry.getBoolean(KEY_TRIGGERED, Boolean.FALSE))));
        }
    }

    public synchronized void saveToDisk() {
        if (!dirty) {
            return;
        }
        if (persistenceFile == null && databaseRepository == null) {
            dirty = false;
            return;
        }
        if (persist()) {
            dirty = false;
        }
    }

    public int size() {
        return states.size();
    }

    public boolean isDirty() {
        return dirty;
    }

    public boolean persistent() {
        return persistenceFile != null || databaseRepository != null;
    }

    public synchronized boolean flushToDisk() {
        if (!dirty) {
            return true;
        }
        if (persistenceFile == null && databaseRepository == null) {
            dirty = false;
            return true;
        }
        if (persist()) {
            dirty = false;
            return true;
        }
        return false;
    }

    public @NotNull Map<String, PityState> snapshot() {
        Map<String, PityState> copy = new LinkedHashMap<>();
        states.forEach((composite, state) -> copy.put(composite, state.copy()));
        return Map.copyOf(copy);
    }

    public int removeGroup(@NotNull String group) {
        String normalized = Texts.lower(group);
        if (Texts.isBlank(normalized)) {
            return 0;
        }
        int removed = 0;
        for (String composite : Set.copyOf(states.keySet())) {
            String[] parts = composite.split("\\|", 3);
            if (parts.length != 3) {
                continue;
            }
            if (parts[1].equals(normalized) || parts[1].startsWith(normalized + "#")) {
                states.remove(composite);
                markDirty(composite);
                removed++;
            }
        }
        if (removed > 0) {
            dirty = true;
        }
        return removed;
    }

    private synchronized boolean persist() {
        if (persistenceFile == null && databaseRepository == null) {
            return false;
        }
        boolean fileOk = true;
        if (persistenceFile != null) {
            fileOk = persistFile();
        }
        PlayerDomainRepository repository = databaseRepository;
        boolean databaseOk = repository == null || persistDatabase(repository);
        return fileOk && databaseOk;
    }

    private boolean persistFile() {
        try {
            Path parent = persistenceFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Properties properties = new Properties();
            states.forEach((key, state) -> {
                if (!fileManaged(key)) {
                    return;
                }
                properties.setProperty(
                        encodeKey(key), state.getCounter() + "," + state.getLastTriggerTime() + "," + state.isTriggered());
            });
            try (OutputStream output = Files.newOutputStream(persistenceFile,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                properties.store(output, "EmakiStrengthen 保底状态");
            }
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private boolean persistDatabase(PlayerDomainRepository repository) {
        if (dirtyPlayers.isEmpty()) {
            return true;
        }
        long deadline = System.nanoTime() + DATABASE_WAIT_NANOS;
        boolean allCommitted = true;
        for (UUID playerId : Set.copyOf(dirtyPlayers)) {
            byte[] payload = YamlFiles.dump(databasePayload(playerId)).getBytes(StandardCharsets.UTF_8);
            long expectedRevision = databaseRevisions.getOrDefault(playerId, 0L);
            try {
                DomainSaveResult result = repository
                        .save(playerId, DATABASE_DOMAIN, playerId.toString(), payload, DATABASE_SCHEMA_VERSION,
                                expectedRevision)
                        .get(Math.max(1L, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                if (result != null && result.outcome() == DomainSaveResult.Outcome.COMMITTED) {
                    databaseRevisions.put(playerId, result.revision());
                    dirtyPlayers.remove(playerId);
                } else {
                    allCommitted = false;
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            } catch (ExecutionException | TimeoutException exception) {
                allCommitted = false;
            }
        }
        return allCommitted;
    }

    private Map<String, Object> databasePayload(UUID playerId) {
        Map<String, Object> entries = new LinkedHashMap<>();
        for (Map.Entry<String, PityState> entry : states.entrySet()) {
            if (!playerId.equals(databaseUuid(entry.getKey()))) {
                continue;
            }
            PityState state = entry.getValue();
            Map<String, Object> value = new LinkedHashMap<>();
            value.put(KEY_COUNTER, state.getCounter());
            value.put(KEY_LAST_TRIGGER_TIME, state.getLastTriggerTime());
            value.put(KEY_TRIGGERED, state.isTriggered());
            entries.put(entry.getKey().substring(entry.getKey().indexOf('|') + 1), value);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put(KEY_SCHEMA_VERSION, DATABASE_SCHEMA_VERSION);
        root.put(KEY_ENTRIES, entries);
        return root;
    }

    private void markDirty(String composite) {
        dirty = true;
        UUID playerId = databaseUuid(composite);
        if (playerId != null && databaseRepository != null) {
            dirtyPlayers.add(playerId);
        }
    }

    private boolean fileManaged(String composite) {
        return databaseRepository == null || databaseUuid(composite) == null;
    }

    private static UUID databaseUuid(String composite) {
        String[] parts = composite.split("\\|", 3);
        if (parts.length != 3 || !PLAYER_SCOPE.equals(parts[0])) {
            return null;
        }
        try {
            return UUID.fromString(parts[2]);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static long toLong(Object value, long fallback) {
        return value instanceof Number number ? number.longValue() : fallback;
    }

    private <T> T await(CompletableFuture<T> future, long deadline)
            throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(Math.max(1L, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
    }

    private static String encodeKey(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeKey(String value) {
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private static String composite(String scope, String group, String key) {
        String normalizedScope = Texts.lower(scope);
        String normalizedGroup = Texts.lower(group);
        String normalizedKey = Texts.toStringSafe(key);
        if (Texts.isBlank(normalizedGroup) || Texts.isBlank(normalizedKey)) {
            return "";
        }
        return normalizedScope + "|" + normalizedGroup + "|" + normalizedKey;
    }
}
