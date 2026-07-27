package emaki.jiuwu.craft.item.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.item.ItemBuildResult;
import emaki.jiuwu.craft.corelib.api.item.ItemComponentPatch;
import emaki.jiuwu.craft.corelib.assembly.ItemOperationEntry;
import emaki.jiuwu.craft.corelib.assembly.ItemOperationLedger;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.item.api.event.EmakiItemCreateEvent;
import emaki.jiuwu.craft.item.loader.EmakiItemLoader;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;
import emaki.jiuwu.craft.item.script.JavaScriptItemFactoryRegistry;

public final class EmakiItemFactory {

    private static final String DISPLAY_OPERATION_NAMESPACE = "emakiitem:item_display";
    private static final List<String> RANDOM_TYPES = List.of(
            "range", "uniform", "gaussian", "normal", "skew_normal", "triangle"
    );

    private final EmakiItemLoader loader;
    private final EmakiItemIdResolver idResolver;
    private final EmakiItemPdcWriter pdcWriter;
    private final JavaScriptItemFactoryRegistry javaScriptFactories;
    private final ThreadOwnership threadOwnership;
    private final ItemOperationLedger itemOperationLedger;
    private final ConcurrentHashMap<String, ItemStack> prototypeCache = new ConcurrentHashMap<>();

    public EmakiItemFactory(EmakiItemLoader loader, EmakiItemIdResolver idResolver, EmakiItemPdcWriter pdcWriter) {
        this(loader, idResolver, pdcWriter, null, null);
    }

    public EmakiItemFactory(EmakiItemLoader loader,
            EmakiItemIdResolver idResolver,
            EmakiItemPdcWriter pdcWriter,
            JavaScriptItemFactoryRegistry javaScriptFactories) {
        this(loader, idResolver, pdcWriter, javaScriptFactories, null);
    }

    public EmakiItemFactory(EmakiItemLoader loader,
            EmakiItemIdResolver idResolver,
            EmakiItemPdcWriter pdcWriter,
            JavaScriptItemFactoryRegistry javaScriptFactories,
            ThreadOwnership threadOwnership) {
        this(loader, idResolver, pdcWriter, javaScriptFactories, threadOwnership, null);
    }

    public EmakiItemFactory(EmakiItemLoader loader,
            EmakiItemIdResolver idResolver,
            EmakiItemPdcWriter pdcWriter,
            JavaScriptItemFactoryRegistry javaScriptFactories,
            ThreadOwnership threadOwnership,
            emaki.jiuwu.craft.corelib.debug.DebugLogger debugLogger) {
        this.loader = loader;
        this.idResolver = idResolver;
        this.pdcWriter = pdcWriter;
        this.javaScriptFactories = javaScriptFactories;
        this.threadOwnership = threadOwnership;
        this.itemOperationLedger = new ItemOperationLedger(debugLogger);
    }

    public ItemStack create(String id, int amount) {
        ItemStack scripted = javaScriptFactories == null ? null : javaScriptFactories.create(id, amount);
        if (scripted != null) {
            return fireCreateEvent(id, scripted.getAmount(), scripted);
        }
        EmakiItemDefinition definition = idResolver == null ? loader.get(id) : idResolver.resolveDefinition(id);
        if (definition == null) {
            return null;
        }
        ItemStack itemStack;
        if (definition.hasRandomElements()) {
            itemStack = build(definition);
        } else {
            ItemStack prototype = prototypeCache.get(definition.id());
            if (prototype == null) {
                prototype = build(definition);
                if (prototype != null) {
                    prototypeCache.put(definition.id(), prototype.clone());
                }
            }
            itemStack = prototype == null ? null : prototype.clone();
        }
        if (itemStack == null) {
            return null;
        }

        int resolved = amount > 0 ? amount : definition.amount();
        itemStack.setAmount(Math.max(1, Math.min(resolved, itemStack.getMaxStackSize())));
        return fireCreateEvent(id, itemStack.getAmount(), itemStack);
    }

    private ItemStack fireCreateEvent(String id, int amount, ItemStack itemStack) {
        if (threadOwnership == null || !threadOwnership.isGlobalOwned()) {
            return itemStack;
        }
        EmakiItemCreateEvent event = new EmakiItemCreateEvent(id, amount, null, itemStack);
        Bukkit.getPluginManager().callEvent(event);
        return event.getResult() != null ? event.getResult() : itemStack;
    }

    public void clearCache() {
        prototypeCache.clear();
    }

    public ItemStack rebuildBase(EmakiItemDefinition definition, int amount) {
        if (definition == null) {
            return null;
        }
        ItemStack itemStack = build(definition);
        if (itemStack == null) {
            return null;
        }
        itemStack.setAmount(Math.max(1, Math.min(amount, itemStack.getMaxStackSize())));
        return itemStack;
    }

    private ItemStack build(EmakiItemDefinition definition) {
        PreparedBuild prepared = prepareBuild(definition);
        return prepared == null ? null : finishBuild(prepared.itemStack(), definition, prepared.variables());
    }

    PreparedBuild prepareBuild(EmakiItemDefinition definition) {
        if (definition == null) {
            return null;
        }
        Map<String, Object> variables = resolveBuildVariables(definition.variables());
        ConfiguredItemDefinition resolvedDefinition = resolveItemDefinition(definition.itemDefinition(), variables).withAmount(1);
        ItemBuildResult result = EmakiCoreLibApi.createConfiguredItem(resolvedDefinition);
        if (!result.success() || result.itemStack() == null) {
            return null;
        }
        return new PreparedBuild(result.itemStack(), resolvedDefinition, variables);
    }

    ItemStack finishBuild(ItemStack itemStack,
            EmakiItemDefinition definition,
            Map<String, Object> variables) {
        if (itemStack == null || definition == null) {
            return null;
        }
        pdcWriter.write(itemStack, definition, variables == null ? Map.of() : variables);
        applyDisplayActions(itemStack, definition, variables == null ? Map.of() : variables);
        return itemStack;
    }

    FinishedBuild finishBuild(ItemStack itemStack,
            EmakiItemDefinition definition,
            Map<String, Object> variables,
            ItemOperationLedger.ReadResult readResult) {
        ItemOperationLedger.ReadResult currentReadResult = readResult == null
                ? ItemOperationLedger.ReadResult.corrupt(List.of())
                : readResult;
        if (itemStack == null || definition == null || currentReadResult.corrupt()) {
            return new FinishedBuild(false, itemStack, currentReadResult);
        }
        Map<String, Object> safeVariables = variables == null ? Map.of() : variables;
        pdcWriter.write(itemStack, definition, safeVariables);
        ItemOperationLedger.UpdateResult reverted = revertDisplayOperations(itemStack, currentReadResult);
        if (!reverted.success()) {
            return new FinishedBuild(false, itemStack, reverted.readResult());
        }
        currentReadResult = reverted.readResult();
        if (!hasActions(definition.nameActions()) && !hasActions(definition.loreActions())) {
            return new FinishedBuild(true, itemStack, currentReadResult);
        }
        ItemOperationLedger.UpdateResult applied = itemOperationLedger.apply(
                itemStack,
                currentReadResult,
                "emakiitem:item_display:" + definition.id(),
                DISPLAY_OPERATION_NAMESPACE,
                definition.nameActions(),
                definition.loreActions(),
                safeVariables
        );
        return new FinishedBuild(applied.success(), itemStack, applied.readResult());
    }

    private ItemOperationLedger.UpdateResult revertDisplayOperations(
            ItemStack itemStack,
            ItemOperationLedger.ReadResult initialReadResult) {
        ItemOperationLedger.ReadResult currentReadResult = initialReadResult == null
                ? ItemOperationLedger.ReadResult.corrupt(List.of())
                : initialReadResult;
        if (currentReadResult.corrupt()) {
            return ItemOperationLedger.UpdateResult.failure(currentReadResult);
        }
        LinkedHashSet<String> operationIds = new LinkedHashSet<>();
        List<ItemOperationEntry> entries = currentReadResult.entries();
        for (int index = entries.size() - 1; index >= 0; index--) {
            ItemOperationEntry entry = entries.get(index);
            if (entry != null && DISPLAY_OPERATION_NAMESPACE.equals(entry.sourceNamespace())) {
                operationIds.add(entry.operationId());
            }
        }
        for (String operationId : operationIds) {
            ItemOperationLedger.UpdateResult reverted = itemOperationLedger.revert(
                    itemStack, currentReadResult, operationId);
            if (!reverted.success()) {
                return ItemOperationLedger.UpdateResult.failure(currentReadResult);
            }
            currentReadResult = reverted.readResult();
        }
        return ItemOperationLedger.UpdateResult.success(currentReadResult);
    }

    private void applyDisplayActions(ItemStack itemStack,
            EmakiItemDefinition definition,
            Map<String, Object> variables) {
        if (itemStack == null || definition == null
                || (!hasActions(definition.nameActions()) && !hasActions(definition.loreActions()))) {
            return;
        }
        itemOperationLedger.apply(
                itemStack,
                "emakiitem:item_display:" + definition.id(),
                DISPLAY_OPERATION_NAMESPACE,
                definition.nameActions(),
                definition.loreActions(),
                variables
        );
    }

    private boolean hasActions(Object raw) {
        if (raw == null) {
            return false;
        }
        if (raw instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        if (raw instanceof Iterable<?> iterable) {
            return iterable.iterator().hasNext();
        }
        return Texts.isNotBlank(raw);
    }

    private Map<String, Object> resolveBuildVariables(Map<String, Object> rawVariables) {
        if (rawVariables == null || rawVariables.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> resolved = ExpressionEngine.resolveMixedVariables(rawVariables, Map.of());
        return resolved.isEmpty() ? Map.of() : Map.copyOf(resolved);
    }

    private ConfiguredItemDefinition resolveItemDefinition(ConfiguredItemDefinition definition,
            Map<String, Object> variables) {
        if (definition == null) {
            return new ConfiguredItemDefinition(null, 1, Map.of());
        }
        Map<String, ItemComponentPatch> patches = new LinkedHashMap<>();
        definition.components().forEach((componentId, patch) -> patches.put(componentId,
                patch.operation() == ItemComponentPatch.Operation.SET
                        ? ItemComponentPatch.set(resolveComponentValue(componentId, patch.value(), variables))
                        : patch));
        String source = definition.source() == null
                ? null
                : Texts.formatTemplate(definition.source(), variables);
        return new ConfiguredItemDefinition(source, definition.amount(), patches);
    }

    private Object resolveComponentValue(String componentId, Object raw, Map<String, Object> variables) {
        if ("minecraft:custom_name".equals(componentId) || "minecraft:item_name".equals(componentId)) {
            return ExpressionEngine.evaluateStringConfig(raw, variables);
        }
        if ("minecraft:lore".equals(componentId)) {
            return ExpressionEngine.evaluateStringLinesConfig(raw, variables);
        }
        return resolvePlainValue(raw, variables);
    }

    private Object resolvePlainValue(Object raw, Map<String, Object> variables) {
        Object value = ConfigNodes.toPlainData(raw);
        if (value instanceof String text) {
            return Texts.formatTemplate(text, variables);
        }
        if (value instanceof Map<?, ?> map) {
            if (isRandomConfig(map)) {
                return ExpressionEngine.evaluateRandomConfig(map, variables);
            }
            Map<String, Object> resolved = new LinkedHashMap<>();
            map.forEach((key, nested) -> {
                if (key != null) {
                    resolved.put(String.valueOf(key), resolvePlainValue(nested, variables));
                }
            });
            return resolved;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> resolved = new ArrayList<>();
            iterable.forEach(nested -> resolved.add(resolvePlainValue(nested, variables)));
            return resolved;
        }
        return value;
    }

    private boolean isRandomConfig(Map<?, ?> map) {
        String type = Texts.normalizeId(Texts.toStringSafe(map.get("type"))).replace('-', '_');
        return RANDOM_TYPES.contains(type);
    }

    record FinishedBuild(boolean success,
            ItemStack itemStack,
            ItemOperationLedger.ReadResult readResult) {

        FinishedBuild {
            readResult = readResult == null
                    ? ItemOperationLedger.ReadResult.corrupt(List.of())
                    : readResult;
        }

        List<ItemOperationEntry> entries() {
            return readResult.entries();
        }
    }

    record PreparedBuild(ItemStack itemStack,
            ConfiguredItemDefinition itemDefinition,
            Map<String, Object> variables) {
    }
}
