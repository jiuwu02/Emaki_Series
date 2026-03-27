package emaki.jiuwu.craft.attribute.service;

import emaki.jiuwu.craft.attribute.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.model.DamageRequest;
import emaki.jiuwu.craft.attribute.model.DamageResult;
import emaki.jiuwu.craft.attribute.model.DamageStageDefinition;
import emaki.jiuwu.craft.attribute.model.DamageStageKind;
import emaki.jiuwu.craft.attribute.model.DamageStageMode;
import emaki.jiuwu.craft.attribute.model.DamageStageSource;
import emaki.jiuwu.craft.attribute.model.DamageTypeDefinition;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class DamageEngine {

    public DamageResult resolve(DamageRequest request, DamageTypeDefinition definition) {
        return resolve(request, definition, ThreadLocalRandom.current().nextDouble() * 100D);
    }

    public DamageResult resolve(DamageRequest request, DamageTypeDefinition definition, double seededRoll) {
        if (request == null || definition == null) {
            return new DamageResult("", 0D, false, 0D, Map.of(), Map.of());
        }
        double current = Math.max(0D, request.baseDamage());
        Map<String, Double> stageValues = new LinkedHashMap<>();
        boolean critical = false;
        double roll = seededRoll;
        for (DamageStageDefinition stage : definition.stages()) {
            if (stage == null) {
                continue;
            }
            StageInputs inputs = gatherInputs(request, stage, roll);
            double next = switch (stage.kind()) {
                case FLAT_PERCENT -> applyFlatPercent(current, inputs, stage);
                case CUSTOM -> applyCustom(current, inputs, stage, request.context(), roll);
            };
            if (stage.kind() == DamageStageKind.CUSTOM) {
                critical = critical || inputs.crit;
            }
            current = next;
            stageValues.put(stage.id(), current);
        }
        return new DamageResult(definition.id(), Math.max(0D, current), critical, roll, stageValues, request.context());
    }

    private double applyFlatPercent(double input, StageInputs inputs, DamageStageDefinition stage) {
        double flat = inputs.flat;
        double percent = inputs.percent;
        double result;
        if (stage.mode() == DamageStageMode.SUBTRACT) {
            result = Math.max(0D, (input - flat) * Math.max(0D, 1D - (percent / 100D)));
        } else {
            result = (input + flat) * (1D + (percent / 100D));
        }
        return clampResult(result, stage);
    }

    private double applyCustom(double input,
                               StageInputs inputs,
                               DamageStageDefinition stage,
                               Map<String, Object> context,
                               double roll) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("input", input);
        variables.put("base", input);
        variables.put("flat", inputs.flat);
        variables.put("percent", inputs.percent);
        variables.put("chance", inputs.chance);
        variables.put("multiplier", inputs.multiplier);
        variables.put("roll", roll);
        variables.put("crit", inputs.crit ? 1D : 0D);
        if (context != null) {
            variables.putAll(context);
        }
        double result;
        if (Texts.isBlank(stage.expression())) {
            if (inputs.chance > 0D || !stage.chanceAttributes().isEmpty()) {
                result = input * (1D + ((inputs.crit ? inputs.multiplier : 0D) / 100D));
            } else {
                result = input;
            }
        } else {
            result = ExpressionEngine.evaluate(stage.expression(), variables);
        }
        return clampResult(result, stage);
    }

    private double clampResult(double value, DamageStageDefinition stage) {
        double result = value;
        if (stage.minResult() != null) {
            result = Math.max(result, stage.minResult());
        }
        if (stage.maxResult() != null) {
            result = Math.min(result, stage.maxResult());
        }
        return Math.max(0D, result);
    }

    private StageInputs gatherInputs(DamageRequest request, DamageStageDefinition stage, double roll) {
        AttributeSnapshot sourceSnapshot = switch (stage.source()) {
            case ATTACKER -> request.attackerSnapshot();
            case TARGET -> request.targetSnapshot();
            case CONTEXT -> null;
        };
        Map<String, Object> context = request.context();
        double flat = sum(sourceSnapshot, context, stage.flatAttributes());
        double percent = sum(sourceSnapshot, context, stage.percentAttributes());
        double chance = clamp(sum(sourceSnapshot, context, stage.chanceAttributes()), stage.minChance(), stage.maxChance(), 0D, 100D);
        double multiplier = clamp(sum(sourceSnapshot, context, stage.multiplierAttributes()), stage.minMultiplier(), stage.maxMultiplier(), -100D, 100000D);
        boolean crit = chance > 0D && roll <= chance;
        return new StageInputs(flat, percent, chance, multiplier, crit);
    }

    private double sum(AttributeSnapshot snapshot, Map<String, Object> context, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0D;
        }
        double total = 0D;
        for (String id : ids) {
            if (Texts.isBlank(id)) {
                continue;
            }
            String normalized = id.trim().toLowerCase(Locale.ROOT);
            Double value = snapshot == null ? null : snapshot.values().get(normalized);
            if (value == null && context != null) {
                Object raw = context.get(normalized);
                value = Numbers.tryParseDouble(raw, null);
            }
            if (value != null) {
                total += value;
            }
        }
        return total;
    }

    private double clamp(double value, Double min, Double max, double fallbackMin, double fallbackMax) {
        double result = value;
        double effectiveMin = min == null ? fallbackMin : min;
        double effectiveMax = max == null ? fallbackMax : max;
        if (effectiveMin > effectiveMax) {
            double swap = effectiveMin;
            effectiveMin = effectiveMax;
            effectiveMax = swap;
        }
        result = Math.max(result, effectiveMin);
        result = Math.min(result, effectiveMax);
        return result;
    }
    private record StageInputs(double flat, double percent, double chance, double multiplier, boolean crit) {
    }
}
