package emaki.jiuwu.craft.cooking.action.v2;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.model.NutritionOperationResult;
import emaki.jiuwu.craft.cooking.model.NutritionTypeConfig;
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
 * Returns the target's nutrition values to their minimum or configured default.
 *
 * <p>The v2 counterpart of {@code NutritionResetAction}. {@code clear} drops each value to its minimum while
 * {@code reset} restores the configured default, which is why they are distinct stages: one starves the
 * player, the other returns them to a neutral state.</p>
 *
 * <p>Domain {@code CONTEXT_ENTITY}: writes one player's nutrition record.</p>
 */
public final class NutritionResetStage implements CoreActionStage {

    /** Which reset variant a stage instance performs. */
    public enum Operation {

        /** Drop each value to its configured minimum. */
        CLEAR("cooking_clear_nutrition", "Drops the target's nutrition values to their minimum."),

        /** Restore each value to its configured default. */
        RESET("cooking_reset_nutrition", "Restores the target's nutrition values to their defaults.");

        private final String id;
        private final String description;

        Operation(String id, String description) {
            this.id = id;
            this.description = description;
        }

        /** {@return the pipeline stage id} */
        public String id() {
            return id;
        }
    }

    private final EmakiCookingPlugin plugin;
    private final Operation operation;

    /**
     * Creates a stage.
     *
     * @param plugin owning plugin, source of the nutrition services
     * @param operation which variant this instance performs
     */
    public NutritionResetStage(@NotNull EmakiCookingPlugin plugin, @NotNull Operation operation) {
        this.plugin = plugin;
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
        return "cooking";
    }

    @Override
    public @NotNull List<CoreStageParameter> parameters() {
        return List.of(CoreStageParameter.optional("type", CoreStageParameterType.STRING, "",
                "Nutrition type id; empty covers every registered type"));
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
        if (plugin.nutritionService() == null || plugin.nutritionTypeRegistry() == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.MISSING_CONTEXT,
                    "action.v2.stage.cooking.service_unavailable");
        }
        Player target = player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.v2.stage.common.not_player");
        }
        String requestedType = Texts.trim(arguments.getString("type"));
        List<NutritionTypeConfig> types;
        if (requestedType.isEmpty()) {
            types = List.copyOf(plugin.nutritionTypeRegistry().all());
        } else {
            NutritionTypeConfig type = plugin.nutritionTypeRegistry().type(requestedType).orElse(null);
            if (type == null) {
                return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                        "action.v2.stage.cooking.unknown_type", Map.of("type", requestedType));
            }
            types = List.of(type);
        }
        if (types.isEmpty()) {
            return CoreActionOutcome.skipped("action.v2.stage.cooking.no_types");
        }
        UUID targetId = target.getUniqueId();
        int changed = 0;
        Map<String, Object> values = new LinkedHashMap<>();
        for (NutritionTypeConfig type : types) {
            double targetValue = operation == Operation.CLEAR ? type.min() : type.defaultValue();
            NutritionOperationResult result = plugin.nutritionService().set(targetId, type.id(), targetValue);
            if (!result.success()) {
                // Stops at the first failure, as v1 did: continuing would leave the player's nutrition half
                // reset with no record of where it stopped.
                return CoreActionOutcome.failure(CoreActionFailureKind.REJECTED,
                        "action.v2.stage.cooking.reset_failed",
                        Map.of("type", type.id(), "reason", String.valueOf(result.reason())));
            }
            if (Double.compare(result.oldValue(), result.newValue()) != 0) {
                changed++;
            }
            values.put(type.id(), result.newValue());
        }
        return CoreActionOutcome.success(Map.of(
                "target", targetId.toString(),
                "operation", operation.name().toLowerCase(Locale.ROOT),
                "types", types.size(),
                "changed", changed,
                "values", Map.copyOf(values)));
    }

    private static Player player(CoreActionSubject subject) {
        return subject != null && subject.entityOrNull() instanceof Player resolved ? resolved : null;
    }
}
