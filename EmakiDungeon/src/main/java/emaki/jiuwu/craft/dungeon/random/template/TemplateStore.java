package emaki.jiuwu.craft.dungeon.random.template;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.debug.FailureRecorder;
import emaki.jiuwu.craft.dungeon.random.model.TemplateMeta;
import emaki.jiuwu.craft.dungeon.random.nbt.StructureNbt;

public final class TemplateStore {

    private final File root;

    public static TemplateStore forDungeon(File dataFolder, String dungeonId) {
        return new TemplateStore(new File(new File(new File(dataFolder, "world"), dungeonId), "structures"));
    }

    TemplateStore(File root) {
        this.root = root;
    }

    public File root() {
        return root;
    }

    public List<String> ids() {
        File[] files = root.listFiles((dir, name) -> name.endsWith(".yml"));
        java.util.List<String> ids = new java.util.ArrayList<>();
        if (files != null) {
            for (File file : files) {
                ids.add(file.getName().substring(0, file.getName().length() - 4));
            }
        }
        ids.sort(String::compareTo);
        return ids;
    }

    public boolean exists(String id) {
        return new File(root, id + ".yml").isFile();
    }

    public Map<String, TemplateMeta> loadAll() {
        Map<String, TemplateMeta> metas = new LinkedHashMap<>();
        for (String id : ids()) {
            TemplateMeta meta = loadMeta(id);
            if (meta != null) {
                metas.put(id, meta);
            }
        }
        return metas;
    }

    public TemplateMeta loadMeta(String id) {
        File file = new File(root, id + ".yml");
        if (!file.isFile()) {
            return null;
        }
        YamlSection section = YamlFiles.load(file);
        if (section == null || section.isEmpty()) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (String key : section.getKeys(true)) {
            Object value = section.get(key);
            if (value != null) {
                map.put(key, value);
            }
        }
        return TemplateMeta.fromMap(id, map);
    }

    public StructureNbt.StructureData loadTemplate(String id) {
        File file = new File(root, id + ".nbt");
        if (!file.isFile()) {
            return null;
        }
        try {
            return StructureNbt.read(file.toPath());
        } catch (IOException exception) {
            return null;
        }
    }

    public long nbtBytes(String id) {
        File file = new File(root, id + ".nbt");
        return file.isFile() ? file.length() : 0L;
    }

    public void save(String id, TemplateMeta meta, StructureNbt.StructureData data) throws IOException {
        if (!root.isDirectory() && !root.mkdirs()) {
            throw new IOException("无法创建模板目录: " + root);
        }
        Path nbtPath = new File(root, id + ".nbt").toPath();
        StructureNbt.write(nbtPath, data);
        Map<String, Object> map = new LinkedHashMap<>(meta.toMap());
        YamlFiles.save(new File(root, id + ".yml"), map);
    }

    public static void migrateLegacy(File dataFolder, List<String> dungeonIds) {
        File legacy = new File(dataFolder, "random-dungeon" + File.separator + "templates");
        File[] files = legacy.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".nbt"));
        if (files == null || files.length == 0) {
            return;
        }
        for (String dungeonId : dungeonIds) {
            File target = new File(new File(new File(dataFolder, "world"), dungeonId), "structures");
            for (File file : files) {
                File dest = new File(target, file.getName());
                if (dest.isFile()) {
                    continue;
                }
                try {
                    Files.createDirectories(target.toPath());
                    Files.copy(file.toPath(), dest.toPath());
                } catch (IOException exception) {
                    FailureRecorder.recordIgnoredFailure("EmakiDungeon", "TemplateStore.java#1", exception);
                }
            }
        }
        File backup = new File(dataFolder, "random-dungeon" + File.separator + "templates-backup");
        int suffix = 1;
        while (backup.exists()) {
            backup = new File(dataFolder, "random-dungeon" + File.separator + "templates-backup-" + (++suffix));
        }
        if (!legacy.renameTo(backup)) {
            FailureRecorder.recordIgnoredFailure("EmakiDungeon", "TemplateStore.java#2",
                    new IOException("旧模板目录改名失败: " + legacy));
        }
    }
}
