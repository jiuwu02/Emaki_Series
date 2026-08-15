package emaki.jiuwu.craft.corelib.script;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.polyglot.Source;
import org.jetbrains.annotations.NotNull;

final class CompiledScriptCache {

    private static final String LANGUAGE_ID = "js";
    private final Map<String, Source> cache = new ConcurrentHashMap<>();

    @NotNull Source getOrCompile(@NotNull String code, @NotNull String name) {
        return cache.computeIfAbsent(code, key ->
                Source.newBuilder(LANGUAGE_ID, key, name)
                        .cached(true)
                        .buildLiteral()
        );
    }

    void clear() {
        cache.clear();
    }

    int size() {
        return cache.size();
    }
}
