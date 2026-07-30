package emaki.jiuwu.craft.attribute.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.attribute.api.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.api.model.DamageContext;
import emaki.jiuwu.craft.attribute.api.model.DamageContextVariables;
import emaki.jiuwu.craft.attribute.model.DamageRequest;
import emaki.jiuwu.craft.attribute.model.DamageStageDefinition;
import emaki.jiuwu.craft.attribute.model.DamageStageKind;
import emaki.jiuwu.craft.attribute.model.DamageStageMode;
import emaki.jiuwu.craft.attribute.model.DamageStageSource;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.text.Texts;

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
            case FLAT_PERCENT ->
                applyFlatPercent(input, inputs, stage);
            case CUSTOM ->
                applyCustom(input, inputs, stage, context, roll);
        };
        return new StageOutcome(next, stage.kind() == DamageStageKind.CUSTOM && inputs.critical());
    }

    private double applyFlatPercent(double input, StageInputs inputs, DamageStageDefinition stage) {
        double result;
        if (stage.mode() == DamageStageMode.SUBTRACT) {
            result = Math.max(0D, (input - inputs.flat()) * Math.max(0D, 1D - (inputs.percent() / 100D)));
        } else if (inputs.fusedFlat()) {
            double factor = AttributeFusionMath.percentFactor(inputs.percent(), false);
            result = Math.max(0D, (input * factor) + inputs.flat());
        } else {
            result = (input + inputs.flat()) * (1D + (inputs.percent() / 100D));
        }
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
        // 兜底：不带目标实体的伤害请求（公开 API 直接构造 DamageRequest）没有格挡上下文，
        // 缺失变量会让引用它的表达式整体求值失败并把结果压成 0。真实值随后由上下文覆盖。
        variables.put(ShieldBlockResolver.TARGET_BLOCKING, 0D);
        if (context != null) {
            variables.putAll(context.asMap());
        }
        if (!stage.variables().isEmpty()) {
            variables.putAll(stage.variables());
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
        double flatRoll = clampedRoll(context, "damage_roll_flat", context.getDouble("damage_roll", 0D));
        double percentRoll = clampedRoll(context, "damage_roll_percent", flatRoll);
        double chanceRoll = clampedRoll(context, "damage_roll_chance", flatRoll);
        double multiplierRoll = clampedRoll(context, "damage_roll_multiplier", flatRoll);
        double flat = rolledSum(sourceSnapshot, context, stage.flatAttributes(), stage.flatAttributesSignature(), flatRoll, calculationCache);
        double percent = rolledSum(sourceSnapshot, context, stage.percentAttributes(), stage.percentAttributesSignature(), percentRoll, calculationCache);
        boolean fusedFlat = stage.kind() == DamageStageKind.FLAT_PERCENT
                && stage.mode() == DamageStageMode.ADD
                && !stage.flatAttributes().isEmpty()
                && !stage.percentAttributes().isEmpty()
                && AttributeFusionMath.usesFusedCombatValues(sourceSnapshot);
        double chance = clamp(
                rolledSum(sourceSnapshot, context, stage.chanceAttributes(), stage.chanceAttributesSignature(), chanceRoll, calculationCache),
                stage.minChance(),
                stage.maxChance(),
                0D,
                100D
        );
        double multiplier = clamp(
                rolledSum(sourceSnapshot, context, stage.multiplierAttributes(), stage.multiplierAttributesSignature(), multiplierRoll, calculationCache),
                stage.minMultiplier(),
                stage.maxMultiplier(),
                -100D,
                100000D
        );
        return new StageInputs(flat, percent, chance, multiplier, chance > 0D && roll <= chance, fusedFlat);
    }

    private double clampedRoll(DamageContextVariables context, String key, double fallback) {
        double value = context == null ? fallback : context.getDouble(key, fallback);
        return Math.min(1D, Math.max(0D, value));
    }

    private double rolledSum(AttributeSnapshot snapshot,
            DamageContextVariables context,
            List<String> ids,
            String attributeIdsSignature,
            double damageRoll,
            DamageCalculationCache calculationCache) {
        double base = calculationCache.sum(snapshot, context, ids, attributeIdsSignature);
        double spread = calculationCache.spreadSum(snapshot, ids);
        return spread <= 0D ? base : base + (damageRoll * spread);
    }

    private AttributeSnapshot resolveSourceSnapshot(DamageContext damageContext, DamageStageSource source) {
        if (damageContext == null || source == null) {
            return null;
        }
        return switch (source) {
            case ATTACKER ->
                damageContext.attackerSnapshot();
            case TARGET ->
                damageContext.targetSnapshot();
            case CONTEXT ->
                null;
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

    private record StageInputs(double flat,
            double percent,
            double chance,
            double multiplier,
            boolean critical,
            boolean fusedFlat) {

    }
}
