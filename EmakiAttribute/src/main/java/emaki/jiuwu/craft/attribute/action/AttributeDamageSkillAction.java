package emaki.jiuwu.craft.attribute.action;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

import emaki.jiuwu.craft.attribute.service.AttributeServiceFacade;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.skills.api.SkillScriptAction;
import emaki.jiuwu.craft.skills.api.SkillScriptContext;

public final class AttributeDamageSkillAction implements SkillScriptAction {

    public static final String ID = "attribute_damage";

    private static final List<ActionParameter> PARAMETERS = List.of(
            ActionParameter.required("amount", ActionParameterType.DOUBLE, "Base damage amount"),
            ActionParameter.optional("damage_type", ActionParameterType.STRING, "", "Damage type id"),
            ActionParameter.optional("target", ActionParameterType.STRING, "target", "Target entity"),
            ActionParameter.optional("cause", ActionParameterType.STRING, "CUSTOM", "Damage cause"),
            ActionParameter.optional("element", ActionParameterType.STRING, "", "Element tag")
    );

    private final AttributeServiceFacade attributeService;

    public AttributeDamageSkillAction(AttributeServiceFacade attributeService) {
        this.attributeService = attributeService;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String category() {
        return "combat";
    }

    @Override
    public String description() {
        return "Apply attribute-based damage through the EmakiAttribute damage pipeline.";
    }

    @Override
    public List<ActionParameter> parameters() {
        return PARAMETERS;
    }

    @Override
    public CompletableFuture<ActionResult> execute(SkillScriptContext context, Map<String, String> arguments) {
        if (context == null || context.caster() == null) {
            return CompletableFuture.completedFuture(
                    ActionResult.failure(ActionErrorType.INVALID_STATE, "Skill action '" + ID + "' requires a caster."));
        }

        Entity targetEntity = resolveTarget(context, arguments);
        if (!(targetEntity instanceof LivingEntity target)) {
            return CompletableFuture.completedFuture(
                    ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Damage target is not a living entity."));
        }

        double amount = ActionParsers.parseDouble(arg(arguments, "amount"), 0D);
        if (amount <= 0D) {
            return CompletableFuture.completedFuture(
                    ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Damage amount must be positive."));
        }

        String damageTypeId = arg(arguments, "damage_type");
        if (Texts.isBlank(damageTypeId)) {
            damageTypeId = attributeService.defaultDamageTypeId();
        }

        Map<String, Object> damageContext = new LinkedHashMap<>();
        String causeStr = arg(arguments, "cause");
        EntityDamageEvent.DamageCause cause = parseCause(causeStr);
        if (cause != null) {
            damageContext.put("damage_cause", cause.name());
            damageContext.put("cause", cause.name());
        }
        damageContext.put("action_id", ID);
        damageContext.put("damage_type_id", damageTypeId);
        String element = arg(arguments, "element");
        if (Texts.isNotBlank(element)) {
            damageContext.put("element", element);
        }

        Player caster = context.caster();
        boolean applied = attributeService.applyDamage(caster, target, damageTypeId, amount, damageContext);

        return CompletableFuture.completedFuture(
                applied
                        ? ActionResult.ok(Map.of("damage_type", damageTypeId, "amount", String.valueOf(amount)))
                        : ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, "Failed to apply attribute damage."));
    }

    private Entity resolveTarget(SkillScriptContext context, Map<String, String> arguments) {
        String targetKey = arg(arguments, "target");
        if (Texts.isBlank(targetKey)) {
            targetKey = "target";
        }
        String normalized = targetKey.toLowerCase(Locale.ROOT);
        if ("caster".equals(normalized) || "self".equals(normalized) || "player".equals(normalized)) {
            return context.caster();
        }
        Object stored = context.sharedValue(normalized);
        if (stored instanceof Entity entity) {
            return entity;
        }
        return context.targetEntity();
    }

    private String arg(Map<String, String> arguments, String key) {
        if (arguments == null) {
            return "";
        }
        String value = arguments.get(key);
        return value == null ? "" : value.trim();
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
