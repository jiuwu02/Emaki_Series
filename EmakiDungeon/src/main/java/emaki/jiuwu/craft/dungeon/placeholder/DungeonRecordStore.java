package emaki.jiuwu.craft.dungeon.placeholder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;

public final class DungeonRecordStore {

    public record DungeonRecord(boolean cleared,
            long firstClearMs,
            long bestTimeMs,
            long clearCount,
            double bestScore,
            Set<String> difficulties) {
    }

    private static final String CURRENT_VERSION = "1.0.0";
    private static final DungeonRecord EMPTY =
            new DungeonRecord(false, 0L, 0L, 0L, 0.0D, Set.of());

    private final Plugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final File root;
    private final Map<UUID, Map<String, DungeonRecord>> cache = new ConcurrentHashMap<>();

    public DungeonRecordStore(Plugin plugin, ExecutionDispatcher dispatcher, File dataFolder) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.root = new File(dataFolder, "records");
    }

    public DungeonRecord find(UUID playerId, String dungeonId) {
        if (playerId == null || dungeonId == null) {
            return EMPTY;
        }
        Map<String, DungeonRecord> all = cache.computeIfAbsent(playerId, this::loadAll);
        DungeonRecord record = all.get(dungeonId);
        return record == null ? EMPTY : record;
    }

    public void recordAsync(UUID playerId, String dungeonId, String difficulty,
            long durationMs, double score, long timestamp) {
        dispatcher.runAsync(plugin,
                () -> recordSync(playerId, dungeonId, difficulty, durationMs, score, timestamp));
    }

    public synchronized void recordSync(UUID playerId, String dungeonId, String difficulty,
            long durationMs, double score, long timestamp) {
        if (playerId == null || dungeonId == null) {
            return;
        }
        Map<String, DungeonRecord> all = new LinkedHashMap<>(
                cache.computeIfAbsent(playerId, this::loadAll));
        DungeonRecord previous = all.getOrDefault(dungeonId, EMPTY);
        long firstClearMs = previous.firstClearMs() == 0L
                ? timestamp : Math.min(previous.firstClearMs(), timestamp);
        long bestTimeMs = previous.bestTimeMs();
        if (durationMs > 0L && (bestTimeMs == 0L || durationMs < bestTimeMs)) {
            bestTimeMs = durationMs;
        }
        double bestScore = Math.max(previous.bestScore(), score);
        Set<String> difficulties = new LinkedHashSet<>(previous.difficulties());
        if (difficulty != null && !difficulty.isBlank()) {
            difficulties.add(difficulty);
        }
        all.put(dungeonId, new DungeonRecord(true, firstClearMs, bestTimeMs,
                previous.clearCount() + 1L, bestScore, Set.copyOf(difficulties)));
        Map<String, DungeonRecord> snapshot = Map.copyOf(all);
        cache.put(playerId, snapshot);
        save(playerId, snapshot);
    }

    private Map<String, DungeonRecord> loadAll(UUID playerId) {
        Map<String, DungeonRecord> all = new LinkedHashMap<>();
        File file = fileOf(playerId);
        if (!file.isFile()) {
            return all;
        }
        YamlSection section = YamlFiles.load(file);
        if (section == null || section.isEmpty()) {
            return all;
        }
        YamlSection records = section.getSection("records");
        if (records == null) {
            return all;
        }
        for (String key : records.getKeys(false)) {
            YamlSection entry = records.getSection(key);
            if (entry == null) {
                continue;
            }
            String dungeonId = entry.getString("dungeon-id", "");
            if (dungeonId.isEmpty()) {
                continue;
            }
            long firstClearMs = entry.getDouble("first-clear-ms", 0.0D).longValue();
            long bestTimeMs = entry.getDouble("best-time-ms", 0.0D).longValue();
            long clearCount = entry.getDouble("clear-count", 0.0D).longValue();
            double bestScore = entry.getDouble("best-score", 0.0D);
            Boolean clearedValue = entry.getBoolean("cleared", false);
            boolean cleared = Boolean.TRUE.equals(clearedValue) || clearCount > 0L;
            Set<String> difficulties = new LinkedHashSet<>();
            YamlSection diffSection = entry.getSection("difficulties");
            if (diffSection != null) {
                for (String diff : diffSection.getKeys(false)) {
                    Boolean value = diffSection.getBoolean(diff, false);
                    if (Boolean.TRUE.equals(value)) {
                        difficulties.add(diff);
                    }
                }
            }
            all.put(dungeonId, new DungeonRecord(cleared, firstClearMs, bestTimeMs,
                    clearCount, bestScore, Set.copyOf(difficulties)));
        }
        return all;
    }

    private void save(UUID playerId, Map<String, DungeonRecord> all) {
        Map<String, Object> recordNodes = new LinkedHashMap<>();
        List<String> ids = new ArrayList<>(all.keySet());
        for (int i = 0; i < ids.size(); i++) {
            String dungeonId = ids.get(i);
            DungeonRecord record = all.get(dungeonId);
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("dungeon-id", dungeonId);
            fields.put("cleared", record.cleared());
            fields.put("first-clear-ms", record.firstClearMs());
            fields.put("best-time-ms", record.bestTimeMs());
            fields.put("clear-count", record.clearCount());
            fields.put("best-score", record.bestScore());
            Map<String, Object> diffs = new LinkedHashMap<>();
            for (String diff : record.difficulties()) {
                diffs.put(diff, true);
            }
            fields.put("difficulties", diffs);
            recordNodes.put("rec-" + (i + 1), fields);
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("version", CURRENT_VERSION);
        values.put("records", recordNodes);
        File file = fileOf(playerId);
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
        }
    }

    private File fileOf(UUID playerId) {
        return new File(root, playerId + ".yml");
    }
}
