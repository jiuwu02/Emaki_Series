package emaki.jiuwu.craft.corelib.expression;

import java.lang.ref.SoftReference;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.function.Function;

import net.objecthunter.exp4j.Expression;

final class ExpressionCache {

    static final int COMPILED_CACHE_LIMIT = 256;
    static final long COMPILED_CACHE_TTL_MILLIS = 30 * 60 * 1000L;
    static final int GLOBAL_CACHE_LIMIT = 1024;

    private static final Map<String, Expression> GLOBAL_COMPILED_CACHE = new ConcurrentHashMap<>(256);

    private static final ThreadLocal<LinkedHashMap<String, CachedExpression>> COMPILED_CACHE = ThreadLocal.withInitial(
            () -> new LinkedHashMap<>(COMPILED_CACHE_LIMIT, 0.75F, true)
    );

    private ExpressionCache() {
    }

    static Expression getOrCompile(String prepared, Function<String, Expression> compiler) {
        long now = System.currentTimeMillis();
        LinkedHashMap<String, CachedExpression> cache = COMPILED_CACHE.get();
        CachedExpression cached = cache.get(prepared);
        Expression expression = cached == null ? null : cached.expression();
        if (expression != null && !cached.isExpired(now)) {
            cached.touch(now);
            return expression;
        }
        if (cached != null) {
            cache.remove(prepared);
        }
        Expression global = GLOBAL_COMPILED_CACHE.get(prepared);
        if (global != null) {
            cache.put(prepared, new CachedExpression(global, now + COMPILED_CACHE_TTL_MILLIS));
            trimCompiledCache(cache);
            return global;
        }
        Expression compiled = compiler.apply(prepared);
        cache.put(prepared, new CachedExpression(compiled, now + COMPILED_CACHE_TTL_MILLIS));
        trimCompiledCache(cache);
        if (GLOBAL_COMPILED_CACHE.size() >= GLOBAL_CACHE_LIMIT) {
            var iterator = GLOBAL_COMPILED_CACHE.keySet().iterator();
            int toRemove = GLOBAL_CACHE_LIMIT / 4;
            while (iterator.hasNext() && toRemove > 0) {
                iterator.next();
                iterator.remove();
                toRemove--;
            }
        }
        GLOBAL_COMPILED_CACHE.put(prepared, compiled);
        return compiled;
    }

    static void clearThreadLocal() {
        COMPILED_CACHE.remove();
    }

    static void clearGlobal() {
        GLOBAL_COMPILED_CACHE.clear();
    }

    private static void trimCompiledCache(LinkedHashMap<String, CachedExpression> cache) {
        while (cache.size() > COMPILED_CACHE_LIMIT) {
            String eldest = cache.keySet().iterator().next();
            cache.remove(eldest);
        }
    }

    static final class CachedExpression {

        private final SoftReference<Expression> reference;
        private volatile long expiresAt;

        CachedExpression(Expression expression, long expiresAt) {
            this.reference = new SoftReference<>(expression);
            this.expiresAt = expiresAt;
        }

        Expression expression() {
            return reference.get();
        }

        boolean isExpired(long now) {
            return now >= expiresAt || reference.get() == null;
        }

        void touch(long now) {
            expiresAt = now + COMPILED_CACHE_TTL_MILLIS;
        }
    }
}
