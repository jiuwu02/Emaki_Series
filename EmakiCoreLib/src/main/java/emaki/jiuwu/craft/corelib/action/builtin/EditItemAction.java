package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.assembly.ItemPresentationCompiler;
import emaki.jiuwu.craft.corelib.assembly.ItemPresentationEditor;
import emaki.jiuwu.craft.corelib.assembly.PresentationCompileIssue;
import emaki.jiuwu.craft.corelib.assembly.lore.ActionNameParser;
import emaki.jiuwu.craft.corelib.assembly.lore.IndexedLineInsertActionParser;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class EditItemAction extends BaseAction {

    private static final List<String> NAME_OPERATIONS = List.of(
            "append_suffix",
            "prepend_prefix",
            "replace",
            "regex_replace"
    );

    private static final List<String> LORE_OPERATIONS = List.of(
            "append",
            "prepend",
            "insert_below",
            "insert_above",
            "replace_line",
            "delete_line",
            "regex_replace"
    );

    private final ItemPresentationEditor presentationEditor;

    public EditItemAction(ItemPresentationCompiler itemPresentationCompiler) {
        super(
                "edititem",
                "item",
                "Edit a temporary item's name or lore using CoreLib presentation operations.",
                ActionParameter.required("id", ActionParameterType.STRING, "Temporary item id"),
                ActionParameter.optional("target", ActionParameterType.STRING, "", "Edit target: name or lore")
        );
        this.presentationEditor = new ItemPresentationEditor(itemPresentationCompiler);
    }

    @Override
    public boolean acceptsDynamicParameter(String name) {
        return true;
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        String itemId = stringArg(arguments, "id");
        if (Texts.isBlank(itemId)) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "edititem requires a non-empty 'id'.");
        }
        TemporaryItemStore store = TemporaryItemStore.from(context);
        ItemStack itemStack = store.get(itemId);
        if (itemStack == null || itemStack.getType().isAir()) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "Temporary item not found: " + itemId);
        }
        String target = resolveTarget(arguments);
        String operation = resolveOperation(arguments);
        if (Texts.isBlank(operation)) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "edititem requires 'operation'.");
        }

        Map<String, Object> rawOperation = buildRawOperation(arguments, target, operation);
        ItemPresentationEditor.EditResult result;
        if (isNameTarget(target)) {
            if (!isSupportedNameOperation(operation)) {
                return ActionResult.failure(ActionErrorType.UNSUPPORTED, "Unsupported name operation: " + operation);
            }
            result = presentationEditor.applyNameOperation(itemStack, rawOperation);
        } else if (isLoreTarget(target)) {
            if (!isSupportedLoreOperation(operation)) {
                return ActionResult.failure(ActionErrorType.UNSUPPORTED, "Unsupported lore operation: " + operation);
            }
            result = presentationEditor.applyLoreOperation(itemStack, rawOperation);
        } else {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "edititem target must be 'name' or 'lore'.");
        }

        if (!result.success()) {
            PresentationCompileIssue firstIssue = result.issues().isEmpty() ? null : result.issues().get(0);
            String message = firstIssue == null
                    ? "Failed to edit temporary item: " + itemId
                    : "Invalid presentation operation: " + firstIssue.detail();
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, message);
        }
        store.put(itemId, result.itemStack());
        return ActionResult.ok(Map.of(
                "id", Texts.lower(itemId),
                "target", target,
                "operation", operation
        ));
    }

    private Map<String, Object> buildRawOperation(Map<String, String> arguments, String target, String operation) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("action", operation);
        if (isNameTarget(target)) {
            copyIfPresent(arguments, raw, "value");
            copyIfPresent(arguments, raw, "regex_pattern");
            copyIfPresent(arguments, raw, "replacement");
            return raw;
        }
        copyIfPresent(arguments, raw, "target_pattern");
        copyIfPresent(arguments, raw, "regex_pattern");
        copyIfPresent(arguments, raw, "replacement");
        copyIfPresent(arguments, raw, "ignore_case");
        copyIfPresent(arguments, raw, "inherit_style");
        copyIfPresent(arguments, raw, "on_not_found");
        List<String> content = resolveContent(arguments);
        if (!content.isEmpty()) {
            raw.put("content", content);
        }
        return raw;
    }

    private List<String> resolveContent(Map<String, String> arguments) {
        String raw = arguments.get("content");
        if (Texts.isBlank(raw)) {
            return List.of();
        }
        return List.of(raw.split("\\R", -1));
    }

    private void copyIfPresent(Map<String, String> arguments, Map<String, Object> target, String key) {
        String value = arguments.get(key);
        if (Texts.isNotBlank(value)) {
            target.put(key, value);
        }
    }

    String resolveTarget(Map<String, String> arguments) {
        return normalize(arguments == null ? null : arguments.get("target"));
    }

    String resolveOperation(Map<String, String> arguments) {
        return normalize(arguments == null ? null : arguments.get("operation"));
    }

    private boolean isNameTarget(String value) {
        return "name".equals(value);
    }

    private boolean isLoreTarget(String value) {
        return "lore".equals(value);
    }

    private boolean isSupportedNameOperation(String operation) {
        return NAME_OPERATIONS.contains(operation);
    }

    private boolean isSupportedLoreOperation(String operation) {
        return LORE_OPERATIONS.contains(operation)
                || ActionNameParser.isSearchInsertAction(operation)
                || IndexedLineInsertActionParser.isIndexedLineInsertAction(operation);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
