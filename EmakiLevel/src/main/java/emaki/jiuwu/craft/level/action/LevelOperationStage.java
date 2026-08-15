package emaki.jiuwu.craft.level.action;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

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
import emaki.jiuwu.craft.level.EmakiLevelPlugin;
import emaki.jiuwu.craft.level.api.LevelOperationResult;
import emaki.jiuwu.craft.level.api.LevelOperationType;
import emaki.jiuwu.craft.level.api.LevelUpCause;

public final class LevelOperationStage implements CoreActionStage {

    private final EmakiLevelPlugin plugin;
    private final LevelOperationType operationType;
    private final String id;

    public LevelOperationStage(@NotNull EmakiLevelPlugin plugin,
            @NotNull LevelOperationType operationType,
            @NotNull String id) {
        this.plugin = plugin;
        this.operationType = operationType;
        this.id = id;
    }

    @Override
    public @NotNull String id() {
        return id;
    }

    @Override
    public @NotNull String description() {
        return switch (operationType) {
            case ADD_EXP -> "Adds experience to the target's level type.";
            case SET_EXP -> "Sets the target's experience for a level type.";
            case REMOVE_EXP -> "Removes experience from the target's level type.";
            case ADD_LEVEL -> "Adds levels to the target's level type.";
            case SET_LEVEL -> "Sets the target's level for a level type.";
            case REMOVE_LEVEL -> "Removes levels from the target's level type.";
            case RESET -> "Resets the target's progress for a level type.";
            case LEVEL_UP -> "Levels the target up once in a level type.";
            default -> "Modifies the target's level progress.";
        };
    }

    @Override
    public @NotNull String category() {
        return "level";
    }

    @Override
    public @NotNull List<CoreStageParameter> parameters() {
        List<CoreStageParameter> parameters = new ArrayList<>();
        parameters.add(CoreStageParameter.required("type", CoreStageParameterType.STRING, "Level type id"));

        parameters.add(CoreStageParameter.optional("amount", CoreStageParameterType.EXPRESSION, "0",
                "Amount, may be an arithmetic expression"));
        parameters.add(CoreStageParameter.optional("reason", CoreStageParameterType.STRING, "action",
                "Audit reason recorded with the change"));
        if (operationType == LevelOperationType.ADD_EXP) {
            parameters.add(CoreStageParameter.optional("auto_upgrade", CoreStageParameterType.BOOLEAN, "true",
                    "Level up automatically when the threshold is crossed"));
            parameters.add(CoreStageParameter.optional("silent", CoreStageParameterType.BOOLEAN, "false",
                    "Suppress the level-up feedback"));
        }
        return List.copyOf(parameters);
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
        if (plugin.levelService() == null || plugin.appConfig() == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.MISSING_CONTEXT,
                    "action.stage.level.service_unavailable");
        }
        Player target = player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.stage.common.not_player");
        }
        UUID targetId = target.getUniqueId();
        String type = arguments.getString("type", plugin.appConfig().primaryType());
        double amount = arguments.getExpression("amount", 0D);
        String reason = arguments.getString("reason", "action");
        LevelOperationResult result = apply(targetId, type, amount, reason, arguments);
        if (!result.success()) {
            return CoreActionOutcome.failure(CoreActionFailureKind.REJECTED,
                    "action.stage.level.operation_failed",
                    Map.of("reason", String.valueOf(result.reason())));
        }
        return CoreActionOutcome.success(Map.of(
                "type", result.typeId(),
                "old_level", result.oldLevel(),
                "new_level", result.newLevel(),
                "old_exp", result.oldExp(),
                "new_exp", result.newExp(),
                "amount", result.amount()));
    }

    private LevelOperationResult apply(UUID targetId,
            String type,
            double amount,
            String reason,
            CoreResolvedArguments arguments) {
        return switch (operationType) {
            case ADD_EXP -> plugin.levelService().addExp(targetId, type, amount, reason,
                    arguments.getBoolean("auto_upgrade", true),
                    arguments.getBoolean("silent", false));
            case SET_EXP -> plugin.levelService().setExp(targetId, type, amount, reason);
            case REMOVE_EXP -> plugin.levelService().removeExp(targetId, type, amount, reason);
            case ADD_LEVEL -> plugin.levelService().addLevel(targetId, type, rounded(amount), reason);
            case SET_LEVEL -> plugin.levelService().setLevel(targetId, type, rounded(amount), reason);
            case REMOVE_LEVEL -> plugin.levelService().removeLevel(targetId, type, rounded(amount), reason);
            case RESET -> plugin.levelService().reset(targetId, type);
            case LEVEL_UP -> plugin.levelService().levelUp(targetId, type, LevelUpCause.ACTION);
            default -> LevelOperationResult.failure("unsupported_operation", operationType, type);
        };
    }

    private static int rounded(double amount) {
        return (int) Math.round(amount);
    }

    private static Player player(CoreActionSubject subject) {
        return subject != null && subject.entityOrNull() instanceof Player resolved ? resolved : null;
    }
}
