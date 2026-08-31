package emaki.jiuwu.craft.corelib.random;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import emaki.jiuwu.craft.corelib.math.Randoms;

public final class WeightedPool<T> {

    private final List<Entry<T>> entries = new ArrayList<>();
    private double totalWeight = 0.0;

    public void add(T item, double weight) {
        if (item == null || weight <= 0.0) {
            return;
        }
        entries.add(new Entry<>(item, weight));
        totalWeight += weight;
    }

    public Optional<T> roll() {
        if (entries.isEmpty() || totalWeight <= 0.0) {
            return Optional.empty();
        }
        double roll = Randoms.uniform(0.0, totalWeight);
        double cumulative = 0.0;
        for (Entry<T> entry : entries) {
            cumulative += entry.weight;
            if (roll <= cumulative) {
                return Optional.of(entry.item);
            }
        }
        return Optional.of(entries.get(entries.size() - 1).item);
    }

    public void clear() {
        entries.clear();
        totalWeight = 0.0;
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    private record Entry<T>(T item, double weight) {
    }
}
