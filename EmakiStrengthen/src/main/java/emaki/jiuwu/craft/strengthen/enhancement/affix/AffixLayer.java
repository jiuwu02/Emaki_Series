package emaki.jiuwu.craft.strengthen.enhancement.affix;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public record AffixLayer(int capacityMax, @NotNull Map<String, AffixState> affixes) {

    public AffixLayer {
        capacityMax = Math.max(0, capacityMax);
        affixes = affixes == null ? Map.of() : Map.copyOf(affixes);
    }

    public static @NotNull AffixLayer empty(int capacityMax) {
        return new AffixLayer(capacityMax, Map.of());
    }

    public int capacityUsed() {
        long used = 0L;
        for (AffixState state : affixes.values()) {
            used += state.capacityCost();
            if (used >= Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
        }
        return (int) used;
    }

    public int capacityRemaining() {
        return Math.max(0, capacityMax - capacityUsed());
    }

    public boolean canAfford(int cost) {
        return cost <= 0 || capacityRemaining() >= cost;
    }

    public @NotNull AffixState affix(@Nullable String attributeKey) {
        String key = Texts.lower(attributeKey);
        AffixState state = affixes.get(key);
        return state == null ? AffixState.fresh(key) : state;
    }

    public @NotNull AffixLayer with(@NotNull AffixState state) {
        Map<String, AffixState> next = new LinkedHashMap<>(affixes);
        next.put(state.attributeKey(), state);
        return new AffixLayer(capacityMax, next);
    }

    public @NotNull AffixLayer without(@Nullable String attributeKey) {
        String key = Texts.lower(attributeKey);
        if (!affixes.containsKey(key)) {
            return this;
        }
        Map<String, AffixState> next = new LinkedHashMap<>(affixes);
        next.remove(key);
        return new AffixLayer(capacityMax, next);
    }

    public @NotNull AffixLayer withCapacityMax(int newCapacityMax) {
        return new AffixLayer(newCapacityMax, affixes);
    }
}
