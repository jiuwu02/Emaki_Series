package emaki.jiuwu.craft.codex.advancement;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;































@SuppressWarnings("deprecation")
public final class UnsafeAdvancementPlatform implements AdvancementPlatform {


    private static final String PLURAL_DIR = "advancements";

    private static final String SINGULAR_DIR = "advancement";

    private final Logger logger;

    public UnsafeAdvancementPlatform(Logger logger) {
        this.logger = logger;
    }

    @Override
    public String id() {
        return "unsafe";
    }

    @Override
    public boolean register(NamespacedKey key, String json) {
        if (key == null || json == null) {
            return false;
        }
        try {







            boolean existed = Bukkit.getAdvancement(key) != null;
            if (existed) {
                Bukkit.getUnsafe().removeAdvancement(key);
            }
            boolean loaded = Bukkit.getUnsafe().loadAdvancement(key, json) != null;
            if (loaded) {






                mirrorToSingularDir(key, json);
            }
            return loaded;
        } catch (Throwable throwable) {
            logger.log(Level.WARNING, "[Codex] Failed to register advancement " + key + ": " + throwable.getMessage());
            return false;
        }
    }

    @Override
    public boolean remove(NamespacedKey key) {
        if (key == null) {
            return false;
        }
        try {
            return Bukkit.getUnsafe().removeAdvancement(key);
        } catch (Throwable throwable) {
            logger.log(Level.WARNING, "[Codex] Failed to remove advancement " + key + ": " + throwable.getMessage());
            return false;
        }
    }

    @Override
    public int removeAll(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            return 0;
        }
        List<NamespacedKey> toRemove = collectKeys(namespace);
        int removed = 0;
        for (NamespacedKey key : toRemove) {
            if (remove(key)) {
                removed++;
            }
        }





        int filesDeleted = purgeNamespaceDirs(namespace);





        if (removed > 0 || filesDeleted > 0) {
            reloadData();
        }
        return removed;
    }








    private List<NamespacedKey> collectKeys(String namespace) {
        List<NamespacedKey> keys = new ArrayList<>();
        try {
            Iterator<Advancement> iterator = Bukkit.advancementIterator();
            while (iterator.hasNext()) {
                NamespacedKey key = iterator.next().getKey();
                if (namespace.equals(key.getNamespace())) {
                    keys.add(key);
                }
            }
        } catch (Throwable throwable) {
            logger.log(Level.WARNING, "[Codex] Failed to enumerate advancements for namespace "
                    + namespace + ": " + throwable.getMessage());
        }
        return keys;
    }













    private void mirrorToSingularDir(NamespacedKey key, String json) {
        String relative = key.getKey() + ".json";
        for (World world : Bukkit.getWorlds()) {
            File pluralBase = namespaceDir(world, key.getNamespace(), PLURAL_DIR);


            if (!pluralBase.isDirectory()) {
                continue;
            }
            File singularBase = namespaceDir(world, key.getNamespace(), SINGULAR_DIR);
            File target = new File(singularBase, relative);
            try {
                Path parent = target.toPath().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.write(target.toPath(), json.getBytes(StandardCharsets.UTF_8));
            } catch (IOException | RuntimeException exception) {
                logger.log(Level.WARNING, "[Codex] Failed to mirror advancement " + key
                        + " into singular dir of world " + world.getName() + ": " + exception.getMessage());
            }
        }
    }








    private int purgeNamespaceDirs(String namespace) {
        int deleted = 0;
        for (World world : Bukkit.getWorlds()) {
            deleted += deleteJsonTree(namespaceDir(world, namespace, PLURAL_DIR));
            deleted += deleteJsonTree(namespaceDir(world, namespace, SINGULAR_DIR));
        }
        return deleted;
    }


    private File namespaceDir(World world, String namespace, String leaf) {
        return new File(world.getWorldFolder(), "datapacks/bukkit/data/" + namespace + "/" + leaf);
    }








    private int deleteJsonTree(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return 0;
        }
        int deleted = 0;
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    deleted += deleteJsonTree(child);
                    child.delete();
                } else if (child.getName().endsWith(".json") && child.delete()) {
                    deleted++;
                }
            }
        }
        return deleted;
    }

    @Override
    public void reloadData() {
        try {
            Bukkit.reloadData();
        } catch (Throwable throwable) {
            logger.log(Level.WARNING, "[Codex] reloadData failed: " + throwable.getMessage());
        }
    }
}
