package emaki.jiuwu.craft.level.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.level.config.LevelTypeConfig;

public final class LevelCurveService {

    private static final int DEFAULT_RANGE = 80;
    private static final int MAX_POINTS_PER_TYPE = 300;
    private static final double SPIKE_GROWTH_RATE = 2.0D;
    private static final double DROP_GROWTH_RATE = -0.5D;

    private final LevelTypeRegistry typeRegistry;
    private final RequirementService requirementService;

    public LevelCurveService(LevelTypeRegistry typeRegistry, RequirementService requirementService) {
        this.typeRegistry = typeRegistry;
        this.requirementService = requirementService;
    }

    public Map<String, ?> curves(List<String> requestedTypes, int requestedFromLevel, int requestedToLevel) {
        List<LevelTypeConfig> types = resolveTypes(requestedTypes);
        List<Map<String, ?>> curves = new ArrayList<>();
        for (LevelTypeConfig type : types) {
            curves.add(curve(type, requestedFromLevel, requestedToLevel));
        }
        return Map.of(
                "curves", curves,
                "limits", Map.of("maxPointsPerType", MAX_POINTS_PER_TYPE),
                "warnings", List.of()
        );
    }

    private List<LevelTypeConfig> resolveTypes(List<String> requestedTypes) {
        if (requestedTypes != null && !requestedTypes.isEmpty()) {
            List<LevelTypeConfig> result = new ArrayList<>();
            for (String id : requestedTypes) {
                typeRegistry.type(id).ifPresent(result::add);
            }
            if (!result.isEmpty()) {
                return result;
            }
        }
        Collection<LevelTypeConfig> all = typeRegistry.all();
        return all.stream()
                .filter(LevelTypeConfig::enabled)
                .sorted(Comparator.comparing(LevelTypeConfig::primary).reversed().thenComparing(LevelTypeConfig::id))
                .toList();
    }

    private Map<String, ?> curve(LevelTypeConfig type, int requestedFromLevel, int requestedToLevel) {
        int minTarget = Math.max(type.startLevel() + 1, requestedFromLevel <= 0 ? type.startLevel() + 1 : requestedFromLevel);
        int maxTarget = requestedToLevel <= 0 ? Math.min(type.maxLevel(), minTarget + DEFAULT_RANGE - 1) : Math.min(type.maxLevel(), requestedToLevel);
        if (maxTarget < minTarget) {
            maxTarget = minTarget;
        }
        if (maxTarget - minTarget + 1 > MAX_POINTS_PER_TYPE) {
            maxTarget = minTarget + MAX_POINTS_PER_TYPE - 1;
        }
        List<Map<String, ?>> points = new ArrayList<>();
        List<Map<String, ?>> warnings = new ArrayList<>();
        double total = 0D;
        double previous = Double.NaN;
        int plateauCount = 1;
        for (int targetLevel = minTarget; targetLevel <= maxTarget; targetLevel++) {
            List<Map<String, ?>> pointWarnings = new ArrayList<>();
            double required;
            String source;
            try {
                required = requirementService.requiredExp(type, null, targetLevel);
                source = requirementService.debugSource(type, targetLevel);
            } catch (Exception exception) {
                required = 0D;
                source = "error";
                pointWarnings.add(warning("formula_error", "Requirement formula failed: " + exception.getMessage()));
            }
            if (required <= 0D) {
                pointWarnings.add(warning("non_positive", "Required exp is not positive."));
            }
            double growthRate = Double.isNaN(previous) || previous == 0D ? 0D : (required - previous) / previous;
            if (!Double.isNaN(previous)) {
                if (growthRate > SPIKE_GROWTH_RATE) {
                    pointWarnings.add(warning("spike", "Required exp grows by more than 200%."));
                } else if (growthRate < DROP_GROWTH_RATE) {
                    pointWarnings.add(warning("drop", "Required exp drops by more than 50%."));
                }
                plateauCount = Double.compare(required, previous) == 0 ? plateauCount + 1 : 1;
                if (plateauCount >= 3) {
                    pointWarnings.add(warning("plateau", "Required exp stays unchanged for at least 3 levels."));
                }
            }
            total += Math.max(0D, required);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("targetLevel", targetLevel);
            point.put("requiredExp", required);
            point.put("totalExp", total);
            point.put("growthRate", growthRate);
            point.put("source", source);
            point.put("warnings", pointWarnings);
            points.add(point);
            int warningTargetLevel = targetLevel;
            warnings.addAll(pointWarnings.stream()
                    .map(warning -> Map.of(
                            "targetLevel", warningTargetLevel,
                            "type", warning.get("type"),
                            "message", warning.get("message")
                    ))
                    .toList());
            previous = required;
        }
        Map<String, Object> curve = new LinkedHashMap<>();
        curve.put("type", type.id());
        curve.put("displayName", Texts.toStringSafe(type.displayName()));
        curve.put("startLevel", type.startLevel());
        curve.put("maxLevel", type.maxLevel());
        curve.put("fromLevel", minTarget);
        curve.put("toLevel", maxTarget);
        curve.put("points", points);
        curve.put("warnings", warnings);
        return curve;
    }

    private Map<String, String> warning(String type, String message) {
        return Map.of("type", type, "message", message);
    }
}
