package emaki.jiuwu.craft.corelib.math;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class Randoms {

    public record Weighted<T>(T item, double weight) {
    }

    private Randoms() {
    }

    public static double constant(Object value) {
        Double parsed = Numbers.tryParseDouble(value, 0D);
        return parsed == null ? 0D : parsed;
    }

    public static double uniform(double min, double max) {
        if (min > max) {
            double swap = min;
            min = max;
            max = swap;
        }
        if (Double.compare(min, max) == 0) {
            return min;
        }
        return ThreadLocalRandom.current().nextDouble(min, max);
    }

    public static double gaussian(double mean, double stdDev, Double min, Double max, int maxAttempts) {
        double safeStd = Math.abs(stdDev);
        int attempts = Math.max(1, maxAttempts);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double lastValue = mean;
        for (int i = 0; i < attempts; i++) {
            double value = mean + random.nextGaussian() * safeStd;
            lastValue = value;
            if ((min == null || value >= min) && (max == null || value <= max)) {
                return value;
            }
        }
        return clamp(lastValue, min, max);
    }

    public static double skewNormal(double mean, double stdDev, double skewness, Double min, Double max, int maxAttempts) {
        double delta = Numbers.clamp(skewness, -0.995D, 0.995D);
        double residual = Math.sqrt(Math.max(0D, 1D - delta * delta));
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int attempts = Math.max(1, maxAttempts);
        double lastValue = mean;
        for (int i = 0; i < attempts; i++) {
            double z = delta * Math.abs(random.nextGaussian()) + residual * random.nextGaussian();
            double value = mean + z * Math.abs(stdDev);
            lastValue = value;
            if ((min == null || value >= min) && (max == null || value <= max)) {
                return value;
            }
        }
        return clamp(lastValue, min, max);
    }

    public static double triangle(double mode, double deviation) {
        double safeDeviation = Math.abs(deviation);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double u = random.nextDouble();
        double v = random.nextDouble();
        if (u < v) {
            return mode - safeDeviation + (v - u) * safeDeviation;
        }
        return mode + safeDeviation - (u - v) * safeDeviation;
    }

    public static int randomInt(int min, int max) {
        if (min > max) {
            int swap = min;
            min = max;
            max = swap;
        }
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    public static double randomDouble() {
        return ThreadLocalRandom.current().nextDouble();
    }

    public static boolean chance(double probability) {
        return randomDouble() < Numbers.clamp(probability, 0D, 1D);
    }

    public static <T> T weightedRandom(List<Weighted<T>> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        double totalWeight = 0D;
        for (Weighted<T> item : items) {
            if (item != null && item.weight() > 0D) {
                totalWeight += item.weight();
            }
        }
        if (totalWeight <= 0D) {
            return null;
        }
        double roll = uniform(0D, totalWeight);
        double cumulative = 0D;
        for (Weighted<T> item : items) {
            if (item == null || item.weight() <= 0D) {
                continue;
            }
            cumulative += item.weight();
            if (roll <= cumulative) {
                return item.item();
            }
        }
        return items.get(items.size() - 1).item();
    }

    public static <T> List<T> shuffle(List<T> values) {
        if (values == null || values.isEmpty()) {
            return values == null ? List.of() : new ArrayList<>(values);
        }
        List<T> result = new ArrayList<>(values);
        Collections.shuffle(result);
        return result;
    }

    private static double clamp(double value, Double min, Double max) {
        if (min != null && value < min) {
            value = min;
        }
        if (max != null && value > max) {
            value = max;
        }
        return value;
    }
}
