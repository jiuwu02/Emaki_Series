package emaki.jiuwu.craft.attribute.action;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.attribute.service.AttributeServiceFacade;
import emaki.jiuwu.craft.attribute.service.TemporaryAttributeOutcome;
import emaki.jiuwu.craft.attribute.service.TemporaryAttributeService;
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

public final class TemporaryAttributeTagStage implements CoreActionStage {

    public enum Operation {

        ADD("attribute_tag_add", "Adds every tagged attribute into one timed effect group on the target."),

        REMOVE("attribute_tag_remove", "Removes tagged timed attribute modifiers from the target."),

        CLEAR("attribute_tag_clear", "Clears tagged timed attribute modifiers on the target; equivalent to attribute_tag_remove.");

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

    public TemporaryAttributeTagStage(@NotNull AttributeServiceFacade attributeService,
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
        if (operation != Operation.ADD) {
            return List.of(CoreStageParameter.required("tag", CoreStageParameterType.STRING, "Attribute tag"));
        }
        return List.of(
                CoreStageParameter.required("tag", CoreStageParameterType.STRING, "Attribute tag"),
                CoreStageParameter.required("value", CoreStageParameterType.DOUBLE, "Modifier value"),
                CoreStageParameter.required("duration_ticks", CoreStageParameterType.DURATION,
                        "How long the modifiers last"),
                CoreStageParameter.optional("effect_prefix", CoreStageParameterType.STRING, "",
                        "Effect group id for every matched attribute; defaults to tag:<tag>"),
                CoreStageParameter.optional("stack_mode", CoreStageParameterType.STRING, "",
                        "How to combine with existing effects"));
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
        String tag = Texts.trim(arguments.getString("tag"));
        if (tag.isEmpty()) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.attribute.tag_required");
        }
        TemporaryAttributeService service = attributeService.temporaryAttributeService();
        TemporaryAttributeOutcome result = operation == Operation.ADD
                ? service.addGroupByTag(target,
                        arguments.getString("effect_prefix", ""),
                        tag,
                        arguments.getDouble("value", 0D),
                        arguments.getDurationTicks("duration_ticks", 0L),
                        arguments.getString("stack_mode", ""),
                        TemporaryEffectSource.CORE_ACTION)
                : service.removeGroupByTag(target, tag);
        return outcome(result, tag);
    }

    private static CoreActionOutcome outcome(TemporaryAttributeOutcome result, String tag) {
        return switch (result.status()) {
            case APPLIED, REPLACED, STACKED, REMOVED -> CoreActionOutcome.success(Map.of(
                    "tag", tag,
                    "effect_id", result.groupId(),
                    "status", result.status().name(),
                    "count", result.affectedCount()));
            case NOT_FOUND, NO_MATCH -> CoreActionOutcome.skipped(result.reasonKey());
            case UNKNOWN_ATTRIBUTE -> CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    result.reasonKey(), Map.of("tag", tag));
            case INVALID_INPUT -> CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    result.reasonKey(), Map.of("detail", result.detail()));
            case WRONG_THREAD -> CoreActionOutcome.failure(CoreActionFailureKind.WRONG_THREAD, result.reasonKey());
            case CLOSED -> CoreActionOutcome.failure(CoreActionFailureKind.OWNER_DISABLED, result.reasonKey());
        };
    }

    private static LivingEntity target(CoreActionSubject subject) {
        return subject != null && subject.entityOrNull() instanceof LivingEntity resolved ? resolved : null;
    }
}
