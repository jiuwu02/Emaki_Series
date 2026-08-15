package emaki.jiuwu.craft.attribute.action;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.attribute.service.AttributeServiceFacade;
import emaki.jiuwu.craft.attribute.service.TemporaryAttributeService;
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

        ADD("attribute_add", "Adds a timed attribute modifier to the target."),

        SET("attribute_set", "Sets a timed attribute modifier on the target."),

        REMOVE("attribute_remove", "Removes a timed attribute modifier from the target.");

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
                    "Effect id to remove"));
        }
        return List.of(
                CoreStageParameter.required("effect_id", CoreStageParameterType.STRING, "Effect id"),
                CoreStageParameter.required("attribute", CoreStageParameterType.STRING, "Attribute id"),
                CoreStageParameter.required("value", CoreStageParameterType.DOUBLE, "Modifier value"),
                CoreStageParameter.required("duration_ticks", CoreStageParameterType.DURATION,
                        "How long the modifier lasts"));
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
        Player target = player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.stage.common.not_player");
        }
        TemporaryAttributeService service = attributeService.temporaryAttributeService();
        String effectId = Texts.normalizeId(arguments.getString("effect_id"));
        if (effectId.isEmpty()) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.attribute.effect_id_required");
        }
        if (operation == Operation.REMOVE) {
            TemporaryAttributeService.TemporaryAttributeResult result = service.remove(target, effectId);
            return CoreActionOutcome.success(data(result, effectId, "", 0D, 0L));
        }
        String attributeId = Texts.normalizeId(arguments.getString("attribute"));
        double value = arguments.getDouble("value", 0D);
        long durationTicks = arguments.getDurationTicks("duration_ticks", 0L);
        TemporaryAttributeService.TemporaryAttributeResult result = operation == Operation.ADD
                ? service.add(target, effectId, attributeId, value, durationTicks)
                : service.set(target, effectId, attributeId, value, durationTicks);
        return CoreActionOutcome.success(data(result, effectId, attributeId, value, durationTicks));
    }

    private static Map<String, Object> data(TemporaryAttributeService.TemporaryAttributeResult result,
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
        return Map.copyOf(data);
    }

    private static Player player(CoreActionSubject subject) {
        return subject != null && subject.entityOrNull() instanceof Player resolved ? resolved : null;
    }
}
