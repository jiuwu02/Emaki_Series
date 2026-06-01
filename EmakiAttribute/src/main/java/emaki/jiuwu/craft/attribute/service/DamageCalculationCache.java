package emaki.jiuwu.craft.attribute.service;

import java.util.List;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import emaki.jiuwu.craft.attribute.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.model.DamageContext;
import emaki.jiuwu.craft.attribute.model.DamageContextVariables;
import emaki.jiuwu.craft.attribute.model.DamageRequest;
import emaki.jiuwu.craft.attribute.model.DamageResult;
import emaki.jiuwu.craft.attribute.model.DamageTypeDefinition;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.text.Texts;

final class DamageCalculationCache {

    private static final int DEFAULT_RESULT_LIMIT = 512;
    private static final int DEFAULT_SUM_LIMIT = 1024;

    private final Cache<DamageResultKey, DamageResult> resultCache;
    private final Cache<AttributeSumKey, Double> sumCache;

    DamageCalculationCache() {
        this(DEFAULT_RESULT_LIMIT, DEFAULT_SUM_LIMIT);
    }

    DamageCalculationCache(int resultLimit, int sumLimit) {
        this.resultCache = Caffeine.newBuilder()
                .maximumSize(resultLimit)
                .build();
        this.sumCache = Caffeine.newBuilder()
                .maximumSize(sumLimit)
                .build();
    }

    DamageResult getResult(DamageRequest request, DamageTypeDefinition definition, double seededRoll) {
        DamageResultKey key = resultKey(request, definition, seededRoll);
        return resultCache.getIfPresent(key);
    }

    void cacheResult(DamageRequest request, DamageTypeDefinition definition, double seededRoll, DamageResult result) {
        if (result == null) {
            return;
        }
        resultCache.put(resultKey(request, definition, seededRoll), result);
    }

    double sum(AttributeSnapshot snapshot, DamageContextVariables context, List<String> ids) {
        return sum(snapshot, context, ids, SignatureUtil.stableSignature(ids));
    }

    double sum(AttributeSnapshot snapshot, DamageContextVariables context, List<String> ids, String attributeIdsSignature) {
        if (ids == null || ids.isEmpty()) {
            return 0D;
        }
        AttributeSumKey key = new AttributeSumKey(
                snapshot == null ? 0L : (long) safeHashCode(snapshot.sourceSignature()),
                contextSigHash(context),
                (long) safeHashCode(attributeIdsSignature)
        );
        Double cached = sumCache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        double computed = computeSum(snapshot, context, ids);
        sumCache.put(key, computed);
        return computed;
    }

    /**
     * {@return the summed random "spread" (max - min) of the given attributes}
     *
     * <p>Reads the {@code <id>$range_spread} companion entries written by the
     * lore parser. Not cached: it is a handful of O(1) map lookups and must stay
     * outside the per-roll damage path so range rolling is applied fresh.
     */
    double spreadSum(AttributeSnapshot snapshot, List<String> ids) {
        if (snapshot == null || ids == null || ids.isEmpty()) {
            return 0D;
        }
        var values = snapshot.values();
        if (values.isEmpty()) {
            return 0D;
        }
        double total = 0D;
        for (String id : ids) {
            if (Texts.isBlank(id)) {
                continue;
            }
            Double spread = values.get(AttributeSnapshot.rangeSpreadKey(id));
            if (spread != null) {
                total += spread;
            }
        }
        return total;
    }

    private DamageResultKey resultKey(DamageRequest request, DamageTypeDefinition definition, double seededRoll) {
        DamageContext damageContext = request == null ? null : request.damageContext();
        return new DamageResultKey(
                definition == null ? "" : definition.id(),
                (long) safeHashCode(definition == null ? "" : SignatureUtil.stableSignature(definition.stages())),
                damageContext == null ? "" : damageContext.causeId(),
                Double.doubleToRawLongBits(damageContext == null ? 0D : damageContext.sourceDamage()),
                Double.doubleToRawLongBits(damageContext == null ? 0D : damageContext.baseDamage()),
                Double.doubleToRawLongBits(seededRoll),
                (long) safeHashCode(damageContext == null || damageContext.attackerSnapshot() == null ? "" : damageContext.attackerSnapshot().sourceSignature()),
                (long) safeHashCode(damageContext == null || damageContext.targetSnapshot() == null ? "" : damageContext.targetSnapshot().sourceSignature()),
                contextSigHash(damageContext == null ? null : damageContext.variables())
        );
    }

    private long contextSigHash(DamageContextVariables context) {
        if (context == null || context.isEmpty()) {
            return 0L;
        }
        return (long) SignatureUtil.stableSignature(context.asMap()).hashCode();
    }

    private static int safeHashCode(String value) {
        return value == null ? 0 : value.hashCode();
    }

    private double computeSum(AttributeSnapshot snapshot, DamageContextVariables context, List<String> ids) {
        double total = 0D;
        for (String id : ids) {
            if (Texts.isBlank(id)) {
                continue;
            }
            Double value = snapshot == null ? null : snapshot.values().get(id);
            if (value == null && context != null) {
                value = Numbers.tryParseDouble(context.get(id), null);
            }
            if (value != null) {
                total += value;
            }
        }
        return total;
    }

    private record DamageResultKey(String damageTypeId,
            long stagesHash,
            String causeId,
            long sourceDamageBits,
            long baseDamageBits,
            long seededRollBits,
            long attackerSigHash,
            long targetSigHash,
            long contextSigHash) {

    }

    private record AttributeSumKey(long snapshotSigHash, long contextSigHash, long attributeIdsSigHash) {

    }
}
