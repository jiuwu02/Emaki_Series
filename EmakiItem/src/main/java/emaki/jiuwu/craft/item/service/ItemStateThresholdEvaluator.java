package emaki.jiuwu.craft.item.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.item.api.event.ItemStateThresholdEvent;
import emaki.jiuwu.craft.item.model.ItemStateConfig;

public final class ItemStateThresholdEvaluator {

    public record Crossing(ItemStateConfig.Threshold threshold,
            ItemStateThresholdEvent.Direction direction,
            boolean rearmed) {
    }

    public record Outcome(List<Crossing> crossings, long mask, boolean maskChanged) {

        public Outcome {
            crossings = crossings == null ? List.of() : List.copyOf(crossings);
        }

        public boolean empty() {
            return crossings.isEmpty();
        }
    }

    private ItemStateThresholdEvaluator() {
    }

    public static Outcome evaluate(ItemStateConfig.Field field,
            Number oldValue,
            Number newValue,
            long storedMask) {
        if (field == null || field.thresholds().isEmpty() || newValue == null) {
            return new Outcome(List.of(), storedMask, false);
        }
        BigDecimal after = decimal(newValue);
        if (after == null) {
            return new Outcome(List.of(), storedMask, false);
        }
        BigDecimal before = decimal(oldValue);
        List<ItemStateConfig.Threshold> thresholds = field.thresholds();
        List<Crossing> rising = new ArrayList<>();
        List<Crossing> falling = new ArrayList<>();
        long mask = storedMask;
        for (int index = 0; index < thresholds.size() && index < ItemStateConfig.MAX_THRESHOLDS_PER_FIELD; index++) {
            ItemStateConfig.Threshold threshold = thresholds.get(index);
            boolean reachedBefore = before != null && before.compareTo(threshold.value()) >= 0;
            boolean reachedAfter = after.compareTo(threshold.value()) >= 0;
            long bit = 1L << index;
            boolean latched = (mask & bit) != 0L;
            if (reachedAfter && !reachedBefore) {
                if (threshold.once() && latched) {
                    continue;
                }
                mask |= bit;
                rising.add(new Crossing(threshold, ItemStateThresholdEvent.Direction.UP, false));
                continue;
            }
            if (!reachedAfter && reachedBefore) {
                boolean rearmed = latched;
                mask &= ~bit;
                falling.add(new Crossing(threshold, ItemStateThresholdEvent.Direction.DOWN, rearmed));
            }
        }
        List<Crossing> ordered = new ArrayList<>(rising.size() + falling.size());
        ordered.addAll(rising);
        for (int index = falling.size() - 1; index >= 0; index--) {
            ordered.add(falling.get(index));
        }
        return new Outcome(ordered, mask, mask != storedMask);
    }

    private static BigDecimal decimal(Number value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Integer || value instanceof Long) {
            return BigDecimal.valueOf(value.longValue());
        }
        double converted = value.doubleValue();
        return Double.isFinite(converted) ? BigDecimal.valueOf(converted) : null;
    }
}
