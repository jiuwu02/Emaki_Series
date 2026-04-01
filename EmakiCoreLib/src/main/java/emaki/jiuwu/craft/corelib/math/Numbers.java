package emaki.jiuwu.craft.corelib.math;

import java.text.DecimalFormat;

import emaki.jiuwu.craft.corelib.text.Texts;

public final class Numbers {

    private Numbers() {
    }

    public static Integer tryParseInt(Object value, Integer defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(Texts.trim(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    public static Long tryParseLong(Object value, Long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(Texts.trim(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    public static Double tryParseDouble(Object value, Double defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(Texts.trim(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    public static boolean isNumeric(Object value) {
        return tryParseDouble(value, null) != null;
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static String formatNumber(double value, String pattern) {
        String effectivePattern = Texts.isBlank(pattern) ? "0.##" : pattern;
        try {
            return new DecimalFormat(effectivePattern).format(value);
        } catch (Exception ignored) {
            return Double.toString(value);
        }
    }

    public static int roundToInt(double value) {
        return (int) Math.round(value);
    }

    public static int floor(double value) {
        return (int) Math.floor(value);
    }

    public static int ceil(double value) {
        return (int) Math.ceil(value);
    }

    public static double safeDivide(double numerator, double denominator, double defaultValue) {
        if (denominator == 0D) {
            return defaultValue;
        }
        return numerator / denominator;
    }

    public static Double parsePercentage(Object value) {
        if (value == null) {
            return null;
        }
        String text = Texts.trim(value);
        if (text.endsWith("%")) {
            text = text.substring(0, text.length() - 1);
        }
        return tryParseDouble(text, null);
    }
}
