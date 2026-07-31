package emaki.jiuwu.craft.corelib.action.v2.compile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** In-memory {@link SequenceCatalog} for cycle and parameter checks. */
final class FakeSequenceCatalog implements SequenceCatalog {

    private final Map<String, Set<String>> required = new LinkedHashMap<>();
    private final Map<String, Set<String>> calls = new LinkedHashMap<>();

    FakeSequenceCatalog define(String name, Set<String> requiredParameters, Set<String> callees) {
        required.put(name, requiredParameters);
        calls.put(name, callees);
        return this;
    }

    @Override
    public boolean contains(@Nullable String name) {
        return name != null && required.containsKey(name);
    }

    @Override
    public @NotNull Set<String> requiredParameters(@Nullable String name) {
        return required.getOrDefault(name, Set.of());
    }

    @Override
    public @NotNull Set<String> calls(@Nullable String name) {
        return calls.getOrDefault(name, Set.of());
    }

    @Override
    public @NotNull List<String> names() {
        return List.copyOf(required.keySet());
    }
}
