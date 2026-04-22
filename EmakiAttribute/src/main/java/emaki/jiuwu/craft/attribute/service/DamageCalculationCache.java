package emaki.jiuwu.craft.attribute.service;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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
        return sum(snapshot, context, ids, SignatureUtil.stableSignature(ids));
    }

    double sum(AttributeSnapshot snapshot, DamageContextVariables context, List<String> ids, String attributeIdsSignature) {
        if (ids == null || ids.isEmpty()) {
            return 0D;
        }
        AttributeSumKey key = new AttributeSumKey(
                snapshot == null ? "" : snapshot.sourceSignature(),
                contextSignature(context),
                attributeIdsSignature
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

    private static final class BoundedCache<K, V> {

        private final int limit;
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        private final LinkedHashMap<K, V> entries = new LinkedHashMap<>(16, 0.75F, true);

        private BoundedCache(int limit) {
            this.limit = Math.max(1, limit);
        }

        private V get(K key) {
            if (key == null) {
                return null;
            }
            lock.writeLock().lock();
            try {
                return entries.get(key);
            } finally {
                lock.writeLock().unlock();
            }
        }

        private void put(K key, V value) {
            if (key == null || value == null) {
                return;
            }
            lock.writeLock().lock();
            try {
                entries.remove(key);
                entries.put(key, value);
                while (entries.size() > limit) {
                    K eldest = entries.keySet().iterator().next();
                    entries.remove(eldest);
                }
            } finally {
                lock.writeLock().unlock();
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
