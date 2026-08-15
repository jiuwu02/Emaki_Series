package emaki.jiuwu.craft.station.recipe;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import emaki.jiuwu.craft.corelib.condition.ConditionBlock;
import emaki.jiuwu.craft.station.api.model.MaterialRequirementView;
import emaki.jiuwu.craft.station.api.model.PendingOutput;
import emaki.jiuwu.craft.station.api.model.RecipeView;

public record RecipeDefinition(String id,
        String displayName,
        Set<String> tags,
        Set<String> stationIds,
        List<MaterialRequirement> requirements,
        long durationSeconds,
        List<RecipeOutput> outputs,
        List<String> resultActions,
        String permission,
        ConditionBlock condition,
        List<String> preActions,
        List<String> successActions,
        List<String> failureActions,
        RecipeCost cost,
        boolean visible,
        ConditionBlock displayCondition) {

    public RecipeDefinition {
        displayName = displayName == null ? id : displayName;
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        stationIds = stationIds == null ? Set.of() : Set.copyOf(stationIds);
        requirements = requirements == null ? List.of() : List.copyOf(requirements);
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
        resultActions = resultActions == null ? List.of() : List.copyOf(resultActions);
        permission = permission == null ? "" : permission;
        condition = condition == null ? ConditionBlock.empty() : condition;
        preActions = preActions == null ? List.of() : List.copyOf(preActions);
        successActions = successActions == null ? List.of() : List.copyOf(successActions);
        failureActions = failureActions == null ? List.of() : List.copyOf(failureActions);
        durationSeconds = Math.max(0L, durationSeconds);
        cost = cost == null ? RecipeCost.none() : cost;
        displayCondition = displayCondition == null ? ConditionBlock.empty() : displayCondition;
    }

    public boolean belongsTo(String stationId) {
        if (stationIds.isEmpty()) {
            return true;
        }
        return stationId != null
                && stationIds.contains(stationId.trim().toLowerCase(Locale.ROOT));
    }

    public boolean instant() {
        return durationSeconds <= 0L;
    }

    public boolean hasPermission() {
        return !permission.isBlank();
    }

    public boolean hasDisplayCondition() {
        return displayCondition.configured();
    }

    public long effectiveDurationMillis(double speedMultiplier) {
        if (instant()) {
            return 0L;
        }
        double multiplier = speedMultiplier <= 0.0D ? 1.0D : speedMultiplier;
        double millis = durationSeconds * 1_000.0D * multiplier;
        if (millis >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, (long) millis);
    }

    public RecipeView toView() {
        List<MaterialRequirementView> requirementViews = requirements.stream()
                .map(MaterialRequirement::toView)
                .toList();
        List<PendingOutput> outputViews = outputs.stream()
                .map(output -> new PendingOutput(output.source(), output.amount()))
                .toList();
        return new RecipeView(id, displayName, List.copyOf(tags), requirementViews,
                durationSeconds, outputViews, permission,
                cost.providerId(), cost.amount(), visible, hasDisplayCondition());
    }
}
