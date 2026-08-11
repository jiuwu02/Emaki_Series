package emaki.jiuwu.craft.cooking.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.cooking.model.CookingInputIngredient;
import emaki.jiuwu.craft.cooking.model.RecipeDocument;
import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;


/**
 * A completion submission. {@code conditionOutcome} carries an already-evaluated recipe completion
 * condition so the pipeline does not evaluate it a second time; see {@link ConditionOutcome}.
 */
record CookingCompletionRequest(
        String discriminator,
        StationType stationType,
        StationCoordinates coordinates,
        Map<String, Object> expectedState,
        CookingCompletionOperation.CommitMode commitMode,
        Map<String, Object> committedState,
        RecipeDocument recipe,
        Player player,
        Location rewardLocation,
        boolean dropResult,
        List<CookingInputIngredient> inputs,
        List<Map<String, Object>> outputs,
        List<String> actions,
        String phase,
        Map<String, ?> placeholders,
        List<PlayerInventoryInput> playerInputs,
        ConditionOutcome conditionOutcome) {

    /**
     * A completion condition that the caller already evaluated.
     *
     * <p>Callers that gate the submission on the condition themselves (so a blocked condition never
     * consumes inputs or commits state) pass their result here. The pipeline then reuses
     * {@code passed} instead of re-evaluating, which keeps the condition a single observation per
     * completion. {@code null} means "not pre-evaluated" and the pipeline evaluates it as before.
     *
     * @param passed whether the condition evaluated to true
     */
    record ConditionOutcome(boolean passed) {

        static final ConditionOutcome PASSED = new ConditionOutcome(true);

        static ConditionOutcome of(boolean passed) {
            return passed ? PASSED : new ConditionOutcome(false);
        }
    }

    CookingCompletionRequest {
        discriminator = discriminator == null ? "" : discriminator.trim();
        expectedState = immutableMap(expectedState);
        commitMode = commitMode == null ? CookingCompletionOperation.CommitMode.DELETE : commitMode;
        committedState = commitMode == CookingCompletionOperation.CommitMode.DELETE
                ? Map.of()
                : immutableMap(committedState);
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        outputs = immutableMapList(outputs);
        actions = actions == null ? List.of() : List.copyOf(actions);
        placeholders = placeholders == null ? Map.of() : immutableMap(placeholders);
        playerInputs = playerInputs == null ? List.of() : List.copyOf(playerInputs);
        rewardLocation = rewardLocation == null ? null : rewardLocation.clone();
    }

    String completionKey() {
        String station = stationType == null ? "unknown" : stationType.folderName();
        String location = coordinates == null ? "unknown" : coordinates.runtimeKey();
        return CookingCompletionStateDigest.sha256(Map.of(
                "station", station,
                "location", location,
                "discriminator", discriminator,
                "expected_state", expectedState
        ));
    }

    String operationId() {
        return completionKey();
    }

    record PlayerInventoryInput(Map<String, Object> item, int amount, String description) {

        PlayerInventoryInput {
            item = immutableMap(item);
            amount = Math.max(1, amount);
            description = description == null ? "" : description;
        }

        static PlayerInventoryInput mainHand(Player player, int amount, String description) {
            if (player == null) {
                return null;
            }
            ItemStack current = player.getInventory().getItemInMainHand();
            if (current == null || current.getType().isAir() || current.getAmount() < Math.max(1, amount)) {
                return null;
            }
            ItemStack template = current.clone();
            template.setAmount(1);
            return new PlayerInventoryInput(StoredItemCodec.serialize(template), amount, description);
        }
    }

    private static List<Map<String, Object>> immutableMapList(List<Map<String, Object>> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> value : values) {
            if (value != null && !value.isEmpty()) {
                result.add(immutableMap(value));
            }
        }
        return List.copyOf(result);
    }

    private static Map<String, Object> immutableMap(Map<?, ?> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Object plain = ConfigNodes.toPlainData(values);
        if (!(plain instanceof Map<?, ?> map)) {
            return Map.of();
        }
        return Map.copyOf(new LinkedHashMap<>(MapYamlSection.normalizeMap(map)));
    }
}
