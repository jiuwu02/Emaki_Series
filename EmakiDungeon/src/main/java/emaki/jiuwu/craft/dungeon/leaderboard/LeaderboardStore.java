package emaki.jiuwu.craft.dungeon.leaderboard;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.async.AsyncWaits;
import emaki.jiuwu.craft.corelib.api.data.SharedEntry;
import emaki.jiuwu.craft.corelib.api.data.SharedStateRepository;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.debug.FailureRecorder;
import java.util.UUID;

public final class LeaderboardStore {

    public record MemberScore(String name, double score) {
    }

    public record Entry(String difficulty,
            long durationMs,
            double score,
            List<MemberScore> members,
            long timestamp) {
    }

    private static final String CURRENT_VERSION = "1.0.0";
    private static final String DATABASE_BOARD_PREFIX = "dungeon.board.";
    private static final String DATABASE_BEST_PREFIX = "dungeon.best.";

    private final Plugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final File root;
    private volatile SharedStateRepository sharedState;

    public LeaderboardStore(Plugin plugin, ExecutionDispatcher dispatcher, File dataFolder) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.root = new File(dataFolder, "leaderboards");
    }

    public void attachSharedState(SharedStateRepository sharedState) {
        this.sharedState = sharedState;
    }

    public void recordAsync(String dungeonId, Entry entry, int maxEntries) {
        dispatcher.runAsync(plugin, () -> recordSync(dungeonId, entry, maxEntries));
    }

    public synchronized void recordSync(String dungeonId, Entry entry, int maxEntries) {
        if (dungeonId == null || entry == null) {
            return;
        }
        SharedStateRepository shared = sharedState;
        if (shared != null) {
            recordShared(shared, dungeonId, entry, Math.max(1, maxEntries));
            return;
        }
        List<Entry> entries = load(dungeonId);
        entries.add(entry);
        entries.sort(rankComparator());
        while (entries.size() > Math.max(1, maxEntries)) {
            entries.remove(entries.size() - 1);
        }
        save(dungeonId, entries);
    }

    public List<Entry> top(String dungeonId, int limit) {
        if (dungeonId == null) {
            return List.of();
        }
        SharedStateRepository shared = sharedState;
        if (shared != null) {
            return topShared(shared, dungeonId, limit);
        }
        List<Entry> entries = load(dungeonId);
        entries.sort(rankComparator());
        if (limit > 0 && entries.size() > limit) {
            return new ArrayList<>(entries.subList(0, limit));
        }
        return entries;
    }

    public List<Entry> personalBest(String dungeonId, String playerName) {
        if (dungeonId == null || playerName == null) {
            return List.of();
        }
        SharedStateRepository shared = sharedState;
        if (shared != null) {
            return personalBestShared(shared, dungeonId, playerName);
        }
        return load(dungeonId).stream()
                .filter(entry -> entry.members().stream()
                        .anyMatch(member -> playerName.equalsIgnoreCase(member.name())))
                .sorted(rankComparator())
                .limit(1)
                .toList();
    }

    private static Comparator<Entry> rankComparator() {
        return Comparator
                .comparingDouble(Entry::score).reversed()
                .thenComparingLong(Entry::durationMs);
    }

    private record SharedRow(String key, String value, Entry entry) {
    }

    private void recordShared(SharedStateRepository shared, String dungeonId, Entry entry, int maxEntries) {
        String serialized = serializeEntry(entry);
        String boardNamespace = DATABASE_BOARD_PREFIX + dungeonId;
        String bestNamespace = DATABASE_BEST_PREFIX + dungeonId;
        CompletableFuture<Long> sequence = shared.nextSequence(boardNamespace);
        Throwable failure = failureOf(sequence);
        if (failure != null) {
            recordFailure(failure);
            return;
        }
        failure = failureOf(shared.put(boardNamespace, String.valueOf(sequence.getNow(0L)), serialized));
        if (failure != null) {
            recordFailure(failure);
            return;
        }
        for (MemberScore member : entry.members()) {
            CompletableFuture<String> existing = shared.get(bestNamespace, member.name());
            if (failureOf(existing) != null) {
                continue;
            }
            Entry current = parseEntry(existing.getNow(null));
            if (current == null || ranksBetter(entry, current)) {
                failure = failureOf(shared.put(bestNamespace, member.name(), serialized));
                if (failure != null) {
                    recordFailure(failure);
                }
            }
        }
        trimSharedBoard(shared, boardNamespace, bestNamespace, maxEntries);
    }

    private void trimSharedBoard(SharedStateRepository shared, String boardNamespace, String bestNamespace,
            int maxEntries) {
        CompletableFuture<List<SharedEntry>> rows = shared.list(boardNamespace);
        if (failureOf(rows) != null) {
            return;
        }
        List<SharedRow> parsed = new ArrayList<>();
        for (SharedEntry row : rows.getNow(List.of())) {
            Entry entry = parseEntry(row.value());
            if (entry != null) {
                parsed.add(new SharedRow(row.key(), row.value(), entry));
            }
        }
        parsed.sort(Comparator.comparing(SharedRow::entry, rankComparator()));
        List<String> removedValues = new ArrayList<>();
        for (int i = maxEntries; i < parsed.size(); i++) {
            SharedRow row = parsed.get(i);
            failureOf(shared.remove(boardNamespace, row.key()));
            removedValues.add(row.value());
        }
        if (removedValues.isEmpty()) {
            return;
        }
        CompletableFuture<List<SharedEntry>> bestRows = shared.list(bestNamespace);
        if (failureOf(bestRows) != null) {
            return;
        }
        for (SharedEntry row : bestRows.getNow(List.of())) {
            if (removedValues.contains(row.value())) {
                failureOf(shared.remove(bestNamespace, row.key()));
            }
        }
    }

    private List<Entry> topShared(SharedStateRepository shared, String dungeonId, int limit) {
        CompletableFuture<List<SharedEntry>> rows = shared.list(DATABASE_BOARD_PREFIX + dungeonId);
        if (failureOf(rows) != null) {
            return List.of();
        }
        List<Entry> entries = new ArrayList<>();
        for (SharedEntry row : rows.getNow(List.of())) {
            Entry entry = parseEntry(row.value());
            if (entry != null) {
                entries.add(entry);
            }
        }
        entries.sort(rankComparator());
        if (limit > 0 && entries.size() > limit) {
            return new ArrayList<>(entries.subList(0, limit));
        }
        return entries;
    }

    private List<Entry> personalBestShared(SharedStateRepository shared, String dungeonId, String playerName) {
        CompletableFuture<List<SharedEntry>> rows = shared.list(DATABASE_BEST_PREFIX + dungeonId);
        if (failureOf(rows) != null) {
            return List.of();
        }
        return rows.getNow(List.of()).stream()
                .filter(row -> playerName.equalsIgnoreCase(row.key()))
                .map(row -> parseEntry(row.value()))
                .filter(entry -> entry != null)
                .sorted(rankComparator())
                .limit(1)
                .toList();
    }

    private String serializeEntry(Entry entry) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("difficulty", entry.difficulty());
        fields.put("duration-ms", entry.durationMs());
        fields.put("score", entry.score());
        Map<String, Object> members = new LinkedHashMap<>();
        for (MemberScore member : entry.members()) {
            members.put(member.name(), member.score());
        }
        fields.put("members", members);
        fields.put("timestamp", entry.timestamp());
        return YamlFiles.dump(fields);
    }

    private Entry parseEntry(String serialized) {
        if (serialized == null || serialized.isEmpty()) {
            return null;
        }
        YamlSection entrySection;
        try {
            entrySection = YamlFiles.load(serialized);
        } catch (RuntimeException exception) {
            return null;
        }
        if (entrySection == null || entrySection.isEmpty()) {
            return null;
        }
        List<MemberScore> members = new ArrayList<>();
        YamlSection membersSection = entrySection.getSection("members");
        if (membersSection != null) {
            for (String memberName : membersSection.getKeys(false)) {
                Double memberScore = membersSection.getDouble(memberName, 0.0D);
                members.add(new MemberScore(memberName,
                        memberScore == null ? 0.0D : memberScore));
            }
        }
        return new Entry(
                entrySection.getString("difficulty", "normal"),
                entrySection.getDouble("duration-ms", 0.0D).longValue(),
                entrySection.getDouble("score", 0.0D),
                members,
                entrySection.getDouble("timestamp", 0.0D).longValue()
        );
    }

    private boolean ranksBetter(Entry candidate, Entry current) {
        return rankComparator().compare(candidate, current) < 0;
    }

    private void recordFailure(Throwable failure) {
        FailureRecorder.recordIgnoredFailure("EmakiDungeon", "LeaderboardStore.java#1", failure);
    }

    private Throwable failureOf(CompletableFuture<?> future) {
        if (!AsyncWaits.awaitUntil(future, System.nanoTime() + TimeUnit.SECONDS.toNanos(5L))) {
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


    private File fileOf(String dungeonId) {
        return new File(root, dungeonId + ".yml");
    }

    private List<Entry> load(String dungeonId) {
        List<Entry> entries = new ArrayList<>();
        File file = fileOf(dungeonId);
        if (!file.isFile()) {
            return entries;
        }
        YamlSection section = YamlFiles.load(file);
        if (section == null || section.isEmpty()) {
            return entries;
        }
        YamlSection entriesSection = section.getSection("entries");
        if (entriesSection == null) {
            return entries;
        }
        for (String key : entriesSection.getKeys(false)) {
            YamlSection entrySection = entriesSection.getSection(key);
            if (entrySection == null) {
                continue;
            }
            List<MemberScore> members = new ArrayList<>();
            YamlSection membersSection = entrySection.getSection("members");
            if (membersSection != null) {
                for (String memberName : membersSection.getKeys(false)) {
                    Double memberScore = membersSection.getDouble(memberName, 0.0D);
                    members.add(new MemberScore(memberName,
                            memberScore == null ? 0.0D : memberScore));
                }
            }
            entries.add(new Entry(
                    entrySection.getString("difficulty", "normal"),
                    entrySection.getDouble("duration-ms", 0.0D).longValue(),
                    entrySection.getDouble("score", 0.0D),
                    members,
                    entrySection.getDouble("timestamp", 0.0D).longValue()
            ));
        }
        return entries;
    }

    private void save(String dungeonId, List<Entry> entries) {
        Map<String, Object> entryNodes = new LinkedHashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("difficulty", entry.difficulty());
            fields.put("duration-ms", entry.durationMs());
            fields.put("score", entry.score());
            Map<String, Object> members = new LinkedHashMap<>();
            for (MemberScore member : entry.members()) {
                members.put(member.name(), member.score());
            }
            fields.put("members", members);
            fields.put("timestamp", entry.timestamp());
            entryNodes.put("entry-" + (i + 1), fields);
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("version", CURRENT_VERSION);
        values.put("entries", entryNodes);
        File file = fileOf(dungeonId);
        try {
            Files.createDirectories(root.toPath());
            Path target = file.toPath();
            Path temp = target.resolveSibling(file.getName() + "." + UUID.randomUUID() + ".tmp");
            YamlFiles.save(temp.toFile(), values);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException exception) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            FailureRecorder.recordIgnoredFailure("EmakiDungeon", "LeaderboardStore.java#1", ignored);
        }
    }
}
