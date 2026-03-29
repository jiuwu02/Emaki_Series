package emaki.jiuwu.craft.attribute.service;

import emaki.jiuwu.craft.attribute.model.DamageContext;
import emaki.jiuwu.craft.attribute.model.DamageContextVariables;
import emaki.jiuwu.craft.attribute.model.DamageRequest;
import emaki.jiuwu.craft.attribute.model.DamageResult;
import emaki.jiuwu.craft.attribute.model.DamageStageDefinition;
import emaki.jiuwu.craft.attribute.model.DamageTypeDefinition;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class DamageEngine {

    private final DamageCalculationCache calculationCache;
    private final StageCalculator stageCalculator;

    public DamageEngine() {
        this(new DamageCalculationCache(), new StageCalculator());
    }

    DamageEngine(DamageCalculationCache calculationCache, StageCalculator stageCalculator) {
        this.calculationCache = calculationCache;
        this.stageCalculator = stageCalculator;
    }

    public DamageResult resolve(DamageRequest request, DamageTypeDefinition definition) {
        return resolve(request, definition, ThreadLocalRandom.current().nextDouble() * 100D);
    }

    public DamageResult resolve(DamageRequest request, DamageTypeDefinition definition, double seededRoll) {
        if (request == null || definition == null) {
            return emptyResult();
        }
        DamageResult cached = calculationCache.getResult(request, definition, seededRoll);
        if (cached != null) {
            return cached;
        }
        DamageContext damageContext = request.damageContext();
        double current = Math.max(0D, damageContext.baseDamage());
        Map<String, Double> stageValues = new LinkedHashMap<>();
        boolean critical = false;
        for (DamageStageDefinition stage : definition.stages()) {
            if (stage == null) {
                continue;
            }
            StageCalculator.StageOutcome outcome = stageCalculator.calculate(current, request, stage, seededRoll, calculationCache);
            current = outcome.value();
            critical = critical || outcome.critical();
            stageValues.put(stage.id(), current);
        }
        DamageResult result = new DamageResult(definition.id(), Math.max(0D, current), critical, seededRoll, stageValues, damageContext);
        calculationCache.cacheResult(request, definition, seededRoll, result);
        return result;
    }
    private DamageResult emptyResult() {
        return new DamageResult("", 0D, false, 0D, Map.of(), DamageContext.legacy("", 0D, null, null, DamageContextVariables.empty()));
    }
}
