package emaki.jiuwu.craft.codex.advancement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.codex.advancement.loader.AdvancementPageLoader;
import emaki.jiuwu.craft.codex.advancement.model.AdvancementDefinition;
import emaki.jiuwu.craft.codex.advancement.model.AdvancementPage;

/**
 * Registers every configured advancement into the server through the active
 * {@link AdvancementPlatform} and maintains the mapping from a registered
 * advancement key to its definition, used by the completion listener.
 *
 * <p>Keys are built as {@code emakicodex:<pageId>/<localId>}. Roots register before
 * their children so parent references resolve. All registry-affecting calls must run
 * on the main thread.
 */
public final class AdvancementRegistrar {

    private final JavaPlugin plugin;
    private final AdvancementPageLoader pageLoader;
    private final AdvancementPlatform platform;
    private final AdvancementJsonBuilder jsonBuilder;

    private final Map<String, AdvancementDefinition> byKey = new ConcurrentHashMap<>();
    private final Map<String, String> pageByKey = new ConcurrentHashMap<>();
    private final List<NamespacedKey> registeredKeys = new ArrayList<>();

    public AdvancementRegistrar(JavaPlugin plugin,
            AdvancementPageLoader pageLoader,
            AdvancementPlatform platform,
            AdvancementJsonBuilder jsonBuilder) {
        this.plugin = plugin;
        this.pageLoader = pageLoader;
        this.platform = platform;
        this.jsonBuilder = jsonBuilder;
    }

    /**
     * Registers all loaded pages. Existing EmakiCodex advancements are removed first so
     * a reload rebuilds cleanly.
     *
     * @return the number of advancement nodes registered
     */
    public synchronized int registerAll() {
        unregisterAll();
        int count = 0;
        for (AdvancementPage page : pageLoader.all().values()) {
            count += registerPage(page);
        }
        return count;
    }

    private int registerPage(AdvancementPage page) {
        AdvancementDefinition root = page.root();
        if (root == null) {
            plugin.getLogger().warning("[Codex] Advancement page '" + page.pageId()
                    + "' has no valid root '" + page.rootId() + "', skipped.");
            return 0;
        }
        int count = 0;
        // Register root first so children can reference it.
        if (registerNode(page, root, null)) {
            count++;
        }
        for (AdvancementDefinition definition : page.definitions()) {
            if (definition.isRoot() || Objects.equals(definition.id(), page.rootId())) {
                continue;
            }
            String parentKey = keyOf(page.pageId(), definition.parent()).toString();
            if (registerNode(page, definition, parentKey)) {
                count++;
            }
        }
        return count;
    }

    private boolean registerNode(AdvancementPage page, AdvancementDefinition definition, String parentKey) {
        NamespacedKey key = keyOf(page.pageId(), definition.id());
        String json = jsonBuilder.build(page, definition, parentKey);
        if (!platform.register(key, json)) {
            return false;
        }
        registeredKeys.add(key);
        byKey.put(key.toString(), definition);
        pageByKey.put(key.toString(), page.pageId());
        return true;
    }

    /** Removes every advancement this registrar created and clears its maps. */
    public synchronized void unregisterAll() {
        boolean removedAny = false;
        for (NamespacedKey key : registeredKeys) {
            if (platform.remove(key)) {
                removedAny = true;
            }
        }
        if (removedAny) {
            platform.reloadData();
        }
        registeredKeys.clear();
        byKey.clear();
        pageByKey.clear();
    }

    /** {@return the definition mapped to a registered advancement key, or {@code null}} */
    public AdvancementDefinition definitionByKey(NamespacedKey key) {
        return key == null ? null : byKey.get(key.toString());
    }

    /** {@return the page id owning a registered advancement key, or {@code null}} */
    public String pageByKey(NamespacedKey key) {
        return key == null ? null : pageByKey.get(key.toString());
    }

    /**
     * Resolves the full advancement key for a caller-supplied advancement id. Accepts
     * either a full {@code emakicodex:page/node} key or a bare {@code page/node} path.
     *
     * @param advancementId the id from a command/action
     * @return the resolved key, or {@code null} when it maps to no registered node
     */
    public NamespacedKey resolveKey(String advancementId) {
        if (advancementId == null || advancementId.isBlank()) {
            return null;
        }
        String trimmed = advancementId.trim();
        NamespacedKey candidate;
        if (trimmed.contains(":")) {
            candidate = NamespacedKey.fromString(trimmed);
        } else {
            candidate = new NamespacedKey(plugin, trimmed);
        }
        if (candidate == null) {
            return null;
        }
        return byKey.containsKey(candidate.toString()) ? candidate : null;
    }

    /** {@return an immutable snapshot of registered advancement key strings} */
    public Map<String, AdvancementDefinition> registered() {
        return new LinkedHashMap<>(byKey);
    }

    /** {@return the number of currently registered advancement nodes} */
    public int size() {
        return byKey.size();
    }

    private NamespacedKey keyOf(String pageId, String localId) {
        String path = (pageId + "/" + localId).toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9_./-]", "_");
        return new NamespacedKey(plugin, path);
    }
}
