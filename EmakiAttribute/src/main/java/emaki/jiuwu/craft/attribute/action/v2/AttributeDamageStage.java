package emaki.jiuwu.craft.attribute.action.v2;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.attribute.service.AttributeServiceFacade;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionTarget;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStagePlanningContext;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * Applies attribute-typed custom damage to the target.
 *
 * <p>The v2 counterpart of {@code AttributeDamageAction}. Unlike the builtin {@code damage} stage this routes
 * through EmakiAttribute so the module's damage types, resistances and combat snapshot all apply.</p>
 *
 * <p>Domain {@code CONTEXT_ENTITY}: applies damage to one entity.</p>
 */
public final class AttributeDamageStage implements CoreActionStage {

    private final AttributeServiceFacade attributeService;

    /**
     * Creates the stage.
     *
     * @param attributeService the module's service facade
     */
    public AttributeDamageStage(@NotNull AttributeServiceFacade attributeService) {
        this.attributeService = attributeService;
    }

    @Override
    public @NotNull String id() {
        return "attribute_damage";
    }

    @Override
    public @NotNull String description() {
        return "Applies attribute-typed custom damage to the target.";
    }

    @Override
    public @NotNull String category() {
        return "attribute";
    }

    @Override
    public @NotNull List<CoreStageParameter> parameters() {
        return List.of(
                CoreStageParameter.required("amount", CoreStageParameterType.DOUBLE, "Base damage"),
                CoreStageParameter.optional("type", CoreStageParameterType.STRING, "", "Damage type id"),
                CoreStageParameter.optional("cause", CoreStageParameterType.STRING, "CUSTOM",
                        "Bukkit damage cause"));
    }

    @Override
    public @NotNull CoreTargetRequirement targetRequirement() {
        return CoreTargetRequirement.REQUIRED_ENTITY;
    }

    @Override
    public @NotNull CoreActionExecutionTarget executionTarget(@NotNull CoreStagePlanningContext context) {
        return CoreActionExecutionTarget.contextEntity();
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        if (attributeService == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.MISSING_CONTEXT,
                    "action.v2.stage.attribute.service_unavailable");
        }
        Player target = player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.v2.stage.common.not_player");
        }
        String rawCause = arguments.getString("cause", "CUSTOM");
        EntityDamageEvent.DamageCause cause = parseCause(rawCause);
        if (cause == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.v2.stage.attribute.unknown_damage_cause", Map.of("cause", rawCause));
        }
        String damageTypeId = Texts.trim(arguments.getString("type"));
        if (damageTypeId.isEmpty()) {
            damageTypeId = attributeService.defaultDamageTypeId();
        }
        // Both keys are supplied because module damage rules read one or the other; v1 did the same and
        // dropping either would silently change which rules match.
        Map<String, Object> damageContext = new LinkedHashMap<>();
        damageContext.put("damage_cause", cause.name());
        damageContext.put("cause", cause.name());
        damageContext.put("action_id", id());
        damageContext.put("damage_type_id", damageTypeId);
        boolean applied = attributeService.applyDamage(null, target, damageTypeId,
                arguments.getDouble("amount", 0D), damageContext);
        return applied
                ? CoreActionOutcome.success(Map.of("damage_type", damageTypeId))
                : CoreActionOutcome.failure(CoreActionFailureKind.REJECTED,
                        "action.v2.stage.attribute.damage_refused");
    }

    /**
     * Resolves a Bukkit damage cause.
     *
     * <p>A blank value means {@code CUSTOM}, matching v1. An unrecognised value returns {@code null} so the
     * caller can reject it rather than silently damaging with the wrong cause.</p>
     */
    private static EntityDamageEvent.DamageCause parseCause(String raw) {
        if (Texts.isBlank(raw)) {
            return EntityDamageEvent.DamageCause.CUSTOM;
        }
        try {
            return EntityDamageEvent.DamageCause.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static Player player(CoreActionSubject subject) {
        return subject != null && subject.entityOrNull() instanceof Player resolved ? resolved : null;
    }
}
