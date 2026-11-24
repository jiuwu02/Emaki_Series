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
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.log.LogTag;

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

    public record ReturnPoint(String world, double x, double y, double z, float yaw, float pitch,
            long since) {
    }

    private static final long RETURN_TTL_MILLIS = 24L * 60L * 60L * 1000L;

    private final Plugin plugin;
    private final MessageService messages;
    private final File editorRoot;
    private final File templateRoot;
    private final File returnFile;
    private final Map<String, State> states = new ConcurrentHashMap<>();
    private final Map<UUID, ReturnPoint> returns = new ConcurrentHashMap<>();

    public EditorStateStore(Plugin plugin, MessageService messages, File dataFolder) {
        this.plugin = plugin;
        this.messages = messages;
        this.editorRoot = new File(dataFolder, "editor");
        this.templateRoot = new File(dataFolder, "world");
        this.returnFile = new File(editorRoot, "returns.yml");
        loadReturns();
    }

    public ReturnPoint returnOf(UUID player) {
        ReturnPoint point = player == null ? null : returns.get(player);
        if (point == null) {
            return null;
        }
        if (System.currentTimeMillis() - point.since() > RETURN_TTL_MILLIS) {
            clearReturn(player);
            return null;
        }
        return point;
    }

    public void rememberReturn(UUID player, String world, double x, double y, double z, float yaw, float pitch) {
        if (player == null) {
            return;
        }
        returns.put(player, new ReturnPoint(world, x, y, z, yaw, pitch, System.currentTimeMillis()));
        saveReturns();
    }

    public void clearReturn(UUID player) {
        if (player == null || returns.remove(player) == null) {
            return;
        }
        saveReturns();
    }

    private void loadReturns() {
        File file = returnFile;
        if (!file.isFile()) {
            return;
        }
        YamlSection section = YamlFiles.load(file);
        if (section == null || section.isEmpty()) {
            return;
        }
        for (String key : section.getKeys(false)) {
            YamlSection entry = section.getSection(key);
            if (entry == null || entry.isEmpty()) {
                continue;
            }
            UUID player;
            try {
                player = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            Object rawSince = entry.get("since");
            long since = rawSince instanceof Number value ? value.longValue() : 0L;
            returns.put(player, new ReturnPoint(entry.getString("world", ""),
                    number(entry, "x"), number(entry, "y"), number(entry, "z"),
                    (float) number(entry, "yaw"), (float) number(entry, "pitch"), since));
        }
    }

    private double number(YamlSection section, String key) {
        Object raw = section.get(key);
        return raw instanceof Number value ? value.doubleValue() : 0.0D;
    }

    private void saveReturns() {
        Map<String, Object> root = new LinkedHashMap<>();
        for (Map.Entry<UUID, ReturnPoint> entry : returns.entrySet()) {
            ReturnPoint point = entry.getValue();
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("world", point.world());
            node.put("x", point.x());
            node.put("y", point.y());
            node.put("z", point.z());
            node.put("yaw", point.yaw());
            node.put("pitch", point.pitch());
            node.put("since", point.since());
            root.put(entry.getKey().toString(), node);
        }
        try {
            editorRoot.mkdirs();
            YamlFiles.save(returnFile, root);
        } catch (IOException exception) {
            messages.warning(LogTag.EDITOR, "console.editor_state_save_failed", Map.of(
                    "dungeon", "returns",
                    "error", String.valueOf(exception.getMessage())));
        }
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
            messages.warning(LogTag.EDITOR, "console.editor_state_save_failed", Map.of(
                    "dungeon", String.valueOf(dungeonId),
                    "error", String.valueOf(exception.getMessage())));
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
