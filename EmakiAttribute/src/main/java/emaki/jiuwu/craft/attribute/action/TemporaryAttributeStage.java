package emaki.jiuwu.craft.attribute.action;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.attribute.service.AttributeServiceFacade;
import emaki.jiuwu.craft.attribute.service.TemporaryAttributeOutcome;
import emaki.jiuwu.craft.attribute.service.TemporaryAttributeService;
import emaki.jiuwu.craft.attribute.service.TemporaryAttributeService.TemporaryAttributeMode;
import emaki.jiuwu.craft.attribute.service.TemporaryAttributeStatus;
import emaki.jiuwu.craft.attribute.service.TemporaryEffectSource;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionTarget;
import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreStagePlanningContext;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class TemporaryAttributeStage implements CoreActionStage {

    public enum Operation {

        ADD("attribute_add", "向目标的一个效果组添加限时属性修饰符。"),

        SET("attribute_set", "在目标的一个效果组内设置限时属性修饰符。"),

        REMOVE("attribute_remove", "从目标移除整个限时效果组。");

        private final String id;
        private final String description;

        Operation(String id, String description) {
            this.id = id;
            this.description = description;
        }

        public String id() {
            return id;
        }
    }

    private final AttributeServiceFacade attributeService;
    private final Operation operation;

    public TemporaryAttributeStage(@NotNull AttributeServiceFacade attributeService,
            @NotNull Operation operation) {
        this.attributeService = attributeService;
        this.operation = operation;
    }

    @Override
    public @NotNull String id() {
        return operation.id;
    }

    @Override
    public @NotNull String description() {
        return operation.description;
    }

    @Override
    public @NotNull String category() {
        return "attribute";
    }

    @Override
    public @NotNull List<CoreStageParameter> parameters() {
        if (operation == Operation.REMOVE) {
            return List.of(CoreStageParameter.required("effect_id", CoreStageParameterType.STRING,
                    "要移除的效果组 ID，会清除该组内所有属性"));
        }
        return List.of(
                CoreStageParameter.required("effect_id", CoreStageParameterType.STRING,
                        "效果组 ID；复用同一 ID 可将多个属性归入同一组"),
                CoreStageParameter.required("attribute", CoreStageParameterType.STRING, "属性 ID"),
                CoreStageParameter.required("value", CoreStageParameterType.DOUBLE, "修饰符数值"),
                CoreStageParameter.required("duration_ticks", CoreStageParameterType.DURATION,
                        "修饰符持续时间"),
                CoreStageParameter.optional("stack_mode", CoreStageParameterType.STRING, "",
                        "与同一属性上已有效果的叠加方式: REPLACE 或 STACK"));
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
        if (attributeService == null || attributeService.temporaryAttributeService() == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.MISSING_CONTEXT,
                    "action.stage.attribute.service_unavailable");
        }
        LivingEntity target = target(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.stage.attribute.not_living_entity");
        }
        TemporaryAttributeService service = attributeService.temporaryAttributeService();
        String effectId = Texts.normalizeId(arguments.getString("effect_id"));
        if (effectId.isEmpty()) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.attribute.effect_id_required");
        }
        if (operation == Operation.REMOVE) {
            return outcome(service.removeGroup(target, effectId), effectId, "", 0D, 0L);
        }
        String attributeId = Texts.normalizeId(arguments.getString("attribute"));
        double value = arguments.getDouble("value", 0D);
        long durationTicks = arguments.getDurationTicks("duration_ticks", 0L);
        TemporaryAttributeMode mode = operation == Operation.ADD
                ? TemporaryAttributeMode.ADD
                : TemporaryAttributeMode.SET;
        TemporaryAttributeOutcome result = service.applyGroupEffect(target, effectId, attributeId, value,
                durationTicks, mode, arguments.getString("stack_mode", ""), TemporaryEffectSource.CORE_ACTION);
        return outcome(result, effectId, attributeId, value, durationTicks);
    }

    private static CoreActionOutcome outcome(TemporaryAttributeOutcome result,
            String effectId,
            String attributeId,
            double value,
            long durationTicks) {
        return switch (result.status()) {
            case APPLIED, REPLACED, STACKED, REMOVED ->
                    CoreActionOutcome.success(data(result, effectId, attributeId, value, durationTicks));
            case NOT_FOUND, NO_MATCH -> CoreActionOutcome.skipped(result.reasonKey());
            case UNKNOWN_ATTRIBUTE -> CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    result.reasonKey(), Map.of("attribute", attributeId));
            case INVALID_INPUT -> CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    result.reasonKey(), Map.of("detail", result.detail()));
            case WRONG_THREAD -> CoreActionOutcome.failure(CoreActionFailureKind.WRONG_THREAD, result.reasonKey());
            case CLOSED -> CoreActionOutcome.failure(CoreActionFailureKind.OWNER_DISABLED, result.reasonKey());
        };
    }

    private static Map<String, Object> data(TemporaryAttributeOutcome result,
            String effectId,
            String attributeId,
            double value,
            long durationTicks) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("effect_id", effectId);
        data.put("attribute", attributeId);
        data.put("value", value);
        data.put("duration_ticks", durationTicks);
        data.put("status", result.status().name());
        data.put("affected_count", result.affectedCount());
        data.put("existed", result.status() == TemporaryAttributeStatus.REPLACED
                || result.status() == TemporaryAttributeStatus.STACKED);
        data.put("remaining_ticks", result.remainingTicks());
        return Map.copyOf(data);
    }

    private static LivingEntity target(CoreActionSubject subject) {
        return subject != null && subject.entityOrNull() instanceof LivingEntity resolved ? resolved : null;
    }
}
