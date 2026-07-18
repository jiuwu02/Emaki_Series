package emaki.jiuwu.craft.item.script;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.inventory.ItemStack;
import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.api.script.modules.ScriptServiceApiSupport;
import emaki.jiuwu.craft.corelib.script.ScriptDeferredOperationQueue;
import emaki.jiuwu.craft.corelib.script.ScriptDeferredOperationQueue.OperationResult;
import emaki.jiuwu.craft.corelib.script.ScriptModuleContext;
import emaki.jiuwu.craft.corelib.script.ScriptSnapshots;
import emaki.jiuwu.craft.corelib.script.js.registration.JavaScriptRegistrationTracker;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.item.EmakiItemPlugin;
import emaki.jiuwu.craft.item.api.EmakiItemApi;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;
import emaki.jiuwu.craft.item.script.js.JavaScriptItemDefinitionRegistry;

public final class ScriptItemModuleApi {

    private final EmakiItemPlugin operationOwner;
    private final ScriptModuleContext moduleContext;
    private final ScriptDeferredOperationQueue deferredOperations;
    private final JavaScriptItemDefinitionRegistry definitionRegistry;
    private final JavaScriptItemFactoryRegistry factoryRegistry;
    private final JavaScriptRegistrationTracker tracker;
    private final boolean available;
    private final Set<String> definitionIds;
    private final Map<String, String> displayNames;
    private final Map<String, Map<String, Object>> itemSnapshots;
    private final Map<String, String> identifiedItems;
    private final List<String> registeredDefinitions;
    private final List<String> registeredFactories;

    public ScriptItemModuleApi(EmakiItemPlugin plugin, ScriptModuleContext moduleContext) {
        this.operationOwner = plugin;
        this.moduleContext = moduleContext;
        this.deferredOperations = ScriptDeferredOperationQueue.currentModuleQueue();
        this.definitionRegistry = plugin == null ? null : plugin.javaScriptDefinitionRegistry();
        this.factoryRegistry = plugin == null ? null : plugin.javaScriptFactoryRegistry();
        this.tracker = plugin == null || plugin.coreLib() == null
                ? null
                : plugin.coreLib().javaScriptRegistrationTracker();

        boolean capturedAvailable = false;
        Set<String> capturedDefinitionIds = Set.of();
        Map<String, String> capturedDisplayNames = Map.of();
        Map<String, Map<String, Object>> capturedItems = Map.of();
        Map<String, String> capturedIdentities = Map.of();
        List<String> capturedRegisteredDefinitions = List.of();
        List<String> capturedRegisteredFactories = List.of();
        try {
            capturedAvailable = EmakiItemApi.available();
            capturedDefinitionIds = Set.copyOf(EmakiItemApi.definitionIds());
            Map<String, String> names = new LinkedHashMap<>();
            Map<String, Map<String, Object>> items = new LinkedHashMap<>();
            for (String rawId : capturedDefinitionIds) {
                String id = Texts.normalizeId(rawId);
                if (Texts.isBlank(id)) {
                    continue;
                }
                names.put(id, Texts.toStringSafe(EmakiItemApi.displayName(id)));
                EmakiItemDefinition itemDefinition = plugin == null || plugin.idResolver() == null
                        ? null
                        : plugin.idResolver().resolveDefinition(id);
                ItemStack itemStack = itemDefinition == null || plugin.itemFactory() == null
                        ? null
                        : plugin.itemFactory().rebuildBase(itemDefinition, itemDefinition.amount());
                Map<String, Object> summary = ScriptServiceApiSupport.itemSummary(itemStack);
                if (itemDefinition != null) {
                    Map<String, Object> snapshot = new LinkedHashMap<>(summary);
                    Map<String, Object> normalizedItem = itemDefinition.normalizedItemSnapshot();
                    snapshot.put("schemaVersion", 2);
                    snapshot.put("item", normalizedItem);
                    snapshot.put("components", normalizedItem.getOrDefault("components", Map.of()));
                    items.put(id, ScriptSnapshots.immutableMap(snapshot));
                } else if (!summary.isEmpty()) {
                    items.put(id, summary);
                }
            }
            capturedDisplayNames = Map.copyOf(names);
            capturedItems = Map.copyOf(items);
            capturedIdentities = captureItemIdentities(
                    ScriptDeferredOperationQueue.currentModuleActionContext());
            capturedRegisteredDefinitions = definitionRegistry == null ? List.of() : definitionRegistry.ids();
            capturedRegisteredFactories = factoryRegistry == null ? List.of() : factoryRegistry.ids();
        } catch (RuntimeException | LinkageError ignored) {
            capturedAvailable = false;
            capturedDefinitionIds = Set.of();
            capturedDisplayNames = Map.of();
            capturedItems = Map.of();
            capturedIdentities = Map.of();
            capturedRegisteredDefinitions = List.of();
            capturedRegisteredFactories = List.of();
        }
        this.available = capturedAvailable;
        this.definitionIds = capturedDefinitionIds;
        this.displayNames = capturedDisplayNames;
        this.itemSnapshots = capturedItems;
        this.identifiedItems = capturedIdentities;
        this.registeredDefinitions = List.copyOf(capturedRegisteredDefinitions);
        this.registeredFactories = List.copyOf(capturedRegisteredFactories);
    }

    @HostAccess.Export
    public boolean available() {
        return available;
    }

    @HostAccess.Export
    public boolean exists(String id) {
        return definitionIds.contains(Texts.normalizeId(id));
    }

    @HostAccess.Export
    public Map<String, Object> create(String id, int amount) {
        Map<String, Object> snapshot = itemSnapshots.get(Texts.normalizeId(id));
        if (snapshot == null) {
            return Map.of();
        }
        if (amount <= 0) {
            return snapshot;
        }
        Map<String, Object> adjusted = new LinkedHashMap<>(snapshot);
        adjusted.put("amount", Math.min(64, amount));
        return ScriptSnapshots.immutableMap(adjusted);
    }

    @HostAccess.Export
    public String identify(String itemKey) {
        return Texts.toStringSafe(identifiedItems.get(itemKey));
    }

    @HostAccess.Export
    public List<String> definitionIds() {
        return definitionIds.stream().sorted().toList();
    }

    @HostAccess.Export
    public String displayName(String id) {
        return Texts.toStringSafe(displayNames.get(Texts.normalizeId(id)));
    }

    @HostAccess.Export
    public boolean registerDefinition(Map<String, ?> definition) {
        if (operationOwner == null || definitionRegistry == null || deferredOperations == null || definition == null) {
            return false;
        }
        Map<String, Object> snapshot = ScriptSnapshots.immutableMap(definition);
        return deferredOperations.enqueueGlobalResult(operationOwner, "item:register-definition", () ->
                definitionRegistry.register(moduleContext, snapshot, tracker)
                        ? OperationResult.ok()
                        : OperationResult.failure("Item definition registration was rejected."));
    }

    @HostAccess.Export
    public boolean registerDefinition(String id, Map<String, ?> definition) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (definition != null) {
            definition.forEach(merged::put);
        }
        merged.put("id", id);
        return registerDefinition(merged);
    }

    @HostAccess.Export
    public void unregisterDefinition(String id) {
        if (operationOwner != null && definitionRegistry != null && deferredOperations != null) {
            String safeId = Texts.normalizeId(id);
            deferredOperations.enqueueGlobal(operationOwner, "item:unregister-definition",
                    () -> definitionRegistry.unregister(safeId));
        }
    }

    @HostAccess.Export
    public List<String> registeredDefinitions() {
        return registeredDefinitions;
    }

    @HostAccess.Export
    public boolean registerFactory(Map<String, ?> definition) {
        if (operationOwner == null || factoryRegistry == null || deferredOperations == null || definition == null) {
            return false;
        }
        Map<String, Object> snapshot = ScriptSnapshots.immutableMap(definition);
        return deferredOperations.enqueueGlobalResult(operationOwner, "item:register-factory", () ->
                factoryRegistry.register(moduleContext, snapshot, tracker)
                        ? OperationResult.ok()
                        : OperationResult.failure("Item factory registration was rejected."));
    }

    @HostAccess.Export
    public boolean registerFactory(String id, Map<String, ?> definition) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (definition != null) {
            definition.forEach(merged::put);
        }
        merged.put("id", id);
        return registerFactory(merged);
    }

    @HostAccess.Export
    public void unregisterFactory(String id) {
        if (operationOwner != null && factoryRegistry != null && deferredOperations != null) {
            String safeId = Texts.normalizeId(id);
            deferredOperations.enqueueGlobal(operationOwner, "item:unregister-factory",
                    () -> factoryRegistry.unregister(safeId));
        }
    }

    @HostAccess.Export
    public List<String> registeredFactories() {
        return registeredFactories;
    }

    @HostAccess.Export
    public Map<String, Object> createFactory(String id, int amount) {
        return Map.of();
    }

    private Map<String, String> captureItemIdentities(ActionContext liveContext) {
        if (liveContext == null || liveContext.attributes().isEmpty()) {
            return Map.of();
        }
        Map<String, String> identities = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : liveContext.attributes().entrySet()) {
            if (!(entry.getValue() instanceof ItemStack itemStack)) {
                continue;
            }
            String id = EmakiItemApi.identify(itemStack);
            if (Texts.isNotBlank(id)) {
                identities.put(entry.getKey(), id);
            }
        }
        return Map.copyOf(identities);
    }
}
