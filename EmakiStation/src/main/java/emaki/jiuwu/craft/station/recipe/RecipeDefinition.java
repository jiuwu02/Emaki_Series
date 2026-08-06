package emaki.jiuwu.craft.station.recipe;

import java.util.List;
import java.util.Set;

import emaki.jiuwu.craft.corelib.condition.ConditionBlock;
import emaki.jiuwu.craft.station.api.model.MaterialRequirementView;
import emaki.jiuwu.craft.station.api.model.PendingOutput;
import emaki.jiuwu.craft.station.api.model.RecipeView;

/**
 * One loaded recipe.
 *
 * @param id               the recipe id, unique across the recipe directory
 * @param displayName      the configured display name, unrendered
 * @param tags             lower-cased tags used by station include/exclude rules
 * @param requirements     the material requirements, matched as an unordered set
 * @param durationSeconds  how long one submission takes; zero settles immediately
 * @param outputs          what one batch produces
 * @param resultActions    action lines run once the craft settles
 * @param permission       the permission required to see and use it, or an empty string
 * @param condition        the gate evaluated before submission
 * @param preActions       action lines run before consumption
 * @param successActions   action lines run after a successful settle
 * @param failureActions   action lines run when a submission is refused
 * @param cost             the currency charged per batch
 * @param visible          whether this recipe appears in a station catalog at all
 * @param displayCondition the gate deciding whether the catalog entry is unlocked
 */
public record RecipeDefinition(String id,
        String displayName,
        Set<String> tags,
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

    /**
     * Creates a recipe with defensively copied collections.
     *
     * @param id               the recipe id
     * @param displayName      the display name; {@code null} becomes the id
     * @param tags             the tags; {@code null} becomes empty
     * @param requirements     the material requirements; {@code null} becomes empty
     * @param durationSeconds  the craft duration; negatives are clamped to zero
     * @param outputs          the produced items; {@code null} becomes empty
     * @param resultActions    post-settle action lines; {@code null} becomes empty
     * @param permission       the required permission; {@code null} becomes an empty string
     * @param condition        the submission gate; {@code null} becomes an empty block
     * @param preActions       pre-consumption action lines; {@code null} becomes empty
     * @param successActions   post-success action lines; {@code null} becomes empty
     * @param failureActions   post-refusal action lines; {@code null} becomes empty
     * @param cost             the per-batch currency charge; {@code null} becomes no charge
     * @param visible          whether the recipe appears in a catalog
     * @param displayCondition the unlock gate; {@code null} becomes an empty block
     */
    public RecipeDefinition {
        displayName = displayName == null ? id : displayName;
        tags = tags == null ? Set.of() : Set.copyOf(tags);
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

    /** {@return whether this recipe settles immediately instead of entering a timed queue} */
    public boolean instant() {
        return durationSeconds <= 0L;
    }

    /** {@return whether this recipe restricts use behind its own permission node} */
    public boolean hasPermission() {
        return !permission.isBlank();
    }

    /** {@return whether this recipe gates its catalog entry behind a display condition} */
    public boolean hasDisplayCondition() {
        return displayCondition.configured();
    }

    /**
     * Computes this recipe's duration under a station's speed multiplier.
     *
     * @param speedMultiplier the station multiplier; non-positive values are treated as 1
     * @return the effective duration in milliseconds
     */
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

    /** {@return an API view of this recipe} */
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
