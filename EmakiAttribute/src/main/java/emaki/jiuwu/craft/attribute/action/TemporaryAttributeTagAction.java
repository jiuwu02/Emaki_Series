package emaki.jiuwu.craft.attribute.action;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.attribute.model.TemporaryStackMode;
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

public final class TemporaryAttributeTagAction implements Action {

    public static final String ADD_ID = "attribute_tag_add";
    public static final String REMOVE_ID = "attribute_tag_remove";
    public static final String CLEAR_ID = "attribute_tag_clear";

    private final String id;
    private final AttributeServiceFacade attributeService;

    TemporaryAttributeTagAction(String id, AttributeServiceFacade attributeService) {
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
            return "Add temporary attributes to all definitions with a tag.";
        }
        return "Remove temporary attributes whose definitions have a tag.";
    }

    @Override
    public String category() {
        return "attribute";
    }

    @Override
    public List<ActionParameter> parameters() {
        if (ADD_ID.equals(id)) {
            return List.of(
                    ActionParameter.required("tag", ActionParameterType.STRING, "Attribute tag"),
                    ActionParameter.required("value", ActionParameterType.DOUBLE, "Temporary value"),
                    ActionParameter.required("duration_ticks", ActionParameterType.TIME, "Duration in ticks"),
                    ActionParameter.optional("effect_prefix", ActionParameterType.STRING, "", "Temporary effect id prefix"),
                    ActionParameter.optional("stack_mode", ActionParameterType.STRING, "", "Optional temporary stack mode")
            );
        }
        return List.of(ActionParameter.required("tag", ActionParameterType.STRING, "Attribute tag"));
    }

    @Override
    public ActionResult validate(Map<String, String> arguments) {
        ActionResult validation = Action.super.validate(arguments);
        if (!validation.success()) {
            return validation;
        }
        if (ADD_ID.equals(id) && ActionParsers.parseTicks(arguments.get("duration_ticks")) <= 0L) {
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
        String tag = Texts.toStringSafe(arguments.get("tag")).trim();
        TemporaryAttributeService service = attributeService.temporaryAttributeService();
        int count;
        if (ADD_ID.equals(id)) {
            double value = ActionParsers.parseDouble(arguments.get("value"), 0D);
            long durationTicks = ActionParsers.parseTicks(arguments.get("duration_ticks"));
            count = service.addByTag(player, arguments.get("effect_prefix"), tag, value, durationTicks, stackMode(arguments.get("stack_mode")));
        } else {
            count = service.removeByTag(player, tag);
        }
        return ActionResult.ok(Map.of("tag", tag, "count", count));
    }

    private TemporaryStackMode stackMode(String value) {
        if (Texts.isBlank(value)) {
            return null;
        }
        try {
            return TemporaryStackMode.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
