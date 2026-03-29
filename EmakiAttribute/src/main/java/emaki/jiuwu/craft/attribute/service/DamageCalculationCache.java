package emaki.jiuwu.craft.attribute.service;

import emaki.jiuwu.craft.attribute.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.model.DamageContext;
import emaki.jiuwu.craft.attribute.model.DamageContextVariables;
import emaki.jiuwu.craft.attribute.model.DamageRequest;
import emaki.jiuwu.craft.attribute.model.DamageResult;
import emaki.jiuwu.craft.attribute.model.DamageTypeDefinition;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

final class DamageCalculationCache {

    private static final int DEFAULT_RESULT_LIMIT = 512;
    private static final int DEFAULT_SUM_LIMIT = 1024;

    private final int resultLimit;
    private final int sumLimit;
    private final Map<DamageResultKey, DamageResult> resultCache = new ConcurrentHashMap<>();
    private final Map<AttributeSumKey, Double> sumCache = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<DamageResultKey> resultOrder = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<AttributeSumKey> sumOrder = new ConcurrentLinkedQueue<>();

    DamageCalculationCache() {
        this(DEFAULT_RESULT_LIMIT, DEFAULT_SUM_LIMIT);
    }

    DamageCalculationCache(int resultLimit, int sumLimit) {
        this.resultLimit = Math.max(1, resultLimit);
        this.sumLimit = Math.max(1, sumLimit);
    }

    DamageResult getResult(DamageRequest request, DamageTypeDefinition definition, double seededRoll) {
        DamageResultKey key = resultKey(request, definition, seededRoll);
        DamageResult cached = resultCache.get(key);
        return cached;
    }

    void cacheResult(DamageRequest request, DamageTypeDefinition definition, double seededRoll, DamageResult result) {
        if (result == null) {
            return;
        }
        putBounded(resultCache, resultOrder, resultKey(request, definition, seededRoll), result, resultLimit);
    }

    double sum(AttributeSnapshot snapshot, DamageContextVariables context, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0D;
        }
        AttributeSumKey key = new AttributeSumKey(
            snapshot == null ? "" : snapshot.sourceSignature(),
            contextSignature(context),
            SignatureUtil.stableSignature(ids)
        );
        Double cached = sumCache.get(key);
        if (cached != null) {
            return cached;
        }
        double computed = computeSum(snapshot, context, ids);
        putBounded(sumCache, sumOrder, key, computed, sumLimit);
        return computed;
    }

    private DamageResultKey resultKey(DamageRequest request, DamageTypeDefinition definition, double seededRoll) {
        DamageContext damageContext = request == null ? null : request.damageContext();
        return new DamageResultKey(
            definition == null ? "" : definition.id(),
            definition == null ? "" : SignatureUtil.stableSignature(definition.stages()),
            damageContext == null ? "" : damageContext.causeId(),
            damageContext == null ? 0D : damageContext.sourceDamage(),
            damageContext == null ? 0D : damageContext.baseDamage(),
            seededRoll,
            damageContext == null || damageContext.attackerSnapshot() == null ? "" : damageContext.attackerSnapshot().sourceSignature(),
            damageContext == null || damageContext.targetSnapshot() == null ? "" : damageContext.targetSnapshot().sourceSignature(),
            contextSignature(damageContext == null ? null : damageContext.variables())
        );
    }

    private String contextSignature(DamageContextVariables context) {
        return context == null || context.isEmpty() ? "" : SignatureUtil.stableSignature(context.asMap());
    }

    private double computeSum(AttributeSnapshot snapshot, DamageContextVariables context, List<String> ids) {
        double total = 0D;
        for (String id : ids) {
            if (Texts.isBlank(id)) {
                continue;
            }
            String normalized = normalizeId(id);
            Double value = snapshot == null ? null : snapshot.values().get(normalized);
            if (value == null && context != null) {
                value = Numbers.tryParseDouble(context.get(normalized), null);
            }
            if (value != null) {
                total += value;
            }
        }
        return total;
    }

    private <K, V> void putBounded(Map<K, V> cache, ConcurrentLinkedQueue<K> order, K key, V value, int limit) {
        if (key == null || value == null || cache.putIfAbsent(key, value) != null) {
            return;
        }
        order.add(key);
        while (cache.size() > limit) {
            K evicted = order.poll();
            if (evicted == null) {
                break;
            }
            cache.remove(evicted);
        }
    }

    private String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private record DamageResultKey(String damageTypeId,
                                   String stagesSignature,
                                   String causeId,
                                   double sourceDamage,
                                   double baseDamage,
                                   double seededRoll,
                                   String attackerSignature,
                                   String targetSignature,
                                   String contextSignature) {
    }

    private record AttributeSumKey(String snapshotSignature, String contextSignature, String attributeIdsSignature) {
        private AttributeSumKey {
            snapshotSignature = Objects.requireNonNullElse(snapshotSignature, "");
            contextSignature = Objects.requireNonNullElse(contextSignature, "");
            attributeIdsSignature = Objects.requireNonNullElse(attributeIdsSignature, "");
        }
    }
}
