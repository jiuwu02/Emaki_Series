package emaki.jiuwu.craft.corelib.progression;

import java.util.Map;

public final class TableProgression<T> implements Progression<T> {

    private final Map<Integer, T> table;
    private final T fallback;

    public TableProgression(Map<Integer, T> table, T fallback) {
        this.table = table == null ? Map.of() : Map.copyOf(table);
        this.fallback = fallback;
    }

    @Override
    public T valueAt(int level) {
        T value = table.get(level);
        return value != null ? value : fallback;
    }

    public Map<Integer, T> table() {
        return table;
    }

    public T fallback() {
        return fallback;
    }
}
