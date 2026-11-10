package emaki.jiuwu.craft.strengthen.api.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class StrengthenRecipeDelegate {

    private final StrengthenRecipe ownerRef;

    StrengthenRecipeDelegate(StrengthenRecipe ownerRef) {
        this.ownerRef = ownerRef;
    }

    String id() {
        return ownerRef.id;
    }

    String displayName() {
        return ownerRef.displayName;
    }

    String guiTemplate() {
        return ownerRef.guiTemplate;
    }

    StrengthenRecipe.EconomyConfig economy() {
        return ownerRef.economy;
    }

    StrengthenRecipe.Limits limits() {
        return ownerRef.limits;
    }

    Map<Integer, Double> successRates() {
        return ownerRef.successRates;
    }

    List<String> slotGroups() {
        return ownerRef.slotGroups;
    }

    List<String> statsAny() {
        return ownerRef.statsAny;
    }

    List<String> sourcePatterns() {
        return ownerRef.sourcePatterns;
    }

    Map<String, StrengthenRecipe.StatLineDefinition> statLines() {
        return ownerRef.statLines;
    }

    Map<Integer, StrengthenRecipe.StarStage> stars() {
        return ownerRef.stars;
    }

    StrengthenConditionGroup conditions() {
        return ownerRef.conditions;
    }

    String conditionType() {
        return ownerRef.conditionType;
    }

    Object matcherConfig() {
        return ownerRef.matcherConfig;
    }

    static Map<String, Double> resolveExpressions(Map<String, Object> rawValues, Map<String, ?> context) {
        Map<String, Double> resolved = new LinkedHashMap<>();
        if (rawValues == null || rawValues.isEmpty()) {
            return resolved;
        }
        Map<String, Object> evalContext = new LinkedHashMap<>();
        if (context != null) {
            evalContext.putAll(context);
        }
        for (Map.Entry<String, Object> entry : rawValues.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String key = StrengthenApiValues.lower(entry.getKey());
            double value = StrengthenRecipeSupport.resolveNumericValue(entry.getValue(), evalContext);
            resolved.put(key, value);
            evalContext.put(key, value);
        }
        return resolved;
    }

    static Map<String, Object> resolveMixedExpressions(Map<String, Object> rawValues, Map<String, ?> context) {
        if (rawValues == null || rawValues.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> resolved = new LinkedHashMap<>();
        Map<String, Object> evalContext = new LinkedHashMap<>();
        if (context != null) {
            evalContext.putAll(context);
        }
        for (Map.Entry<String, Object> entry : rawValues.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String key = StrengthenApiValues.lower(entry.getKey());
            Object value = StrengthenApiValues.evaluateMixed(entry.getValue(), evalContext);
            resolved.put(key, value);
            evalContext.put(key, value);
        }
        return Map.copyOf(resolved);
    }

    static List<String> normalizeLower(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String normalized = StrengthenApiValues.lower(value);
            if (StrengthenApiValues.isNotBlank(normalized)) {
                result.add(normalized);
            }
        }
        return List.copyOf(result);
    }

    static List<String> normalizeStripped(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String normalized = StrengthenApiValues.stripMiniTags(value);
            if (StrengthenApiValues.isNotBlank(normalized)) {
                result.add(normalized);
            }
        }
        return List.copyOf(result);
    }

    static List<String> normalizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String normalized = StrengthenApiValues.toStringSafe(value);
            if (StrengthenApiValues.isNotBlank(normalized)) {
                result.add(normalized);
            }
        }
        return List.copyOf(result);
    }

}
