package emaki.jiuwu.craft.dungeon.random.lifecycle;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.dungeon.random.gen.Seeds;

public final class InstanceStore {

    private final File root;

    public InstanceStore(File dataFolder) {
        this.root = new File(dataFolder, "random-dungeon" + File.separator + "instances");
    }

    public File root() {
        return root;
    }

    public void save(RandomInstance instance) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("instance-world", instance.instanceId());
        map.put("dungeon-id", instance.dungeonId());
        map.put("state", instance.state().name());
        map.put("seed", instance.seed());
        Map<String, Object> sub = new LinkedHashMap<>();
        sub.put("topology", instance.seeds().topology());
        sub.put("layout", instance.seeds().layout());
        sub.put("assignment", instance.seeds().assignment());
        sub.put("orientation", instance.seeds().orientation());
        sub.put("link", instance.seeds().link());
        map.put("sub-seeds", sub);
        map.put("config-hash", instance.configHash());
        map.put("topology-hash", instance.topologyHash());
        map.put("room-count", instance.roomCount());
        map.put("created-at", instance.createdAt());
        map.put("events", new ArrayList<>(instance.events()));
        try {
            YamlFiles.save(fileOf(instance.instanceId()), map);
        } catch (IOException ignored) {
        }
    }

    private static long longValue(YamlSection section, String key, long fallback) {
        Object value = section.get(key);
        return value instanceof Number number ? number.longValue() : fallback;
    }

    public RandomInstance load(String instanceWorld) {
        File file = fileOf(instanceWorld);
        if (!file.isFile()) {
            return null;
        }
        YamlSection section = YamlFiles.load(file);
        if (section == null || section.isEmpty()) {
            return null;
        }
        YamlSection sub = section.getSection("sub-seeds");
        Seeds seeds = new Seeds(
                longValue(section, "seed", 0L),
                sub == null ? 0L : longValue(sub, "topology", 0L),
                sub == null ? 0L : longValue(sub, "layout", 0L),
                sub == null ? 0L : longValue(sub, "assignment", 0L),
                sub == null ? 0L : longValue(sub, "orientation", 0L),
                sub == null ? 0L : longValue(sub, "link", 0L));
        RandomInstance instance = new RandomInstance(
                instanceWorld,
                section.getString("dungeon-id", ""),
                longValue(section, "seed", 0L),
                seeds,
                section.getString("config-hash", ""),
                section.getString("topology-hash", ""),
                section.getInt("room-count", 0));
        try {
            instance.forceState(RandomInstanceState.valueOf(
                    section.getString("state", "CREATED")));
        } catch (IllegalArgumentException ignored) {
            instance.forceState(RandomInstanceState.CREATED);
        }
        return instance;
    }

    public List<String> listRecords() {
        File[] files = root.listFiles((dir, name) -> name.endsWith(".yml"));
        List<String> names = new ArrayList<>();
        if (files != null) {
            for (File file : files) {
                names.add(file.getName().substring(0, file.getName().length() - 4));
            }
        }
        names.sort(String::compareTo);
        return names;
    }

    private File fileOf(String instanceWorld) {
        return new File(root, instanceWorld + ".yml");
    }
}
