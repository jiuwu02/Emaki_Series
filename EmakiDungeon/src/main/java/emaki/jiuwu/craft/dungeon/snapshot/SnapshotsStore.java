package emaki.jiuwu.craft.dungeon.snapshot;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;

public final class SnapshotsStore {

    private final Plugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final File root;

    public SnapshotsStore(Plugin plugin, ExecutionDispatcher dispatcher, File dataFolder) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.root = new File(dataFolder, "snapshots");
    }

    public void writeAsync(PlayerSnapshot snapshot, UUID playerId) {
        dispatcher.runAsync(plugin, () -> writeSync(snapshot, playerId));
    }

    public void writeSync(PlayerSnapshot snapshot, UUID playerId) {
        if (snapshot == null || playerId == null) {
            return;
        }
        File dir = new File(root, snapshot.instanceId());
        File target = new File(dir, playerId + ".yml");
        try {
            Files.createDirectories(dir.toPath());
            Path temp = target.toPath().resolveSibling(target.getName() + "." + java.util.UUID.randomUUID() + ".tmp");
            YamlFiles.save(temp.toFile(), snapshot.serialize().asMap());
            try {
                Files.move(temp, target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException exception) {
                Files.move(temp, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
        }
    }

    public PlayerSnapshot read(String instanceId, UUID playerId) {
        if (instanceId == null || instanceId.isEmpty() || playerId == null) {
            return null;
        }
        File target = new File(new File(root, instanceId), playerId + ".yml");
        if (!target.isFile()) {
            return null;
        }
        YamlSection section = YamlFiles.load(target);
        if (section == null || section.isEmpty()) {
            return null;
        }
        return PlayerSnapshot.deserialize(section);
    }

    public boolean exists(String instanceId, UUID playerId) {
        if (instanceId == null || instanceId.isEmpty() || playerId == null) {
            return false;
        }
        return new File(new File(root, instanceId), playerId + ".yml").isFile();
    }

    public void delete(String instanceId, UUID playerId) {
        if (instanceId == null || instanceId.isEmpty() || playerId == null) {
            return;
        }
        File target = new File(new File(root, instanceId), playerId + ".yml");
        try {
            Files.deleteIfExists(target.toPath());
        } catch (IOException ignored) {
        }
    }

    public List<String> listInstanceIds() {
        File[] children = root.listFiles(File::isDirectory);
        if (children == null) {
            return List.of();
        }
        List<String> ids = new ArrayList<>(children.length);
        for (File child : children) {
            ids.add(child.getName());
        }
        return ids;
    }
}
