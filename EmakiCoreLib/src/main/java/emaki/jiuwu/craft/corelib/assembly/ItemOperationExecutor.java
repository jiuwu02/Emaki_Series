package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
        debug(context, "common.item_operation.apply_start", replacements(
                "operation_id", operationId,
                "namespace", sourceNamespace,
                "item", itemStack.getType().name(),
                "name_actions", normalizedNameActions.size(),
                "lore_actions", normalizedLoreActions.size(),
                "variables", safeVariables
        ));

        List<ItemOperationEntry.NameOperationRecord> nameRecords = collectNameRecords(context, itemMeta, normalizedNameActions, safeVariables);
        LocalNameState nameState = resolveNameState(context, normalizedNameActions, safeVariables);
        List<ItemOperationEntry.LoreOperationRecord> loreRecords = executeLoreActions(context, itemMeta, normalizedLoreActions, safeVariables);

        if (nameRecords.isEmpty() && loreRecords.isEmpty()) {
            debug(context, "common.item_operation.apply_empty", replacements("operation_id", operationId));
            return ExecutionResult.EMPTY;
        }


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
        debug(context, "common.item_operation.apply_success", replacements(
                "operation_id", operationId,
                "name_records", nameRecords.size(),
                "lore_records", loreRecords.size()
        ));
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
            boolean recognized = nameOperations.getProcessor(action) != null;
            String regexPattern = "regex_replace".equals(action)
                    ? Texts.toStringSafe(operation.get("regex_pattern"))
                    : "";
            String renderedValue;
            if ("regex_replace".equals(action)) {
                renderedValue = Texts.formatTemplate(
                        Texts.toStringSafe(operation.get("replacement")),
                        variables
                );
            } else {
                Object rawValue = templateRenderer.resolveOperationValue(operation);
                renderedValue = rawValue == null ? "" : templateRenderer.renderTemplate(
                        rawValue,
                        variables,
                        context,
                        ledger.debugLogger(),
                        "item_operation.name.record." + action
                );
            }
            debug(context, "common.item_operation.name_action", replacements(
                    "action", action,
                    "recognized", recognized,
                    "value", renderedValue,
                    "raw", operation
            ));
            if (Texts.isBlank(action) || !recognized) {
                continue;
            }
            if (!"regex_replace".equals(action) && Texts.isBlank(renderedValue)) {
                continue;
            }
            if ("regex_replace".equals(action) && Texts.isBlank(regexPattern)) {
                continue;
            }
            records.add(new ItemOperationEntry.NameOperationRecord(
                    action,
                    renderedValue,
                    currentName,
                    regexPattern
            ));
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
        debug(context, "common.item_operation.name_result", replacements(
                "base", MiniMessages.serialize(baseName),
                "result", MiniMessages.serialize(result)
        ));
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
                debug(context, "common.item_operation.lore_action_skipped", replacements("raw", operation));
                continue;
            }
            List<String> contentLines = templateRenderer.renderContent(operation, variables, context, ledger.debugLogger(), "item_operation.lore.record." + action);
            Object rawAnchor = templateRenderer.resolveSearchPattern(operation);
            String anchor = rawAnchor == null ? "" : templateRenderer.renderTemplate(rawAnchor, variables, context, ledger.debugLogger(), "item_operation.lore.anchor.record." + action);
            LoreOperationProcessor processor = loreOperations.getProcessor(action);
            boolean recognized = processor != null;
            debug(context, "common.item_operation.lore_action", replacements(
                    "action", action,
                    "recognized", recognized,
                    "anchor", anchor,
                    "content", contentLines,
                    "raw", operation
            ));
            if (!recognized) {
                continue;
            }

            List<String> originalLines = List.of();
            if ("replace_line".equals(action) || "delete_line".equals(action)) {
                originalLines = findMatchingLines(currentLore, anchor);
            }
            Integer parsedIndex = emaki.jiuwu.craft.corelib.math.Numbers.tryParseInt(operation.get("index"), null);
            int requestedIndex = parsedIndex == null ? 0 : Math.max(0, parsedIndex);
            String regexPattern = "regex_replace".equals(action)
                    ? Texts.toStringSafe(operation.get("regex_pattern"))
                    : "";
            String regexReplacement = "regex_replace".equals(action)
                    ? Texts.formatTemplate(Texts.toStringSafe(operation.get("replacement")), variables)
                    : "";

            records.add(records.isEmpty()
                    ? new ItemOperationEntry.LoreOperationRecord(
                    action,
                    contentLines,
                    anchor,
                    originalLines,
                    new ArrayList<>(currentLore),
                    requestedIndex,
                    regexPattern,
                    regexReplacement
            )
                    : new ItemOperationEntry.LoreOperationRecord(
                    action,
                    contentLines,
                    anchor,
                    originalLines,
                    requestedIndex,
                    regexPattern,
                    regexReplacement
            ));
            processor.process(currentLore, new LoreOperationProcessor.Context(operation, contentLines, anchor, variables));
        }

        int beforeSize = existingLore == null ? 0 : existingLore.size();
        ItemTextBridge.setLoreLines(itemMeta, currentLore.isEmpty() ? null : currentLore);
        debug(context, "common.item_operation.lore_result", replacements(
                "before_lines", beforeSize,
                "after_lines", currentLore.size()
        ));

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

    private void debug(ActionContext context, String langKey, Map<String, ?> replacements) {
        DebugLogger debugLogger = ledger.debugLogger();
        if (debugLogger != null) {
            debugLogger.log("item_operation", context == null ? null : context.player(), langKey, replacements);
        }
    }

    private Map<String, Object> replacements(Object... entries) {
        Map<String, Object> replacements = new LinkedHashMap<>();
        for (int index = 0; index + 1 < entries.length; index += 2) {
            replacements.put(Texts.toStringSafe(entries[index]), entries[index + 1]);
        }
        return replacements;
    }

    public record ExecutionResult(boolean success, ItemOperationEntry entry) {

        public static final ExecutionResult EMPTY = new ExecutionResult(false, null);
    }
}
