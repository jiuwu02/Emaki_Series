package emaki.jiuwu.craft.item.action;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.action.CoreAction;
import emaki.jiuwu.craft.corelib.api.action.CoreActionContext;
import emaki.jiuwu.craft.corelib.api.action.CoreActionErrorType;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionMode;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionTarget;
import emaki.jiuwu.craft.corelib.api.action.CoreActionParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreActionParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreActionPlanningContext;
import emaki.jiuwu.craft.corelib.api.action.CoreActionResult;
import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.item.ItemBuildIssue;
import emaki.jiuwu.craft.corelib.api.item.ItemBuildIssueSeverity;
import emaki.jiuwu.craft.corelib.api.item.ItemBuildResult;
import emaki.jiuwu.craft.corelib.api.item.ItemComponentPatch;
import emaki.jiuwu.craft.item.service.ItemComponentInspector;
import emaki.jiuwu.craft.item.service.ItemComponentInspector.ComponentValueParseResult;

public final class ItemComponentAction implements CoreAction {

    public enum Operation {
        ADD,
        MODIFY,
        REMOVE
    }

    private final ItemComponentInspector inspector;
    private final ItemActionTargetResolver targetResolver = new ItemActionTargetResolver();
    private final Operation operation;
    private final List<CoreActionParameter> parameters;

    public ItemComponentAction(ItemComponentInspector inspector, Operation operation) {
        this.inspector = inspector;
        this.operation = operation == null ? Operation.MODIFY : operation;
        this.parameters = this.operation != Operation.REMOVE
                ? List.of(
                        CoreActionParameter.required("component", CoreActionParameterType.STRING,
                                "Vanilla item component id"),
                        CoreActionParameter.required("value", CoreActionParameterType.STRING,
                                "JSON-ish component value"),
                        CoreActionParameter.optional("slot", CoreActionParameterType.STRING, "",
                                "Explicit player inventory slot")
                )
                : List.of(
                        CoreActionParameter.required("component", CoreActionParameterType.STRING,
                                "Vanilla item component id"),
                        CoreActionParameter.optional("slot", CoreActionParameterType.STRING, "",
                                "Explicit player inventory slot")
                );
    }

    @Override
    public String id() {
        return switch (operation) {
            case ADD -> "emakiitem:component_add";
            case MODIFY -> "emakiitem:component_modify";
            case REMOVE -> "emakiitem:component_remove";
        };
    }

    @Override
    public String description() {
        return switch (operation) {
            case ADD -> "Adds one component when it is not already present.";
            case MODIFY -> "Modifies one component that is already present.";
            case REMOVE -> "Removes one component from the resolved item target.";
        };
    }

    @Override
    public String category() {
        return "item";
    }

    @Override
    public String version() {
        return "1.0.0";
    }

    @Override
    public List<CoreActionParameter> parameters() {
        return parameters;
    }

    @Override
    public CoreActionExecutionMode executionMode() {
        return CoreActionExecutionMode.SYNC;
    }

    @Override
    public CoreActionExecutionTarget executionTarget(CoreActionPlanningContext context) {
        CoreActionContext actionContext = context == null ? null : context.actionContext();
        return actionContext != null && actionContext.player() != null
                ? CoreActionExecutionTarget.contextEntity()
                : CoreActionExecutionTarget.global();
    }

    @Override
    public CoreActionResult validate(Map<String, String> arguments) {
        Map<String, String> safeArguments = arguments == null ? Map.of() : arguments;
        CoreActionResult base = CoreAction.super.validate(safeArguments);
        if (!base.success()) {
            return base;
        }
        if (inspector == null) {
            return CoreActionResult.failure(CoreActionErrorType.INVALID_STATE,
                    "Item component inspector is unavailable.");
        }
        String componentId = inspector.normalizeComponentId(safeArguments.get("component"));
        if (componentId.isBlank()) {
            return CoreActionResult.failure(CoreActionErrorType.INVALID_ARGUMENT,
                    "Component id cannot be blank.");
        }
        if (safeArguments.containsKey("slot")
                && (safeArguments.get("slot") == null || safeArguments.get("slot").trim().isEmpty())) {
            return CoreActionResult.failure(CoreActionErrorType.INVALID_ARGUMENT,
                    "Explicit component action slot cannot be blank.");
        }
        if (safeArguments.containsKey("slot") && ItemInventorySlot.parse(safeArguments.get("slot")) == null) {
            return CoreActionResult.failure(CoreActionErrorType.INVALID_ARGUMENT,
                    "Unsupported component action slot: " + safeArguments.get("slot"));
        }
        if (operation != Operation.REMOVE) {
            ComponentValueParseResult parsed = inspector.parseComponentValue(safeArguments.get("value"));
            if (!parsed.success()) {
                return CoreActionResult.failure(CoreActionErrorType.INVALID_ARGUMENT, parsed.errorMessage());
            }
        }
        return CoreActionResult.ok();
    }

    @Override
    public CoreActionResult execute(CoreActionContext context, Map<String, String> arguments) {
        try {
            if (inspector == null) {
                return CoreActionResult.failure(CoreActionErrorType.INVALID_STATE,
                        "Item component inspector is unavailable.");
            }
            Map<String, String> safeArguments = arguments == null ? Map.of() : arguments;
            String componentId = inspector.normalizeComponentId(safeArguments.get("component"));
            if (componentId.isBlank()) {
                return CoreActionResult.failure(CoreActionErrorType.INVALID_ARGUMENT,
                        "Component id cannot be blank.");
            }

            ItemComponentPatch patch;
            if (operation != Operation.REMOVE) {
                ComponentValueParseResult parsed = inspector.parseComponentValue(safeArguments.get("value"));
                if (!parsed.success()) {
                    return CoreActionResult.failure(CoreActionErrorType.INVALID_ARGUMENT, parsed.errorMessage());
                }
                patch = ItemComponentPatch.set(parsed.value());
            } else {
                patch = ItemComponentPatch.unset();
            }

            ItemActionTargetResolver.Resolution resolution = targetResolver.resolve(context, safeArguments);
            if (!resolution.resolved()) {
                return resolution.failure();
            }
            ItemActionTargetResolver.Target target = resolution.target();
            ItemStack original = target.itemStack();
            if (original == null || original.getType().isAir()) {
                return CoreActionResult.skipped("No item present at target '" + target.id() + "'.");
            }
            boolean existedBefore = inspector.contains(original, componentId);
            CoreActionResult existenceResult = validateExistence(existedBefore);
            if (!existenceResult.success() || existenceResult.skipped()) {
                return existenceResult;
            }
            ConfiguredItemDefinition definition = new ConfiguredItemDefinition(
                    null,
                    original.getAmount(),
                    Map.of(componentId, patch)
            );
            ItemBuildResult buildResult = EmakiCoreLibApi.applyConfiguredItem(original, definition);
            if (buildResult.hasErrors()) {
                return buildFailure(buildResult);
            }
            ItemStack updated = buildResult.itemStack();
            if (updated == null) {
                return CoreActionResult.failure(CoreActionErrorType.INVALID_STATE,
                        issueSummary(buildResult.issues(), "Component patch returned no item."));
            }

            boolean changed = !updated.equals(original);
            target.commit(updated);
            return CoreActionResult.ok(resultData(componentId, target.id(), changed, existedBefore, buildResult.issues()));
        } catch (RuntimeException | LinkageError exception) {
            return CoreActionResult.failure(CoreActionErrorType.EXECUTION_EXCEPTION, message(exception));
        }
    }

    CoreActionResult validateExistence(boolean existedBefore) {
        return switch (operation) {
            case ADD -> existedBefore
                    ? CoreActionResult.failure(CoreActionErrorType.INVALID_STATE,
                            "Component already exists; use emakiitem:component_modify instead.")
                    : CoreActionResult.ok();
            case MODIFY -> existedBefore
                    ? CoreActionResult.ok()
                    : CoreActionResult.failure(CoreActionErrorType.INVALID_STATE,
                            "Component does not exist; use emakiitem:component_add instead.");
            case REMOVE -> existedBefore
                    ? CoreActionResult.ok()
                    : CoreActionResult.skipped("Component is not present on the resolved item target.");
        };
    }

    private CoreActionResult buildFailure(ItemBuildResult result) {
        String summary = issueSummary(result.issues(), "Component patch failed.");
        boolean unavailable = result.issues().stream()
                .map(ItemBuildIssue::message)
                .map(message -> message.toLowerCase(Locale.ROOT))
                .anyMatch(message -> message.contains("unavailable"));
        return CoreActionResult.failure(
                unavailable ? CoreActionErrorType.PROVIDER_UNAVAILABLE : CoreActionErrorType.INVALID_ARGUMENT,
                summary
        );
    }

    private Map<String, Object> resultData(String componentId,
            String targetId,
            boolean changed,
            boolean existedBefore,
            List<ItemBuildIssue> issues) {
        List<String> issueSummaries = issueSummaries(issues);
        List<String> warnings = issues == null ? List.of() : issues.stream()
                .filter(issue -> issue.severity() == ItemBuildIssueSeverity.WARNING)
                .map(this::issueSummary)
                .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("operation", operation.name().toLowerCase(Locale.ROOT));
        data.put("component", componentId);
        data.put("target", targetId);
        data.put("changed", changed);
        data.put("existed_before", existedBefore);
        data.put("warnings", warnings);
        data.put("issues", issueSummaries);
        return data;
    }

    private String issueSummary(List<ItemBuildIssue> issues, String fallback) {
        List<String> summaries = issueSummaries(issues);
        return summaries.isEmpty() ? fallback : String.join("; ", summaries);
    }

    private List<String> issueSummaries(List<ItemBuildIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return List.of();
        }
        List<String> summaries = new ArrayList<>(issues.size());
        for (ItemBuildIssue issue : issues) {
            summaries.add(issueSummary(issue));
        }
        return List.copyOf(summaries);
    }

    private String issueSummary(ItemBuildIssue issue) {
        String component = issue.componentId() == null ? "" : " [" + issue.componentId() + "]";
        return issue.severity().name() + component + ": " + issue.message();
    }

    private String message(Throwable throwable) {
        String value = throwable == null ? null : throwable.getMessage();
        return value == null || value.isBlank() ? throwable.getClass().getSimpleName() : value;
    }
}
