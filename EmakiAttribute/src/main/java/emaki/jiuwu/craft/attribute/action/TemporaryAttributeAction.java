package emaki.jiuwu.craft.attribute.action;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.attribute.service.AttributeServiceFacade;
import emaki.jiuwu.craft.attribute.service.TemporaryAttributeService;
import emaki.jiuwu.craft.corelib.action.Action;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class TemporaryAttributeAction implements Action {

    public static final String ADD_ID = "attribute_add";
    public static final String SET_ID = "attribute_set";
    public static final String REMOVE_ID = "attribute_remove";

    private final String id;
    private final AttributeServiceFacade attributeService;

    TemporaryAttributeAction(String id, AttributeServiceFacade attributeService) {
        this.id = id;
        this.attributeService = attributeService;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String description() {
        if (ADD_ID.equals(id)) {
            return "Add a temporary attribute value to the current player.";
        }
        if (SET_ID.equals(id)) {
            return "Set a temporary attribute value for the current player.";
        }
        return "Remove a temporary attribute from the current player.";
    }

    @Override
    public String category() {
        return "attribute";
    }

    @Override
    public List<ActionParameter> parameters() {
        if (REMOVE_ID.equals(id)) {
            return List.of(ActionParameter.required("effect_id", ActionParameterType.STRING, "Temporary effect id"));
        }
        return List.of(
                ActionParameter.required("effect_id", ActionParameterType.STRING, "Temporary effect id"),
                ActionParameter.required("attribute", ActionParameterType.STRING, "Attribute id"),
                ActionParameter.required("value", ActionParameterType.DOUBLE, "Temporary value"),
                ActionParameter.required("duration_ticks", ActionParameterType.TIME, "Duration in ticks")
        );
    }

    @Override
    public ActionResult validate(Map<String, String> arguments) {
        ActionResult validation = Action.super.validate(arguments);
        if (!validation.success()) {
            return validation;
        }
        if (!REMOVE_ID.equals(id) && ActionParsers.parseTicks(arguments.get("duration_ticks")) <= 0L) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "duration_ticks must be greater than 0.");
        }
        return ActionResult.ok();
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        Player player = context == null ? null : context.player();
        if (player == null) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "Action '" + id + "' requires a player context.");
        }
        TemporaryAttributeService service = attributeService.temporaryAttributeService();
        String effectId = Texts.normalizeId(arguments.get("effect_id"));
        if (REMOVE_ID.equals(id)) {
            TemporaryAttributeService.TemporaryAttributeResult result = service.remove(player, effectId);
            return ActionResult.ok(resultData(result, effectId, "", 0D, 0L));
        }
        String attributeId = Texts.normalizeId(arguments.get("attribute"));
        double value = ActionParsers.parseDouble(arguments.get("value"), 0D);
        long durationTicks = ActionParsers.parseTicks(arguments.get("duration_ticks"));
        TemporaryAttributeService.TemporaryAttributeResult result = ADD_ID.equals(id)
                ? service.add(player, effectId, attributeId, value, durationTicks)
                : service.set(player, effectId, attributeId, value, durationTicks);
        return ActionResult.ok(resultData(result, effectId, attributeId, value, durationTicks));
    }

    private Map<String, Object> resultData(TemporaryAttributeService.TemporaryAttributeResult result,
            String effectId,
            String attributeId,
            double value,
            long durationTicks) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("effect_id", effectId);
        data.put("attribute", attributeId);
        data.put("value", value);
        data.put("duration_ticks", durationTicks);
        data.put("existed", result != null && result.existed());
        if (result != null && result.entry() != null) {
            data.put("remaining_ticks", result.entry().remainingTicks(System.currentTimeMillis()));
        }
        return data;
    }
}
