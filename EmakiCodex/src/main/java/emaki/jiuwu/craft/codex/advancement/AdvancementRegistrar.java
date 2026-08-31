package emaki.jiuwu.craft.codex.advancement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.codex.advancement.loader.AdvancementPageLoader;
import emaki.jiuwu.craft.codex.advancement.model.AdvancementDefinition;
import emaki.jiuwu.craft.codex.advancement.model.AdvancementFrame;
import emaki.jiuwu.craft.codex.advancement.model.AdvancementPage;
import emaki.jiuwu.craft.codex.api.AdvancementRegistration;
import emaki.jiuwu.craft.codex.api.AdvancementSpec;
import emaki.jiuwu.craft.codex.api.model.AdvancementFrameType;

public final class AdvancementRegistrar implements Listener, AutoCloseable {

    private final JavaPlugin plugin;
    private final AdvancementPageLoader pageLoader;
    private final AdvancementPlatform platform;
    private final AdvancementJsonBuilder jsonBuilder;

    private final Map<String, AdvancementDefinition> byKey = new ConcurrentHashMap<>();
    private final Map<String, String> pageByKey = new ConcurrentHashMap<>();
    private final List<NamespacedKey> configuredKeys = new ArrayList<>();
    private final List<RegisteredNode> registeredNodes = new CopyOnWriteArrayList<>();
    private final Map<ExternalKey, ExternalRegistration> externalRegistrations = new LinkedHashMap<>();
    private long externalGeneration;

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
        unregisterConfigured(true);
        for (AdvancementPage page : pageLoader.all().values()) {
            registerPage(page);
        }
        return verifyConfigured();
    }

    private int verifyConfigured() {
        List<NamespacedKey> missing = new ArrayList<>();
        for (NamespacedKey key : configuredKeys) {
            if (!platform.exists(key)) {
                missing.add(key);
            }
        }
        if (missing.isEmpty()) {
            return configuredKeys.size();
        }
        plugin.getLogger().warning("[Codex] " + missing.size() + " of " + configuredKeys.size()
                + " configured advancement(s) were accepted by platform '" + platform.id() + "' but are absent"
                + " from the server and cannot be granted; something rebuilt the advancement tree from disk"
                + " after registration. Affected: " + describeMissing(missing));
        return configuredKeys.size() - missing.size();
    }

    private String describeMissing(List<NamespacedKey> missing) {
        int shown = Math.min(missing.size(), 5);
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < shown; index++) {
            text.append(index == 0 ? "" : ", ").append(missing.get(index));
        }
        if (missing.size() > shown) {
            text.append(" ... (+").append(missing.size() - shown).append(')');
        }
        return text.toString();
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
            registerNode(page, definition, keyOf(page.pageId(), definition.parent()).toString());
        }
    }

    private boolean registerNode(AdvancementPage page, AdvancementDefinition definition, String parentKey) {
        NamespacedKey key = keyOf(page.pageId(), definition.id());
        if (!platform.register(key, jsonBuilder.build(page, definition, parentKey))) {
            return false;
        }
        configuredKeys.add(key);
        addNode(key, page, definition, parentKey);
        return true;
    }

    public synchronized AdvancementRegistration register(Plugin owner, AdvancementSpec spec) {
        if (owner == null || !owner.isEnabled() || spec == null || spec.id().isBlank()) {
            return AdvancementRegistration.noop();
        }
        String path = sanitizePath(spec.id());
        if (path.isBlank()) {
            return AdvancementRegistration.noop();
        }
        NamespacedKey key = new NamespacedKey(owner, path);
        ExternalKey externalKey = new ExternalKey(owner, path);
        ExternalRegistration previous = externalRegistrations.remove(externalKey);
        if (previous != null) {

            removeExternalNode(previous, true);
        } else if (byKey.containsKey(key.toString())) {
            return AdvancementRegistration.noop();
        }
        String parentKey = resolveParent(owner, spec.parentKey());
        if (!platform.register(key, jsonBuilder.build(spec, parentKey))) {
            return AdvancementRegistration.noop();
        }
        AdvancementDefinition definition = toDefinition(spec);
        long generation = ++externalGeneration;
        ExternalRegistration registration = new ExternalRegistration(key, definition, parentKey, generation);
        externalRegistrations.put(externalKey, registration);
        addNode(key, null, definition, parentKey);

        return new RegistrationHandle(this, externalKey, key, generation);
    }

    public synchronized void unregisterAll() {
        unregisterConfigured(false);
        for (ExternalRegistration registration : List.copyOf(externalRegistrations.values())) {
            removeExternalNode(registration, false);
        }
        externalRegistrations.clear();
        platform.reloadData();
    }

    public synchronized void unregisterConfigured() {
        unregisterConfigured(true);
    }

    private void unregisterConfigured(boolean reloadIfRemoved) {
        boolean removedAny = false;
        for (NamespacedKey key : List.copyOf(configuredKeys)) {
            platform.remove(key);
            removeNode(key);
            removedAny = true;
        }
        configuredKeys.clear();
        if (reloadIfRemoved && removedAny) {
            platform.reloadData();
        }
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
        NamespacedKey candidate = trimmed.contains(":")
                ? NamespacedKey.fromString(trimmed)
                : new NamespacedKey(plugin, sanitizePath(trimmed));
        return candidate != null && byKey.containsKey(candidate.toString()) ? candidate : null;
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        unregisterOwner(event.getPlugin());
    }

    public synchronized void unregisterOwner(Plugin owner) {
        if (owner == null) {
            return;
        }
        boolean changed = false;
        for (Map.Entry<ExternalKey, ExternalRegistration> entry
                : List.copyOf(externalRegistrations.entrySet())) {
            if (entry.getKey().owner() == owner) {
                externalRegistrations.remove(entry.getKey());
                removeExternalNode(entry.getValue(), false);
                changed = true;
            }
        }
        if (changed) {
            platform.reloadData();
        }
    }

    public synchronized void shutdown(boolean removeConfigured) {
        HandlerList.unregisterAll(this);
        if (removeConfigured) {
            unregisterConfigured(false);
        }
        for (ExternalRegistration registration : List.copyOf(externalRegistrations.values())) {
            removeExternalNode(registration, false);
        }
        externalRegistrations.clear();
        platform.reloadData();
    }

    @Override
    public synchronized void close() {
        shutdown(true);
    }

    private synchronized void unregister(ExternalKey externalKey, NamespacedKey key, long generation) {
        ExternalRegistration registration = externalRegistrations.get(externalKey);
        if (registration == null || registration.generation() != generation || !registration.key().equals(key)) {
            return;
        }
        externalRegistrations.remove(externalKey);
        removeExternalNode(registration, true);
    }

    private void addNode(NamespacedKey key, AdvancementPage page,
            AdvancementDefinition definition, String parentKey) {
        byKey.put(key.toString(), definition);
        if (page != null) {
            pageByKey.put(key.toString(), page.pageId());
        }
        registeredNodes.add(new RegisteredNode(key, page, definition, parentKey));
    }

    private void removeExternalNode(ExternalRegistration registration, boolean reload) {
        platform.remove(registration.key());
        removeNode(registration.key());
        if (reload) {
            platform.reloadData();
        }
    }

    private void removeNode(NamespacedKey key) {
        byKey.remove(key.toString());
        pageByKey.remove(key.toString());
        registeredNodes.removeIf(node -> node.key().equals(key));
    }

    private NamespacedKey keyOf(String pageId, String localId) {
        return new NamespacedKey(plugin, sanitizePath(pageId + "/" + localId));
    }

    private static String resolveParent(Plugin owner, String parentKey) {
        if (parentKey == null || parentKey.isBlank()) {
            return null;
        }
        NamespacedKey parsed = parentKey.contains(":")
                ? NamespacedKey.fromString(parentKey)
                : new NamespacedKey(owner, sanitizePath(parentKey));
        return parsed == null ? null : parsed.toString();
    }

    private static String sanitizePath(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_./-]", "_");
    }

    private static AdvancementDefinition toDefinition(AdvancementSpec spec) {
        return new AdvancementDefinition(
                sanitizePath(spec.id()), spec.icon(), spec.title(), spec.description(),
                toFrame(spec.frame()), 0D, 0D, spec.parentKey(), spec.hidden(),
                spec.showToast(), spec.announce(), List.of(), List.of());
    }

    private static AdvancementFrame toFrame(AdvancementFrameType frame) {
        return switch (frame) {
            case GOAL -> AdvancementFrame.GOAL;
            case CHALLENGE -> AdvancementFrame.CHALLENGE;
            default -> AdvancementFrame.TASK;
        };
    }

    public record RegisteredNode(NamespacedKey key,
            AdvancementPage page,
            AdvancementDefinition definition,
            String parentKey) {
    }

    private record ExternalKey(Plugin owner, String id) {
    }

    private record ExternalRegistration(NamespacedKey key,
            AdvancementDefinition definition,
            String parentKey,
            long generation) {
    }

    private static final class RegistrationHandle implements AdvancementRegistration {
        private final AdvancementRegistrar registrar;
        private final ExternalKey externalKey;
        private final NamespacedKey key;
        private final long generation;
        private final AtomicBoolean closed = new AtomicBoolean();

        private RegistrationHandle(AdvancementRegistrar registrar,
                ExternalKey externalKey,
                NamespacedKey key,
                long generation) {
            this.registrar = registrar;
            this.externalKey = externalKey;
            this.key = key;
            this.generation = generation;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                registrar.unregister(externalKey, key, generation);
            }
        }
    }
}
