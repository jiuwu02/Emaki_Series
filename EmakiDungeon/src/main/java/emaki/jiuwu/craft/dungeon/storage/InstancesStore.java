package emaki.jiuwu.craft.dungeon.storage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;

public final class InstancesStore {

    public record Entry(String dungeonId, String state, String partyId, List<String> players, long createdAt) {
    }

    public static final class Snapshot {

        private final Map<String, Entry> instances = new ConcurrentHashMap<>();
        private final List<String> residual = new CopyOnWriteArrayList<>();

        public Map<String, Entry> instances() {
            return Collections.unmodifiableMap(new LinkedHashMap<>(instances));
        }

        public List<String> residual() {
            return List.copyOf(residual);
        }

        public Entry instance(String worldName) {
            return instances.get(worldName);
        }

        public void put(String worldName, Entry entry) {
            instances.put(worldName, entry);
        }

        public void remove(String worldName) {
            instances.remove(worldName);
        }

        public void addResidual(String worldName) {
            if (worldName != null && !residual.contains(worldName)) {
                residual.add(worldName);
            }
        }

        public void clear() {
            instances.clear();
            residual.clear();
        }
    }

    private static final String CURRENT_VERSION = "1.0.0";

    private final Plugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final File file;
    private final Snapshot snapshot = new Snapshot();

    public InstancesStore(Plugin plugin, ExecutionDispatcher dispatcher, File dataFolder) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.file = new File(dataFolder, "instances.yml");
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    public void put(String worldName, Entry entry) {
        snapshot.put(worldName, entry);
    }

    public void remove(String worldName) {
        snapshot.remove(worldName);
    }

    public synchronized void load() {
        if (!file.isFile()) {
            return;
        }
        YamlSection section = YamlFiles.load(file);
        if (section == null || section.isEmpty()) {
            return;
        }
        String version = section.getString("version", "");
        if (!CURRENT_VERSION.equals(version)) {
            throw new IllegalStateException("instances.yml version mismatch: " + version);
        }
        YamlSection instances = section.getSection("instances");
        if (instances != null) {
            for (String worldName : instances.getKeys(false)) {
                YamlSection entrySection = instances.getSection(worldName);
                if (entrySection == null) {
                    continue;
                }
                Entry entry = new Entry(
                        entrySection.getString("dungeon", ""),
                        entrySection.getString("state", ""),
                        entrySection.getString("party", ""),
                        entrySection.getStringList("players"),
                        entrySection.getDouble("created-at", 0.0D).longValue()
                );
                snapshot.put(worldName, entry);
            }
        }
        for (String worldName : section.getStringList("residual")) {
            snapshot.addResidual(worldName);
        }
    }

    public void saveAsync() {
        dispatcher.runAsync(plugin, this::saveSync);
    }

    public synchronized void saveSync() {
        Map<String, Object> instances = new LinkedHashMap<>();
        for (Map.Entry<String, Entry> pair : snapshot.instances().entrySet()) {
            Map<String, Object> fields = new LinkedHashMap<>();
            Entry entry = pair.getValue();
            fields.put("dungeon", entry.dungeonId());
            fields.put("state", entry.state());
            if (entry.partyId() != null && !entry.partyId().isEmpty()) {
                fields.put("party", entry.partyId());
            }
            fields.put("players", new ArrayList<>(entry.players()));
            fields.put("created-at", entry.createdAt());
            instances.put(pair.getKey(), fields);
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("version", CURRENT_VERSION);
        values.put("instances", instances);
        values.put("residual", new ArrayList<>(snapshot.residual()));
        try {
            atomicWrite(values);
        } catch (IOException ignored) {
        }
    }

    private void atomicWrite(Map<String, Object> values) throws IOException {
        Path target = file.toPath();
        Path temp = target.resolveSibling(file.getName() + "." + java.util.UUID.randomUUID() + ".tmp");
        YamlFiles.save(temp.toFile(), values);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
