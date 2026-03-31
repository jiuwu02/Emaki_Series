package emaki.jiuwu.craft.attribute.action;

import emaki.jiuwu.craft.attribute.service.AttributeServiceFacade;
import emaki.jiuwu.craft.corelib.action.Action;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.event.entity.EntityDamageEvent;

public final class AttributeDamageAction implements Action {

    public static final String ID = "attributedamage";

    private final AttributeServiceFacade attributeService;

    public AttributeDamageAction(AttributeServiceFacade attributeService) {
        this.attributeService = attributeService;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "Deal attribute-based custom damage to the current player.";
    }

    @Override
    public String category() {
        return "combat";
    }

    @Override
    public List<ActionParameter> parameters() {
        return List.of(
            ActionParameter.required("amount", ActionParameterType.DOUBLE, "Base damage"),
            ActionParameter.optional("type", ActionParameterType.STRING, "", "Damage type id"),
            ActionParameter.optional("cause", ActionParameterType.STRING, "CUSTOM", "Damage cause")
        );
    }

    @Override
    public ActionResult validate(Map<String, String> arguments) {
        ActionResult validation = Action.super.validate(arguments);
        if (!validation.success()) {
            return validation;
        }
        String cause = Texts.toStringSafe(arguments.get("cause"));
        if (Texts.isBlank(cause) || PLACEHOLDER_PATTERN.matcher(cause).find() || parseCause(cause) != null) {
            return ActionResult.ok();
        }
        return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Unknown damage cause: " + cause);
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        if (context == null || context.player() == null) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "Action '" + ID + "' requires a player context.");
        }
        EntityDamageEvent.DamageCause cause = parseCause(arguments.get("cause"));
        String damageTypeId = Texts.toStringSafe(arguments.get("type"));
        if (Texts.isBlank(damageTypeId)) {
            damageTypeId = attributeService.defaultDamageTypeId();
        }
        Map<String, Object> damageContext = new LinkedHashMap<>();
        if (cause != null) {
            damageContext.put("damage_cause", cause.name());
            damageContext.put("cause", cause.name());
        }
        damageContext.put("action_id", ID);
        damageContext.put("damage_type_id", damageTypeId);
        boolean applied = attributeService.applyDamage(
            null,
            context.player(),
            damageTypeId,
            ActionParsers.parseDouble(arguments.get("amount"), 0D),
            damageContext
        );
        return applied
            ? ActionResult.ok(Map.of("damage_type", damageTypeId))
            : ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, "Failed to apply attribute damage.");
    }

    private EntityDamageEvent.DamageCause parseCause(String raw) {
        if (Texts.isBlank(raw)) {
            return EntityDamageEvent.DamageCause.CUSTOM;
        }
        try {
            return EntityDamageEvent.DamageCause.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
