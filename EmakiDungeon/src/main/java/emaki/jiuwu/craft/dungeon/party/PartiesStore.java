package emaki.jiuwu.craft.dungeon.party;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.data.SharedEntry;
import emaki.jiuwu.craft.corelib.api.data.SharedStateRepository;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.debug.FailureRecorder;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.dungeon.log.DungeonLogTag;

public final class PartiesStore {

    private static final String CURRENT_VERSION = "1.0.0";
    private static final String DATABASE_NAMESPACE = "dungeon.party";

    private final Plugin plugin;
    private final MessageService messages;
    private final ExecutionDispatcher dispatcher;
    private final File file;
    private final Map<UUID, Party> parties = new ConcurrentHashMap<>();
    private volatile SharedStateRepository sharedState;

    public PartiesStore(Plugin plugin, MessageService messages, ExecutionDispatcher dispatcher, File dataFolder) {
        this.plugin = plugin;
        this.messages = messages;
        this.dispatcher = dispatcher;
        this.file = new File(dataFolder, "parties.yml");
    }

    public void attachSharedState(SharedStateRepository sharedState) {
        this.sharedState = sharedState;
    }

    public File file() {
        return file;
    }

    public Map<UUID, Party> parties() {
        return parties;
    }

    public synchronized void load() {
        SharedStateRepository shared = sharedState;
        if (shared != null) {
            loadShared(shared);
            return;
        }
        if (!file.isFile()) {
            return;
        }
        YamlSection section = YamlFiles.load(file);
        if (section == null || section.isEmpty()) {
            return;
        }
        String version = section.getString("version", "");
        if (!CURRENT_VERSION.equals(version)) {
            throw new IllegalStateException("parties.yml version mismatch: " + version);
        }
        YamlSection partiesSection = section.getSection("parties");
        if (partiesSection == null) {
            return;
        }
        for (String key : partiesSection.getKeys(false)) {
            YamlSection entry = partiesSection.getSection(key);
            if (entry == null) {
                continue;
            }
            Party party = parseParty(key, entry);
            if (party != null) {
                parties.put(party.id(), party);
            }
        }
    }

    private void loadShared(SharedStateRepository shared) {
        CompletableFuture<List<SharedEntry>> rows = shared.list(DATABASE_NAMESPACE);
        Throwable failure = failureOf(rows);
        if (failure != null) {
            throw new IllegalStateException("parties shared state load failed", failure);
        }
        for (SharedEntry row : rows.getNow(List.of())) {
            YamlSection entry = YamlFiles.load(row.value());
            if (entry == null || entry.isEmpty()) {
                continue;
            }
            String version = entry.getString("version", "");
            if (!CURRENT_VERSION.equals(version)) {
                throw new IllegalStateException("parties.yml version mismatch: " + version);
            }
            Party party = parseParty(row.key(), entry);
            if (party != null) {
                parties.put(party.id(), party);
            }
        }
    }

    private Party parseParty(String key, YamlSection entry) {
        UUID partyId;
        UUID leaderId;
        try {
            partyId = UUID.fromString(key);
            leaderId = UUID.fromString(entry.getString("leader", ""));
        } catch (IllegalArgumentException exception) {
            return null;
        }
        List<UUID> members = new ArrayList<>();
        for (String rawMember : entry.getStringList("members")) {
            try {
                members.add(UUID.fromString(rawMember));
            } catch (IllegalArgumentException ignored) {
                FailureRecorder.recordIgnoredFailure("EmakiDungeon", "PartiesStore.java#1", ignored);
            }
        }
        if (members.isEmpty() || !members.contains(leaderId)) {
            return null;
        }
        Party party = new Party(partyId, leaderId);
        for (UUID memberId : members) {
            if (!memberId.equals(leaderId)) {
                party.add(memberId);
            }
        }
        return party;
    }

    public void saveAsync() {
        dispatcher.runAsync(plugin, () -> saveSync());
    }

    public synchronized boolean saveSync() {
        SharedStateRepository shared = sharedState;
        if (shared != null) {
            return saveShared(shared);
        }
        Map<String, Object> entries = new LinkedHashMap<>();
        for (Party party : parties.values()) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("leader", String.valueOf(party.leaderId()));
            List<String> members = new ArrayList<>(party.size());
            for (UUID memberId : party.members()) {
                members.add(String.valueOf(memberId));
            }
            fields.put("members", members);
            entries.put(String.valueOf(party.id()), fields);
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("version", CURRENT_VERSION);
        values.put("parties", entries);
        try {
            YamlFiles.save(file, values);
            return true;
        } catch (IOException exception) {
            messages.warning(DungeonLogTag.STORAGE, "console.parties_save_failed", Map.of(
                    "file", String.valueOf(file.getAbsolutePath()),
                    "error", String.valueOf(exception.getMessage())));
            return false;
        }
    }

    private boolean saveShared(SharedStateRepository shared) {
        Map<String, String> rows = new LinkedHashMap<>();
        for (Party party : parties.values()) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("version", CURRENT_VERSION);
            fields.put("leader", String.valueOf(party.leaderId()));
            List<String> members = new ArrayList<>(party.size());
            for (UUID memberId : party.members()) {
                members.add(String.valueOf(memberId));
            }
            fields.put("members", members);
            rows.put(String.valueOf(party.id()), YamlFiles.dump(fields));
        }
        String failureText = "";
        for (Map.Entry<String, String> row : rows.entrySet()) {
            Throwable failure = failureOf(shared.put(DATABASE_NAMESPACE, row.getKey(), row.getValue()));
            if (failure != null && failureText.isEmpty()) {
                failureText = String.valueOf(failure);
            }
        }
        CompletableFuture<List<SharedEntry>> existing = shared.list(DATABASE_NAMESPACE);
        if (failureOf(existing) == null) {
            for (SharedEntry row : existing.getNow(List.of())) {
                if (!rows.containsKey(row.key())) {
                    Throwable failure = failureOf(shared.remove(DATABASE_NAMESPACE, row.key()));
                    if (failure != null && failureText.isEmpty()) {
                        failureText = String.valueOf(failure);
                    }
                }
            }
        }
        if (!failureText.isEmpty()) {
            messages.warning(DungeonLogTag.STORAGE, "console.parties_save_failed", Map.of(
                    "file", "database:" + DATABASE_NAMESPACE,
                    "error", failureText));
            return false;
        }
        return true;
    }

    private Throwable failureOf(CompletableFuture<?> future) {
        if (!awaitUntil(future, System.nanoTime() + TimeUnit.SECONDS.toNanos(5L))) {
            return new IllegalStateException("共享状态操作超时");
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
}
