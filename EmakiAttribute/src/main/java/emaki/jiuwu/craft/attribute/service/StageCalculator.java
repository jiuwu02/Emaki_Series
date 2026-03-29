package emaki.jiuwu.craft.attribute.service;

import emaki.jiuwu.craft.attribute.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.model.DamageContext;
import emaki.jiuwu.craft.attribute.model.DamageContextVariables;
import emaki.jiuwu.craft.attribute.model.DamageRequest;
import emaki.jiuwu.craft.attribute.model.DamageStageDefinition;
import emaki.jiuwu.craft.attribute.model.DamageStageKind;
import emaki.jiuwu.craft.attribute.model.DamageStageMode;
import emaki.jiuwu.craft.attribute.model.DamageStageSource;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.LinkedHashMap;
import java.util.Map;

final class StageCalculator {

    StageOutcome calculate(double input,
                           DamageRequest request,
                           DamageStageDefinition stage,
                           double roll,
                           DamageCalculationCache calculationCache) {
        StageInputs inputs = gatherInputs(request, stage, roll, calculationCache);
        DamageContextVariables context = request == null || request.damageContext() == null
            ? DamageContextVariables.empty()
            : request.damageContext().variables();
        double next = switch (stage.kind()) {
            case FLAT_PERCENT -> applyFlatPercent(input, inputs, stage);
            case CUSTOM -> applyCustom(input, inputs, stage, context, roll);
        };
        return new StageOutcome(next, stage.kind() == DamageStageKind.CUSTOM && inputs.critical());
    }

    private double applyFlatPercent(double input, StageInputs inputs, DamageStageDefinition stage) {
        double result = stage.mode() == DamageStageMode.SUBTRACT
            ? Math.max(0D, (input - inputs.flat()) * Math.max(0D, 1D - (inputs.percent() / 100D)))
            : (input + inputs.flat()) * (1D + (inputs.percent() / 100D));
        return clampResult(result, stage);
    }

    private double applyCustom(double input,
                               StageInputs inputs,
                               DamageStageDefinition stage,
                               DamageContextVariables context,
                               double roll) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("input", input);
        variables.put("base", input);
        variables.put("flat", inputs.flat());
        variables.put("percent", inputs.percent());
        variables.put("chance", inputs.chance());
        variables.put("multiplier", inputs.multiplier());
        variables.put("roll", roll);
        variables.put("crit", inputs.critical() ? 1D : 0D);
        if (context != null) {
            variables.putAll(context.asMap());
        }
        double result = Texts.isBlank(stage.expression())
            ? defaultCustomResult(input, inputs, stage)
            : ExpressionEngine.evaluate(stage.expression(), variables);
        return clampResult(result, stage);
    }

    private double defaultCustomResult(double input, StageInputs inputs, DamageStageDefinition stage) {
        if (inputs.chance() <= 0D && stage.chanceAttributes().isEmpty()) {
            return input;
        }
        return input * (1D + ((inputs.critical() ? inputs.multiplier() : 0D) / 100D));
    }

    private StageInputs gatherInputs(DamageRequest request,
                                     DamageStageDefinition stage,
                                     double roll,
                                     DamageCalculationCache calculationCache) {
        DamageContext damageContext = request == null ? null : request.damageContext();
        AttributeSnapshot sourceSnapshot = resolveSourceSnapshot(damageContext, stage.source());
        DamageContextVariables context = damageContext == null ? DamageContextVariables.empty() : damageContext.variables();
        double flat = calculationCache.sum(sourceSnapshot, context, stage.flatAttributes());
        double percent = calculationCache.sum(sourceSnapshot, context, stage.percentAttributes());
        double chance = clamp(
            calculationCache.sum(sourceSnapshot, context, stage.chanceAttributes()),
            stage.minChance(),
            stage.maxChance(),
            0D,
            100D
        );
        double multiplier = clamp(
            calculationCache.sum(sourceSnapshot, context, stage.multiplierAttributes()),
            stage.minMultiplier(),
            stage.maxMultiplier(),
            -100D,
            100000D
        );
        return new StageInputs(flat, percent, chance, multiplier, chance > 0D && roll <= chance);
    }

    private AttributeSnapshot resolveSourceSnapshot(DamageContext damageContext, DamageStageSource source) {
        if (damageContext == null || source == null) {
            return null;
        }
        return switch (source) {
            case ATTACKER -> damageContext.attackerSnapshot();
            case TARGET -> damageContext.targetSnapshot();
            case CONTEXT -> null;
        };
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

    private double clamp(double value, Double min, Double max, double fallbackMin, double fallbackMax) {
        double effectiveMin = min == null ? fallbackMin : min;
        double effectiveMax = max == null ? fallbackMax : max;
        if (effectiveMin > effectiveMax) {
            double swap = effectiveMin;
            effectiveMin = effectiveMax;
            effectiveMax = swap;
        }
        return Math.min(Math.max(value, effectiveMin), effectiveMax);
    }

    record StageOutcome(double value, boolean critical) {
    }

    private record StageInputs(double flat, double percent, double chance, double multiplier, boolean critical) {
    }
}
