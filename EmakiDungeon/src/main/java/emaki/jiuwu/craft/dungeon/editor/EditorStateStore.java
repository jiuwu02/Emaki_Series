package emaki.jiuwu.craft.dungeon.editor;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import java.io.IOException;
import org.bukkit.plugin.Plugin;

public final class EditorStateStore {

    private record State(int generation, String fingerprint, UUID holder, String world, long since) {

        State withLock(UUID player, String worldName) {
            return new State(generation, fingerprint, player, worldName, System.currentTimeMillis());
        }

        State withoutLock() {
            return new State(generation, fingerprint, null, null, 0L);
        }

        State advanced(int nextGeneration, String nextFingerprint) {
            return new State(nextGeneration, nextFingerprint, holder, world, since);
        }
    }

    private final Plugin plugin;
    private final File editorRoot;
    private final File templateRoot;
    private final Map<String, State> states = new ConcurrentHashMap<>();

    public EditorStateStore(Plugin plugin, File dataFolder) {
        this.plugin = plugin;
        this.editorRoot = new File(dataFolder, "editor");
        this.templateRoot = new File(dataFolder, "world");
    }

    public void loadAll(List<String> dungeonIds) {
        for (String dungeonId : dungeonIds) {
            String fingerprint = fingerprintOf(dungeonId);
            State state = read(dungeonId);
            if (state == null) {
                state = new State(1, fingerprint, null, null, 0L);
                write(dungeonId, state);
            } else {
                int generation = state.generation();
                if (!fingerprint.equals(state.fingerprint())) {
                    generation++;
                }
                state = new State(generation, fingerprint, state.holder(), state.world(), state.since());
                if (state.holder() != null) {
                    state = state.withoutLock();
                }
                write(dungeonId, state);
            }
            states.put(dungeonId, state);
        }
    }

    public int generationOf(String dungeonId) {
        State state = dungeonId == null ? null : states.get(dungeonId);
        return state == null ? 1 : state.generation();
    }

    public synchronized boolean tryAcquire(String dungeonId, UUID player, String worldName) {
        if (dungeonId == null || player == null) {
            return false;
        }
        State state = states.computeIfAbsent(dungeonId, key -> new State(1, fingerprintOf(key), null, null, 0L));
        if (state.holder() != null && !player.equals(state.holder())) {
            return false;
        }
        State locked = state.withLock(player, worldName);
        states.put(dungeonId, locked);
        write(dungeonId, locked);
        return true;
    }

    public synchronized void release(String dungeonId) {
        State state = states.get(dungeonId);
        if (state == null || state.holder() == null) {
            return;
        }
        State released = state.withoutLock();
        states.put(dungeonId, released);
        write(dungeonId, released);
    }

    public synchronized int bump(String dungeonId) {
        State state = states.computeIfAbsent(dungeonId, key -> new State(1, fingerprintOf(key), null, null, 0L));
        State advanced = state.advanced(state.generation() + 1, fingerprintOf(dungeonId));
        states.put(dungeonId, advanced);
        write(dungeonId, advanced);
        return advanced.generation();
    }

    public UUID holderOf(String dungeonId) {
        State state = states.get(dungeonId);
        return state == null ? null : state.holder();
    }

    public String worldOf(String dungeonId) {
        State state = states.get(dungeonId);
        return state == null ? null : state.world();
    }

    private State read(String dungeonId) {
        File file = stateFile(dungeonId);
        if (!file.isFile()) {
            return null;
        }
        YamlSection section = YamlFiles.load(file);
        if (section == null || section.isEmpty()) {
            return null;
        }
        int generation = Math.max(1, section.getInt("generation", 1));
        String fingerprint = section.getString("fingerprint", "");
        YamlSection lock = section.getSection("lock");
        UUID holder = null;
        String world = null;
        long since = 0L;
        if (lock != null && !lock.isEmpty()) {
            try {
                holder = UUID.fromString(lock.getString("player", ""));
            } catch (IllegalArgumentException ignored) {
                holder = null;
            }
            world = lock.getString("world", "");
            Object rawSince = lock.get("since");
            since = rawSince instanceof Number number ? number.longValue() : 0L;
        }
        return new State(generation, fingerprint, holder, world, since);
    }

    private void write(String dungeonId, State state) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("generation", state.generation());
        root.put("fingerprint", state.fingerprint());
        if (state.holder() != null) {
            Map<String, Object> lock = new LinkedHashMap<>();
            lock.put("player", state.holder().toString());
            lock.put("world", state.world() == null ? "" : state.world());
            lock.put("since", state.since());
            root.put("lock", lock);
        }
        try {
            YamlFiles.save(stateFile(dungeonId), root);
        } catch (IOException exception) {
            plugin.getLogger().warning("[editor] state save failed | dungeon=" + dungeonId
                    + " | error=" + exception.getMessage());
        }
    }

    private File stateFile(String dungeonId) {
        return new File(editorRoot, dungeonId + ".yml");
    }

    private String fingerprintOf(String dungeonId) {
        File dir = dungeonId == null ? null : new File(templateRoot, dungeonId);
        if (dir == null || !dir.isDirectory()) {
            return "0:0";
        }
        long[] totals = new long[2];
        fingerprintStep(dir, totals);
        return totals[0] + ":" + totals[1];
    }

    private void fingerprintStep(File file, long[] totals) {
        File[] children = file.listFiles();
        if (children == null) {
            totals[0]++;
            totals[1] = Math.max(totals[1], file.lastModified());
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                fingerprintStep(child, totals);
            } else {
                totals[0]++;
                totals[1] = Math.max(totals[1], child.lastModified());
            }
        }
    }
}
