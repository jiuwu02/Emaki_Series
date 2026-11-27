package emaki.jiuwu.craft.strengthen.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.assembly.EmakiItemLayerSnapshot;
import emaki.jiuwu.craft.corelib.api.assembly.EmakiStatContribution;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenRecipe;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenState;

public final class StrengthenSnapshotBuilder {

    private static final String NAMESPACE_ID = "strengthen";
    private static final int STATS_PRIMARY_ORDER = 210;
    private static final int EFFECTS_ORDER = 230;
    private static final int STATS_SECONDARY_ORDER = 240;

    public EmakiItemLayerSnapshot buildLayerSnapshot(StrengthenRecipe recipe,
            StrengthenState state,
            String materialsSignature) {
        if (recipe == null || state == null) {
            return null;
        }
        Map<String, Double> stats = recipe.cumulativeVariables(state.currentStar(), state.branchPath());
        if (state.fractureLevel() > 0) {
            stats = applyFractureDecay(stats, state.fractureLevel());
        }
        return new EmakiItemLayerSnapshot(
                NAMESPACE_ID,
                1,
                buildAudit(recipe, state, materialsSignature),
                buildStatContributions(stats),
                null
        );
    }

    private Map<String, Object> buildAudit(StrengthenRecipe recipe, StrengthenState state, String materialsSignature) {
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("recipe_id", recipe.id());
        audit.put("current_star", state.currentStar());
        audit.put("crack_level", state.crackLevel());
        audit.put("temper", state.temperLevel());
        audit.put("star", state.currentStar());
        audit.put("max_temper", recipe.limits().maxTemper());
        audit.put("success_count", state.successCount());
        audit.put("failure_count", state.failureCount());
        audit.put("first_reach_flags", new ArrayList<>(state.firstReachFlags()));
        audit.put("last_attempt_at", state.lastAttemptAt());
        audit.put("materials_signature", Texts.toStringSafe(materialsSignature));
        audit.put("base_source_signature", state.baseSourceSignature());
        audit.put("branch_path", state.branchPath());
        audit.put("fracture_level", state.fractureLevel());
        return audit;
    }

    private List<EmakiStatContribution> buildStatContributions(Map<String, Double> stats) {
        List<EmakiStatContribution> result = new ArrayList<>();
        int sequence = 0;
        for (Map.Entry<String, Double> entry : stats.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || Math.abs(entry.getValue()) <= 1.0E-9D) {
                continue;
            }
            result.add(new EmakiStatContribution(entry.getKey(), entry.getValue(), NAMESPACE_ID + ":" + entry.getKey(), sequence++));
        }
        return result;
    }

    private Map<String, Double> applyFractureDecay(Map<String, Double> stats, int fractureLevel) {
        if (stats == null || stats.isEmpty() || fractureLevel <= 0) {
            return stats;
        }
        double decayFactor = Math.max(0D, 1D - fractureLevel * FRACTURE_PENALTY_RATE);
        Map<String, Double> decayed = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : stats.entrySet()) {
            decayed.put(entry.getKey(), entry.getValue() * decayFactor);
        }
        return decayed;
    }

    private static final double FRACTURE_PENALTY_RATE = 0.05D;
}
