package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;

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

    @SuppressWarnings("unchecked")
    public ExecutionResult execute(ItemStack itemStack,
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
        Map<String, Object> safeVariables = variables == null ? Map.of() : (Map<String, Object>) variables;
        List<Map<String, Object>> normalizedNameActions = templateRenderer.normalizeOperations(nameActions);
        List<Map<String, Object>> normalizedLoreActions = templateRenderer.normalizeOperations(loreActions);
        debug("apply start | operationId=" + operationId
                + " | namespace=" + sourceNamespace
                + " | item=" + itemStack.getType().name()
                + " | nameActions=" + normalizedNameActions.size()
                + " | loreActions=" + normalizedLoreActions.size()
                + " | variables=" + summarizeMap(safeVariables));

        List<ItemOperationEntry.NameOperationRecord> nameRecords = executeNameActions(itemStack, itemMeta, normalizedNameActions, safeVariables);
        List<ItemOperationEntry.LoreOperationRecord> loreRecords = executeLoreActions(itemMeta, normalizedLoreActions, safeVariables);

        if (nameRecords.isEmpty() && loreRecords.isEmpty()) {
            debug("apply empty | operationId=" + operationId + " | reason=no_effective_records");
            return ExecutionResult.EMPTY;
        }

        itemStack.setItemMeta(itemMeta);

        ItemOperationEntry entry = new ItemOperationEntry(
                operationId,
                sourceNamespace,
                System.currentTimeMillis(),
                nameRecords,
                loreRecords
        );
        ledger.append(itemStack, entry);
        debug("apply success | operationId=" + operationId
                + " | nameRecords=" + nameRecords.size()
                + " | loreRecords=" + loreRecords.size());
        return new ExecutionResult(true, entry);
    }

    private List<ItemOperationEntry.NameOperationRecord> executeNameActions(ItemStack itemStack,
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

        LocalNameState nameState = new LocalNameState();
        nameOperations.apply(nameState, operations, variables);

        for (Map<String, Object> operation : operations) {
            String action = Texts.lower(operation.get("action"));
            Object rawValue = templateRenderer.resolveOperationValue(operation);
            String renderedValue = rawValue == null ? "" : templateRenderer.renderTemplate(rawValue, variables);
            boolean recognized = nameOperations.getProcessor(action) != null;
            debug("name action | action=" + action
                    + " | recognized=" + recognized
                    + " | value=" + summarize(renderedValue)
                    + " | raw=" + summarizeMap(operation));
            if (Texts.isBlank(action) || Texts.isBlank(renderedValue)) {
                continue;
            }
            records.add(new ItemOperationEntry.NameOperationRecord(action, renderedValue, currentName));
        }

        if (!records.isEmpty()) {
            applyNameState(itemStack, itemMeta, nameState, currentName);
        }
        return records;
    }

    private void applyNameState(ItemStack itemStack, ItemMeta itemMeta, LocalNameState nameState, String originalName) {
        StringBuilder finalName = new StringBuilder();
        for (String prefix : nameState.prefixes()) {
            finalName.append(prefix);
        }
        if (nameState.baseNamePolicy() == BaseNamePolicy.EXPLICIT_TEMPLATE && Texts.isNotBlank(nameState.baseNameTemplate())) {
            finalName.append(nameState.baseNameTemplate());
        } else if (Texts.isNotBlank(originalName)) {
            finalName.append(originalName);
        } else {
            String effectiveName = MiniMessages.serialize(ItemTextBridge.effectiveName(itemStack));
            finalName.append(effectiveName);
        }
        for (String postfix : nameState.postfixes()) {
            finalName.append(postfix);
        }
        String result = finalName.toString();
        debug("name result | original=" + summarize(originalName) + " | result=" + summarize(result));
        if (Texts.isNotBlank(result)) {
            ItemTextBridge.customName(itemMeta, MiniMessages.parse(result));
            itemStack.setItemMeta(itemMeta);
        }
    }

    private List<ItemOperationEntry.LoreOperationRecord> executeLoreActions(ItemMeta itemMeta,
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
                debug("lore action skipped | reason=blank_action | raw=" + summarizeMap(operation));
                continue;
            }
            List<String> contentLines = templateRenderer.renderContent(operation, variables);
            Object rawAnchor = templateRenderer.resolveSearchPattern(operation);
            String anchor = rawAnchor == null ? "" : templateRenderer.renderTemplate(rawAnchor, variables);
            boolean recognized = loreOperations.getProcessor(action) != null;
            debug("lore action | action=" + action
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
        loreOperations.apply(currentLore, operations, variables);

        ItemTextBridge.setLoreLines(itemMeta, currentLore.isEmpty() ? null : currentLore);
        debug("lore result | beforeLines=" + beforeSize + " | afterLines=" + currentLore.size());

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

    private void debug(String message) {
        DebugLogger debugLogger = ledger.debugLogger();
        if (debugLogger != null) {
            debugLogger.logRaw("item_operation", (UUID) null, message);
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
