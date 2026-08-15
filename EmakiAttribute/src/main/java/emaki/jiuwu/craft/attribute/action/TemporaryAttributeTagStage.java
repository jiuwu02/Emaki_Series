package emaki.jiuwu.craft.attribute.action;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.attribute.model.TemporaryStackMode;
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

public final class TemporaryAttributeTagStage implements CoreActionStage {

    public enum Operation {

        ADD("attribute_tag_add", "Adds timed attribute modifiers to the target by tag."),

        REMOVE("attribute_tag_remove", "Removes timed attribute modifiers from the target by tag."),

        CLEAR("attribute_tag_clear", "Clears timed attribute modifiers on the target by tag.");

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
                        "Prefix for the generated effect ids"),
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
        Player target = player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.stage.common.not_player");
        }
        String tag = Texts.trim(arguments.getString("tag"));
        if (tag.isEmpty()) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.attribute.tag_required");
        }
        TemporaryAttributeService service = attributeService.temporaryAttributeService();
        int count = operation == Operation.ADD
                ? service.addByTag(target,
                        arguments.getString("effect_prefix", ""),
                        tag,
                        arguments.getDouble("value", 0D),
                        arguments.getDurationTicks("duration_ticks", 0L),
                        stackMode(arguments.getString("stack_mode", "")))
                : service.removeByTag(target, tag);
        return CoreActionOutcome.success(Map.of("tag", tag, "count", count));
    }

    private static TemporaryStackMode stackMode(String value) {
        if (Texts.isBlank(value)) {
            return null;
        }
        try {
            return TemporaryStackMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static Player player(CoreActionSubject subject) {
        return subject != null && subject.entityOrNull() instanceof Player resolved ? resolved : null;
    }
}
