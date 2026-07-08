package emaki.jiuwu.craft.attribute.action;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.attribute.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.model.ResourceDefinition;
import emaki.jiuwu.craft.attribute.model.ResourceState;
import emaki.jiuwu.craft.attribute.model.ResourceSyncReason;
import emaki.jiuwu.craft.attribute.service.AttributeServiceFacade;
import emaki.jiuwu.craft.corelib.action.Action;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class ResourceConsumeAction implements Action {

    public static final String ID = "attribute_resource_consume";

    private final AttributeServiceFacade attributeService;

    ResourceConsumeAction(AttributeServiceFacade attributeService) {
        this.attributeService = attributeService;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "Consume an EmakiAttribute player resource through the resource sync boundary.";
    }

    @Override
    public String category() {
        return "attribute";
    }

    @Override
    public List<ActionParameter> parameters() {
        return List.of(
                ActionParameter.required("resource", ActionParameterType.STRING, "Resource id"),
                ActionParameter.required("amount", ActionParameterType.DOUBLE, "Amount to consume")
        );
    }

    @Override
    public ActionResult validate(Map<String, String> arguments) {
        ActionResult validation = Action.super.validate(arguments);
        if (!validation.success()) {
            return validation;
        }
        if (ActionParsers.parseDouble(arguments.get("amount"), 0D) <= 0D) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "amount must be greater than 0.");
        }
        return ActionResult.ok();
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        Player player = context == null ? null : context.player();
        if (player == null) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "attribute_resource_consume requires a player context.");
        }
        String resourceId = Texts.normalizeId(arguments.get("resource"));
        ResourceDefinition definition = attributeService.resourceDefinitions().get(resourceId);
        if (definition == null) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Unknown resource: " + resourceId);
        }
        double amount = ActionParsers.parseDouble(arguments.get("amount"), 0D);
        ResourceState current = attributeService.readResourceState(player, resourceId);
        AttributeSnapshot snapshot = attributeService.collectCombatSnapshot(player);
        ResourceState synced = current == null
                ? attributeService.syncResource(player, definition, snapshot, ResourceSyncReason.MANUAL, null)
                : current;
        double oldValue = synced == null ? 0D : synced.currentValue();
        double newValue = Math.max(0D, oldValue - amount);
        ResourceState result = attributeService.syncResource(player, definition, snapshot, ResourceSyncReason.MANUAL, newValue);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("resource", resourceId);
        data.put("amount", amount);
        data.put("old_value", oldValue);
        data.put("new_value", result == null ? newValue : result.currentValue());
        data.put("current_max", result == null ? 0D : result.currentMax());
        return ActionResult.ok(data);
    }
}
