package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRenderer;
import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import net.kyori.adventure.text.Component;

final class ItemOperationExecutor {

    private final ItemOperationLedger ledger;
    private final OperationTemplateRenderer templateRenderer;
    private final LoreOperationRegistry loreOperations;
    private final NameOperationRegistry nameOperations;

    public ItemOperationExecutor(ItemOperationLedger ledger) {
        this.ledger = ledger;
        this.templateRenderer = new OperationTemplateRenderer();
        this.loreOperations = new LoreOperationRegistry(templateRenderer);
        this.nameOperations = new NameOperationRegistry(templateRenderer);
    }

    public ExecutionResult execute(ItemStack itemStack,
            String operationId,
            String sourceNamespace,
            Object nameActions,
            Object loreActions,
            Map<String, ?> variables) {
        return execute(null, itemStack, operationId, sourceNamespace, nameActions, loreActions, variables);
    }

    public ExecutionResult execute(ActionContext context,
            ItemStack itemStack,
            String operationId,
            String sourceNamespace,
            Object nameActions,
            Object loreActions,
            Map<String, ?> variables) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return ExecutionResult.EMPTY;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return ExecutionResult.EMPTY;
        }
        Map<String, Object> safeVariables = PlaceholderRenderer.mergeContextVariables(context, variables);
        PlaceholderRenderer.debugVariables(safeVariables, ledger.debugLogger(), context == null ? null : context.player(), "item_operation." + operationId);
        List<Map<String, Object>> normalizedNameActions = templateRenderer.normalizeOperations(nameActions);
        List<Map<String, Object>> normalizedLoreActions = templateRenderer.normalizeOperations(loreActions);
        debug(context, "apply start | operationId=" + operationId
                + " | namespace=" + sourceNamespace
                + " | item=" + itemStack.getType().name()
                + " | nameActions=" + normalizedNameActions.size()
                + " | loreActions=" + normalizedLoreActions.size()
                + " | variables=" + summarizeMap(safeVariables));

        List<ItemOperationEntry.NameOperationRecord> nameRecords = collectNameRecords(context, itemMeta, normalizedNameActions, safeVariables);
        LocalNameState nameState = resolveNameState(context, normalizedNameActions, safeVariables);
        List<ItemOperationEntry.LoreOperationRecord> loreRecords = executeLoreActions(context, itemMeta, normalizedLoreActions, safeVariables);

        if (nameRecords.isEmpty() && loreRecords.isEmpty()) {
            debug(context, "apply empty | operationId=" + operationId + " | reason=no_effective_records");
            return ExecutionResult.EMPTY;
        }

        // Lore lives on itemMeta and must be committed first; the name is then
        // injected as a component so a translatable base name is preserved and
        // not overwritten by a later setItemMeta.
        itemStack.setItemMeta(itemMeta);
        if (!nameRecords.isEmpty()) {
            applyNameState(context, itemStack, nameState);
        }

        ItemOperationEntry entry = new ItemOperationEntry(
                operationId,
                sourceNamespace,
                System.currentTimeMillis(),
                nameRecords,
                loreRecords
        );
        ledger.append(itemStack, entry);
        debug(context, "apply success | operationId=" + operationId
                + " | nameRecords=" + nameRecords.size()
                + " | loreRecords=" + loreRecords.size());
        return new ExecutionResult(true, entry);
    }

    private List<ItemOperationEntry.NameOperationRecord> collectNameRecords(ActionContext context,
            ItemMeta itemMeta,
            List<Map<String, Object>> operations,
            Map<String, Object> variables) {
        if (operations == null || operations.isEmpty()) {
            return List.of();
        }

        List<ItemOperationEntry.NameOperationRecord> records = new ArrayList<>();
        String currentName = ItemTextBridge.hasCustomName(itemMeta)
                ? MiniMessages.serialize(ItemTextBridge.customName(itemMeta))
                : "";

        for (Map<String, Object> operation : operations) {
            String action = Texts.lower(operation.get("action"));
            Object rawValue = templateRenderer.resolveOperationValue(operation);
            String renderedValue = rawValue == null ? "" : templateRenderer.renderTemplate(rawValue, variables, context, ledger.debugLogger(), "item_operation.name.record." + action);
            boolean recognized = nameOperations.getProcessor(action) != null;
            debug(context, "name action | action=" + action
                    + " | recognized=" + recognized
                    + " | value=" + summarize(renderedValue)
                    + " | raw=" + summarizeMap(operation));
            if (Texts.isBlank(action) || Texts.isBlank(renderedValue)) {
                continue;
            }
            records.add(new ItemOperationEntry.NameOperationRecord(action, renderedValue, currentName));
        }
        return records;
    }

    private LocalNameState resolveNameState(ActionContext context,
            List<Map<String, Object>> operations,
            Map<String, Object> variables) {
        LocalNameState nameState = new LocalNameState();
        if (operations != null && !operations.isEmpty()) {
            nameOperations.apply(nameState, operations, variables, context, ledger.debugLogger());
        }
        return nameState;
    }

    private void applyNameState(ActionContext context, ItemStack itemStack, LocalNameState nameState) {
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return;
        }
        Component baseName = LedgerNameComposer.resolveBaseName(itemStack, itemMeta);
        Component result = LedgerNameComposer.composeFromState(nameState, baseName);
        debug(context, "name result | base=" + summarize(MiniMessages.serialize(baseName))
                + " | result=" + summarize(MiniMessages.serialize(result)));
        LedgerNameComposer.writeName(itemStack, itemMeta, result);
    }

    private List<ItemOperationEntry.LoreOperationRecord> executeLoreActions(ActionContext context,
            ItemMeta itemMeta,
            List<Map<String, Object>> operations,
            Map<String, Object> variables) {
        if (operations == null || operations.isEmpty()) {
            return List.of();
        }

        List<String> currentLore = new ArrayList<>();
        List<String> existingLore = ItemTextBridge.loreLines(itemMeta);
        if (existingLore != null) {
            currentLore.addAll(existingLore);
        }

        List<ItemOperationEntry.LoreOperationRecord> records = new ArrayList<>();

        for (Map<String, Object> operation : operations) {
            String action = Texts.lower(operation.get("action"));
            if (Texts.isBlank(action)) {
                debug(context, "lore action skipped | reason=blank_action | raw=" + summarizeMap(operation));
                continue;
            }
            List<String> contentLines = templateRenderer.renderContent(operation, variables, context, ledger.debugLogger(), "item_operation.lore.record." + action);
            Object rawAnchor = templateRenderer.resolveSearchPattern(operation);
            String anchor = rawAnchor == null ? "" : templateRenderer.renderTemplate(rawAnchor, variables, context, ledger.debugLogger(), "item_operation.lore.anchor.record." + action);
            boolean recognized = loreOperations.getProcessor(action) != null;
            debug(context, "lore action | action=" + action
                    + " | recognized=" + recognized
                    + " | anchor=" + summarize(anchor)
                    + " | content=" + summarize(contentLines)
                    + " | raw=" + summarizeMap(operation));

            List<String> originalLines = List.of();
            if ("replace_line".equals(action) || "delete_line".equals(action)) {
                originalLines = findMatchingLines(currentLore, anchor);
            }

            records.add(new ItemOperationEntry.LoreOperationRecord(action, contentLines, anchor, originalLines));
        }

        int beforeSize = existingLore == null ? 0 : existingLore.size();
        loreOperations.apply(currentLore, operations, variables, context, ledger.debugLogger());

        ItemTextBridge.setLoreLines(itemMeta, currentLore.isEmpty() ? null : currentLore);
        debug(context, "lore result | beforeLines=" + beforeSize + " | afterLines=" + currentLore.size());

        return records;
    }

    private List<String> findMatchingLines(List<String> lines, String anchor) {
        if (lines == null || Texts.isBlank(anchor)) {
            return List.of();
        }
        List<String> matched = new ArrayList<>();
        for (String line : lines) {
            if (line != null && line.contains(anchor)) {
                matched.add(line);
            }
        }
        return matched;
    }

    private void debug(ActionContext context, String message) {
        DebugLogger debugLogger = ledger.debugLogger();
        if (debugLogger != null) {
            debugLogger.logRaw("item_operation", context == null ? null : context.player(), message);
        }
    }

    private String summarizeMap(Map<String, ?> values) {
        if (values == null || values.isEmpty()) {
            return "{}";
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            parts.add(entry.getKey() + "=" + summarize(entry.getValue()));
            if (parts.size() >= 8) {
                parts.add("...");
                break;
            }
        }
        return "{" + String.join(", ", parts) + "}";
    }

    private String summarize(Object value) {
        if (value == null) {
            return "";
        }
        String text;
        if (value instanceof Iterable<?> iterable) {
            List<String> parts = new ArrayList<>();
            int count = 0;
            for (Object entry : iterable) {
                if (count >= 4) {
                    parts.add("...");
                    break;
                }
                parts.add(Texts.toStringSafe(entry));
                count++;
            }
            text = "[" + String.join(" | ", parts) + "]";
        } else {
            text = Texts.toStringSafe(value);
        }
        return text.length() <= 160 ? text : text.substring(0, 157) + "...";
    }

    public record ExecutionResult(boolean success, ItemOperationEntry entry) {

        public static final ExecutionResult EMPTY = new ExecutionResult(false, null);
    }
}
