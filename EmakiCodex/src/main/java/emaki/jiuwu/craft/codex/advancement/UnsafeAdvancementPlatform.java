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

/**
 * Advancement platform backed by {@code Bukkit.getUnsafe().loadAdvancement(...)}.
 *
 * <p>This is the only public Spigot API for dynamic advancement registration. Notes
 * from the API contract that this implementation handles:
 * <ul>
 *   <li>{@code loadAdvancement} throws {@link IllegalArgumentException} when the key
 *       already exists, so {@link #register} removes an existing entry first.</li>
 *   <li>{@code loadAdvancement} persists to the world's bukkit datapack and triggers a
 *       resource reload, so it takes effect immediately.</li>
 *   <li>{@code removeAdvancement} only deletes the persisted file; a
 *       {@link Bukkit#reloadData()} is needed to drop it from the running instance.</li>
 * </ul>
 *
 * <p><b>1.21 directory-name fix (1.0.10):</b> Minecraft 1.21 (pack format 48) renamed
 * datapack content directories to their singular form ({@code advancements/ ->
 * advancement/}, alongside {@code recipes/ -> recipe/} etc.). On this server's build,
 * {@code loadAdvancement} still writes the JSON into the legacy plural
 * {@code data/<ns>/advancements/} directory, but the vanilla resource loader triggered
 * by {@link Bukkit#reloadData()} only scans the singular {@code data/<ns>/advancement/}
 * directory. The net effect on 1.21.x: {@code loadAdvancement} briefly inserts the node
 * into the running registry, but the final {@code reloadData()} rebuilds the registry
 * from disk, scans only the singular dir, finds nothing there, and drops every node
 * (registry count returns to 0). {@link org.bukkit.Bukkit#getAdvancement} then returns
 * {@code null}, so grant/revoke silently fail even though PacketEvents still paints the
 * tree client-side. To make the vanilla loader actually pick the nodes up, this platform
 * mirrors each loaded JSON into the singular {@code advancement/} directory before the
 * batch reload, and purges BOTH directories on removal so no stale node survives. See
 * SPIGOT-7734.
 */
@SuppressWarnings("deprecation")
public final class UnsafeAdvancementPlatform implements AdvancementPlatform {

    /** Legacy plural directory that {@code loadAdvancement} writes to (pre-1.21 layout). */
    private static final String PLURAL_DIR = "advancements";
    /** Singular directory the 1.21+ vanilla loader actually scans on reload. */
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
            // Defensive only: the registrar clears the whole namespace and reloads once
            // (dropping every old node from the running instance) BEFORE any register call,
            // so on the normal path getAdvancement is null here and this branch is skipped.
            // We must NOT reloadData in the middle of a batch: doing so would let the vanilla
            // ServerAdvancementManager re-scan the datapack while some child's parent file was
            // already deleted, logging "Couldn't load advancements". The single post-batch
            // reload in the registrar makes the running instance consistent instead.
            boolean existed = Bukkit.getAdvancement(key) != null;
            if (existed) {
                Bukkit.getUnsafe().removeAdvancement(key);
            }
            boolean loaded = Bukkit.getUnsafe().loadAdvancement(key, json) != null;
            if (loaded) {
                // loadAdvancement wrote the JSON into the legacy plural advancements/ dir.
                // Mirror it into the singular advancement/ dir the 1.21+ vanilla loader
                // scans, so the registrar's post-batch reloadData() actually loads it into
                // the running registry (otherwise the node is dropped and getAdvancement
                // returns null -> grant fails). Best-effort: a mirror failure does not fail
                // the registration (the node is already loaded and PacketEvents still works).
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
        // removeAdvancement only clears the running registry + the plural file it knows
        // about. Also physically purge BOTH the plural and singular namespace directories
        // so no leftover file (from this session's mirror or a previous session) survives a
        // reload. This is what lets the post-purge reload rebuild the registry to empty, so
        // the phase-2 loadAdvancement calls never hit a duplicate-key IllegalArgumentException.
        int filesDeleted = purgeNamespaceDirs(namespace);
        // One reload AFTER the whole namespace is deleted. Because every node (parent and
        // child alike) leaves the datapack together, the vanilla loader never sees a
        // "parent deleted, child still present" tree, so no orphan is logged. This reload
        // also drops the old nodes from the running-instance registry, so the subsequent
        // parent-first re-registration never hits a duplicate-key IllegalArgumentException.
        if (removed > 0 || filesDeleted > 0) {
            reloadData();
        }
        return removed;
    }

    /**
     * Enumerates every currently registered advancement key under the given namespace.
     * Runs on the main thread and only reads the server advancement registry.
     *
     * @param namespace the advancement key namespace to collect
     * @return the matching keys, or an empty list when enumeration fails
     */
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

    /**
     * Mirrors an advancement JSON into the singular {@code advancement/} directory of
     * every world datapack that hosts this namespace, matching the relative path
     * {@code loadAdvancement} used in the plural {@code advancements/} directory.
     *
     * <p>Best-effort: writes go to whichever world folders already contain the plural
     * namespace directory (that is where {@code loadAdvancement} just wrote), so the
     * mirror lands in the same datapack the loader will reload from.
     *
     * @param key  the advancement key (its path becomes the relative file path)
     * @param json the advancement JSON to write
     */
    private void mirrorToSingularDir(NamespacedKey key, String json) {
        String relative = key.getKey() + ".json";
        for (World world : Bukkit.getWorlds()) {
            File pluralBase = namespaceDir(world, key.getNamespace(), PLURAL_DIR);
            // Only mirror where loadAdvancement actually wrote (plural dir exists). This
            // avoids creating stray singular dirs in worlds the API never touched.
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

    /**
     * Physically deletes every {@code .json} under both the plural and singular namespace
     * directories in every world datapack.
     *
     * @param namespace the namespace to purge
     * @return the number of files deleted
     */
    private int purgeNamespaceDirs(String namespace) {
        int deleted = 0;
        for (World world : Bukkit.getWorlds()) {
            deleted += deleteJsonTree(namespaceDir(world, namespace, PLURAL_DIR));
            deleted += deleteJsonTree(namespaceDir(world, namespace, SINGULAR_DIR));
        }
        return deleted;
    }

    /** {@return the {@code datapacks/bukkit/data/<namespace>/<leaf>} dir for a world} */
    private File namespaceDir(World world, String namespace, String leaf) {
        return new File(world.getWorldFolder(), "datapacks/bukkit/data/" + namespace + "/" + leaf);
    }

    /**
     * Recursively deletes {@code .json} files under a directory, then removes now-empty
     * directories. Only touches {@code .json} files, so it never deletes unrelated content.
     *
     * @param dir the directory to clean
     * @return the number of {@code .json} files deleted
     */
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
                    child.delete(); // remove dir if it became empty; ignored otherwise
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
