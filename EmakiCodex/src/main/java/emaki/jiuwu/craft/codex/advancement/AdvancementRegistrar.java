package emaki.jiuwu.craft.codex.advancement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.codex.advancement.loader.AdvancementPageLoader;
import emaki.jiuwu.craft.codex.advancement.model.AdvancementDefinition;
import emaki.jiuwu.craft.codex.advancement.model.AdvancementPage;










public final class AdvancementRegistrar {

    private final JavaPlugin plugin;
    private final AdvancementPageLoader pageLoader;
    private final AdvancementPlatform platform;
    private final AdvancementJsonBuilder jsonBuilder;

    private final Map<String, AdvancementDefinition> byKey = new ConcurrentHashMap<>();
    private final Map<String, String> pageByKey = new ConcurrentHashMap<>();
    private final List<NamespacedKey> registeredKeys = new ArrayList<>();


    private final List<RegisteredNode> registeredNodes = new CopyOnWriteArrayList<>();

    public AdvancementRegistrar(JavaPlugin plugin,
            AdvancementPageLoader pageLoader,
            AdvancementPlatform platform,
            AdvancementJsonBuilder jsonBuilder) {
        this.plugin = plugin;
        this.pageLoader = pageLoader;
        this.platform = platform;
        this.jsonBuilder = jsonBuilder;
    }







    public synchronized int registerAll() {




        unregisterAll();



        for (AdvancementPage page : pageLoader.all().values()) {
            registerPage(page);
        }




        platform.reloadData();

        return byKey.size();
    }

    private void registerPage(AdvancementPage page) {
        AdvancementDefinition root = page.root();
        if (root == null) {
            plugin.getLogger().warning("[Codex] Advancement page '" + page.pageId()
                    + "' has no valid root '" + page.rootId() + "', skipped.");
            return;
        }

        registerNode(page, root, null);
        for (AdvancementDefinition definition : page.definitions()) {
            if (definition.isRoot() || Objects.equals(definition.id(), page.rootId())) {
                continue;
            }
            String parentKey = keyOf(page.pageId(), definition.parent()).toString();
            registerNode(page, definition, parentKey);
        }
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
        registeredNodes.add(new RegisteredNode(key, page, definition, parentKey));
        return true;
    }








    public synchronized void unregisterAll() {

        platform.removeAll(new NamespacedKey(plugin, "root").getNamespace());
        registeredKeys.clear();
        byKey.clear();
        pageByKey.clear();
        registeredNodes.clear();
    }


    public AdvancementDefinition definitionByKey(NamespacedKey key) {
        return key == null ? null : byKey.get(key.toString());
    }


    public String pageByKey(NamespacedKey key) {
        return key == null ? null : pageByKey.get(key.toString());
    }








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


    public Map<String, AdvancementDefinition> registered() {
        return new LinkedHashMap<>(byKey);
    }


    public int size() {
        return byKey.size();
    }








    public List<RegisteredNode> registeredNodes() {
        return List.copyOf(registeredNodes);
    }

    private NamespacedKey keyOf(String pageId, String localId) {
        String path = (pageId + "/" + localId).toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9_./-]", "_");
        return new NamespacedKey(plugin, path);
    }












    public record RegisteredNode(NamespacedKey key,
            AdvancementPage page,
            AdvancementDefinition definition,
            String parentKey) {
    }
}
