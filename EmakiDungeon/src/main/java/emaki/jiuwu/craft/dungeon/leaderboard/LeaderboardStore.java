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

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;

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

    private final Plugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final File root;

    public LeaderboardStore(Plugin plugin, ExecutionDispatcher dispatcher, File dataFolder) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.root = new File(dataFolder, "leaderboards");
    }

    public void recordAsync(String dungeonId, Entry entry, int maxEntries) {
        dispatcher.runAsync(plugin, () -> recordSync(dungeonId, entry, maxEntries));
    }

    public synchronized void recordSync(String dungeonId, Entry entry, int maxEntries) {
        if (dungeonId == null || entry == null) {
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
            Path temp = target.resolveSibling(file.getName() + "." + java.util.UUID.randomUUID() + ".tmp");
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
}
