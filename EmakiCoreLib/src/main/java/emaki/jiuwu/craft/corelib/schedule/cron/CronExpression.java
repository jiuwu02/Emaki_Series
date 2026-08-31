package emaki.jiuwu.craft.corelib.schedule.cron;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

public final class CronExpression {

    private static final String[] MONTH_NAMES = {
            "JAN", "FEB", "MAR", "APR", "MAY", "JUN",
            "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"
    };

    private static final String[] DOW_NAMES = {
            "SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"
    };

    private final Set<Integer> seconds;
    private final Set<Integer> minutes;
    private final Set<Integer> hours;
    private final Set<Integer> daysOfMonth;
    private final Set<Integer> months;
    private final Set<Integer> daysOfWeek;
    private final boolean domWildcard;
    private final boolean dowWildcard;

    private CronExpression(Set<Integer> seconds,
                           Set<Integer> minutes,
                           Set<Integer> hours,
                           Set<Integer> daysOfMonth, boolean domWildcard,
                           Set<Integer> months,
                           Set<Integer> daysOfWeek, boolean dowWildcard) {
        this.seconds = Collections.unmodifiableSet(seconds);
        this.minutes = Collections.unmodifiableSet(minutes);
        this.hours = Collections.unmodifiableSet(hours);
        this.daysOfMonth = Collections.unmodifiableSet(daysOfMonth);
        this.domWildcard = domWildcard;
        this.months = Collections.unmodifiableSet(months);
        this.daysOfWeek = Collections.unmodifiableSet(daysOfWeek);
        this.dowWildcard = dowWildcard;
    }

    public static CronExpression parse(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new CronParseException("Cron expression must not be null or blank");
        }
        String[] parts = expression.trim().split("\\s+");
        if (parts.length != 6) {
            throw new CronParseException("Cron expression must have exactly 6 fields, got "
                    + parts.length + ": '" + expression + "'");
        }

        boolean domWc = isWildcard(parts[3]);
        boolean dowWc = isWildcard(parts[5]);

        return new CronExpression(
                parseField(parts[0], 0, 59, null),
                parseField(parts[1], 0, 59, null),
                parseField(parts[2], 0, 23, null),
                parseField(parts[3], 1, 31, null), domWc,
                parseField(parts[4], 1, 12, MONTH_NAMES),
                parseField(parts[5], 1, 7, DOW_NAMES), dowWc
        );
    }

    public ZonedDateTime nextExecution(ZonedDateTime from) {
        ZonedDateTime candidate = from.plusSeconds(1).truncatedTo(ChronoUnit.SECONDS);
        ZonedDateTime limit = from.plusYears(4);

        outer:
        while (candidate.isBefore(limit)) {
            if (!months.contains(candidate.getMonthValue())) {
                int next = nextValue(months, candidate.getMonthValue());
                if (next < 0) {
                    candidate = candidate.plusYears(1)
                            .withMonth(first(months))
                            .withDayOfMonth(1)
                            .withHour(0).withMinute(0).withSecond(0);
                } else {
                    candidate = candidate.withMonth(next)
                            .withDayOfMonth(1)
                            .withHour(0).withMinute(0).withSecond(0);
                }
                continue;
            }

            if (!matchesDay(candidate)) {
                candidate = candidate.plusDays(1)
                        .withHour(0).withMinute(0).withSecond(0);
                continue;
            }

            if (!hours.contains(candidate.getHour())) {
                int next = nextValue(hours, candidate.getHour());
                if (next < 0) {
                    candidate = candidate.plusDays(1)
                            .withHour(first(hours)).withMinute(0).withSecond(0);
                } else {
                    candidate = candidate.withHour(next).withMinute(0).withSecond(0);
                }
                continue;
            }

            if (!minutes.contains(candidate.getMinute())) {
                int next = nextValue(minutes, candidate.getMinute());
                if (next < 0) {
                    candidate = candidate.plusHours(1)
                            .withMinute(first(minutes)).withSecond(0);
                } else {
                    candidate = candidate.withMinute(next).withSecond(0);
                }
                continue;
            }

            if (!seconds.contains(candidate.getSecond())) {
                int next = nextValue(seconds, candidate.getSecond());
                if (next < 0) {
                    candidate = candidate.plusMinutes(1)
                            .withSecond(first(seconds));
                } else {
                    candidate = candidate.withSecond(next);
                }
                continue;
            }

            return candidate;
        }
        return null;
    }

    private boolean matchesDay(ZonedDateTime dt) {
        int dom = dt.getDayOfMonth();
        int dow = dt.getDayOfWeek().getValue() % 7 + 1;

        if (domWildcard && dowWildcard) return true;
        if (!domWildcard && !dowWildcard) {
            return daysOfMonth.contains(dom) || daysOfWeek.contains(dow);
        }
        if (!domWildcard) return daysOfMonth.contains(dom);
        return daysOfWeek.contains(dow);
    }

    private static boolean isWildcard(String field) {
        return "*".equals(field) || "?".equals(field);
    }

    static Set<Integer> parseField(String field, int min, int max, String[] names) {
        if ("*".equals(field) || "?".equals(field)) {
            return allValues(min, max);
        }

        Set<Integer> result = new TreeSet<>();
        for (String part : field.split(",")) {
            part = part.trim();
            if (part.contains("/")) {
                String[] slashParts = part.split("/", 2);
                int step = parseRaw(slashParts[1].trim(), names, min, max);
                String basePart = slashParts[0].trim();
                int start;
                int end = max;
                if ("*".equals(basePart) || "?".equals(basePart)) {
                    start = min;
                } else if (basePart.contains("-")) {
                    String[] range = basePart.split("-", 2);
                    start = parseRaw(range[0].trim(), names, min, max);
                    end = parseRaw(range[1].trim(), names, min, max);
                } else {
                    start = parseRaw(basePart, names, min, max);
                }
                for (int v = start; v <= end; v += step) {
                    validateRange(v, min, max, field);
                    result.add(v);
                }
            } else if (part.contains("-")) {
                String[] range = part.split("-", 2);
                int start = parseRaw(range[0].trim(), names, min, max);
                int end = parseRaw(range[1].trim(), names, min, max);
                if (start > end) {
                    throw new CronParseException("Invalid range '" + part + "' in field: " + field);
                }
                for (int v = start; v <= end; v++) {
                    validateRange(v, min, max, field);
                    result.add(v);
                }
            } else {
                int val = parseRaw(part, names, min, max);
                validateRange(val, min, max, field);
                result.add(val);
            }
        }

        if (result.isEmpty()) {
            throw new CronParseException("Field produced no values: '" + field + "'");
        }
        return result;
    }

    private static int parseRaw(String token, String[] names, int min, int max) {
        if (names != null) {
            String upper = token.toUpperCase();
            for (int i = 0; i < names.length; i++) {
                if (names[i].equals(upper)) {
                    return min + i;
                }
            }
        }
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException e) {
            throw new CronParseException("Cannot parse cron token '" + token + "': " + e.getMessage());
        }
    }

    private static void validateRange(int value, int min, int max, String field) {
        if (value < min || value > max) {
            throw new CronParseException(
                    "Value " + value + " out of range [" + min + "," + max + "] in field: " + field);
        }
    }

    private static Set<Integer> allValues(int min, int max) {
        Set<Integer> set = new TreeSet<>();
        for (int i = min; i <= max; i++) {
            set.add(i);
        }
        return set;
    }

    private static int first(Set<Integer> set) {
        return set.iterator().next();
    }

    private static int nextValue(Set<Integer> set, int current) {
        for (int v : set) {
            if (v > current) return v;
        }
        return -1;
    }
}
