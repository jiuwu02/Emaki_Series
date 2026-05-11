package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * 物品操作执行器（内部实现）。
 * <p>
 * 对物品执行 name_actions / lore_actions 操作，并生成操作记录。
 * 外部插件应通过 {@link ItemOperationLedger#apply} 使用。
 */
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

    /**
     * 对物品执行操作并记录到账本。
     *
     * @param itemStack       目标物品
     * @param operationId     操作唯一标识
     * @param sourceNamespace 来源命名空间
     * @param nameActions     名称操作列表（CoreLib 标准格式），可为 null
     * @param loreActions     Lore 操作列表（CoreLib 标准格式），可为 null
     * @param variables       模板变量
     * @return 执行结果，包含是否成功和生成的操作记录
     */
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

        List<ItemOperationEntry.NameOperationRecord> nameRecords = executeNameActions(itemStack, itemMeta, nameActions, safeVariables);
        List<ItemOperationEntry.LoreOperationRecord> loreRecords = executeLoreActions(itemMeta, loreActions, safeVariables);

        if (nameRecords.isEmpty() && loreRecords.isEmpty()) {
            return ExecutionResult.EMPTY;
        }

        // Write back ItemMeta after lore modifications
        itemStack.setItemMeta(itemMeta);

        ItemOperationEntry entry = new ItemOperationEntry(
                operationId,
                sourceNamespace,
                System.currentTimeMillis(),
                nameRecords,
                loreRecords
        );
        ledger.append(itemStack, entry);
        return new ExecutionResult(true, entry);
    }

    private List<ItemOperationEntry.NameOperationRecord> executeNameActions(ItemStack itemStack,
            ItemMeta itemMeta,
            Object nameActions,
            Map<String, Object> variables) {
        if (nameActions == null) {
            return List.of();
        }
        List<Map<String, Object>> operations = templateRenderer.normalizeOperations(nameActions);
        if (operations.isEmpty()) {
            return List.of();
        }

        List<ItemOperationEntry.NameOperationRecord> records = new ArrayList<>();
        String currentName = ItemTextBridge.hasCustomName(itemMeta)
                ? MiniMessages.serialize(ItemTextBridge.customName(itemMeta))
                : "";

        LocalNameState nameState = new LocalNameState();
        nameOperations.apply(nameState, nameActions, variables);

        // Record each operation with rendered values
        for (Map<String, Object> operation : operations) {
            String action = Texts.lower(operation.get("action"));
            Object rawValue = templateRenderer.resolveOperationValue(operation);
            String renderedValue = rawValue == null ? "" : templateRenderer.renderTemplate(rawValue, variables);
            if (Texts.isBlank(action) || Texts.isBlank(renderedValue)) {
                continue;
            }
            records.add(new ItemOperationEntry.NameOperationRecord(action, renderedValue, currentName));
        }

        // Apply name state to item
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
            // Use effective name as base
            String effectiveName = MiniMessages.serialize(ItemTextBridge.effectiveName(itemStack));
            finalName.append(effectiveName);
        }
        for (String postfix : nameState.postfixes()) {
            finalName.append(postfix);
        }
        String result = finalName.toString();
        if (Texts.isNotBlank(result)) {
            ItemTextBridge.customName(itemMeta, MiniMessages.parse(result));
            itemStack.setItemMeta(itemMeta);
        }
    }

    private List<ItemOperationEntry.LoreOperationRecord> executeLoreActions(ItemMeta itemMeta,
            Object loreActions,
            Map<String, Object> variables) {
        if (loreActions == null) {
            return List.of();
        }
        List<Map<String, Object>> operations = templateRenderer.normalizeOperations(loreActions);
        if (operations.isEmpty()) {
            return List.of();
        }

        // Read current lore as MiniMessage strings
        List<String> currentLore = new ArrayList<>();
        List<String> existingLore = ItemTextBridge.loreLines(itemMeta);
        if (existingLore != null) {
            currentLore.addAll(existingLore);
        }

        List<ItemOperationEntry.LoreOperationRecord> records = new ArrayList<>();

        // Execute each operation and record
        for (Map<String, Object> operation : operations) {
            String action = Texts.lower(operation.get("action"));
            if (Texts.isBlank(action)) {
                continue;
            }
            List<String> contentLines = templateRenderer.renderContent(operation, variables);
            Object rawAnchor = templateRenderer.resolveSearchPattern(operation);
            String anchor = rawAnchor == null ? "" : templateRenderer.renderTemplate(rawAnchor, variables);

            // Capture original lines for replace/delete operations
            List<String> originalLines = List.of();
            if ("replace_line".equals(action) || "delete_line".equals(action)) {
                originalLines = findMatchingLines(currentLore, anchor);
            }

            records.add(new ItemOperationEntry.LoreOperationRecord(action, contentLines, anchor, originalLines));
        }

        // Apply all lore operations at once
        loreOperations.apply(currentLore, loreActions, variables);

        // Write back lore
        ItemTextBridge.setLoreLines(itemMeta, currentLore.isEmpty() ? null : currentLore);

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

    public record ExecutionResult(boolean success, ItemOperationEntry entry) {

        public static final ExecutionResult EMPTY = new ExecutionResult(false, null);
    }
}
