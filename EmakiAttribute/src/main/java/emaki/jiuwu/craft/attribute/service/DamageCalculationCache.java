package emaki.jiuwu.craft.attribute.service;

import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Map;

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

    private final BoundedCache<DamageResultKey, DamageResult> resultCache;
    private final BoundedCache<AttributeSumKey, Double> sumCache;

    DamageCalculationCache() {
        this(DEFAULT_RESULT_LIMIT, DEFAULT_SUM_LIMIT);
    }

    DamageCalculationCache(int resultLimit, int sumLimit) {
        this.resultCache = new BoundedCache<>(resultLimit);
        this.sumCache = new BoundedCache<>(sumLimit);
    }

    DamageResult getResult(DamageRequest request, DamageTypeDefinition definition, double seededRoll) {
        DamageResultKey key = resultKey(request, definition, seededRoll);
        return resultCache.get(key);
    }

    void cacheResult(DamageRequest request, DamageTypeDefinition definition, double seededRoll, DamageResult result) {
        if (result == null) {
            return;
        }
        resultCache.put(resultKey(request, definition, seededRoll), result);
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
        sumCache.put(key, computed);
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

    private String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static final class BoundedCache<K, V> {

        private final int limit;
        private final ReentrantLock lock = new ReentrantLock();
        private final LinkedHashMap<K, V> entries = new LinkedHashMap<>(16, 0.75F, true);

        private BoundedCache(int limit) {
            this.limit = Math.max(1, limit);
        }

        private V get(K key) {
            if (key == null) {
                return null;
            }
            lock.lock();
            try {
                return entries.get(key);
            } finally {
                lock.unlock();
            }
        }

        private void put(K key, V value) {
            if (key == null || value == null) {
                return;
            }
            lock.lock();
            try {
                entries.put(key, value);
                while (entries.size() > limit) {
                    K eldest = entries.keySet().iterator().next();
                    entries.remove(eldest);
                }
            } finally {
                lock.unlock();
            }
        }
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

        private AttributeSumKey   {
            snapshotSignature = Objects.requireNonNullElse(snapshotSignature, "");
            contextSignature = Objects.requireNonNullElse(contextSignature, "");
            attributeIdsSignature = Objects.requireNonNullElse(attributeIdsSignature, "");
        }
    }
}
