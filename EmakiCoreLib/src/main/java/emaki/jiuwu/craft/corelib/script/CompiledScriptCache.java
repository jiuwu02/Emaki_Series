package emaki.jiuwu.craft.corelib.script;

import org.graalvm.polyglot.Source;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.cache.CacheManager;

final class CompiledScriptCache {

    private static final String LANGUAGE_ID = "js";
    private static final int CACHE_SIZE = 1024;

    private final CacheManager<String, Source> cache = new CacheManager<>(CACHE_SIZE, 0);

    @NotNull Source getOrCompile(@NotNull String code, @NotNull String name) {
        Source cached = cache.get(code);
        if (cached != null) {
            return cached;
        }
        Source compiled = Source.newBuilder(LANGUAGE_ID, code, name)
                .cached(true)
                .buildLiteral();
        cache.put(code, compiled);
        return compiled;
    }

    void clear() {
        cache.clear();
    }

    int size() {
        return cache.size();
    }
}
