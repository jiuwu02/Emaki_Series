package emaki.jiuwu.craft.corelib.schedule.cron;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * 6 段 Quartz 兼容 Cron 表达式解析器。
 *
 * <p>字段顺序：{@code 秒 分 时 日(月) 月 日(周)}
 *
 * <p>支持：{@code *}、{@code ?}（等价于 {@code *}）、固定值、{@code ,}、{@code -}、{@code /}。
 * 月和星期可使用名称缩写（JAN-DEC / SUN-SAT），不区分大小写。
 *
 * <p>示例：
 * <pre>
 *   "0 0 19 * * ?"  — 每天 19:00:00
 *   "0 30 8 ? * MON-FRI"  — 工作日 08:30:00
 *   "0 0/15 * * * *"  — 每 15 分钟
 * </pre>
 */
public final class CronExpression {

    // 月名缩写映射（1-12）
    private static final String[] MONTH_NAMES = {
            "JAN", "FEB", "MAR", "APR", "MAY", "JUN",
            "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"
    };

    // 星期名缩写映射（1=SUN … 7=SAT）
    private static final String[] DOW_NAMES = {
            "SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"
    };

    private final Set<Integer> seconds;    // 0-59
    private final Set<Integer> minutes;    // 0-59
    private final Set<Integer> hours;      // 0-23
    private final Set<Integer> daysOfMonth;// 1-31
    private final Set<Integer> months;     // 1-12
    private final Set<Integer> daysOfWeek; // 1=SUN … 7=SAT
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

    /**
     * 解析 6 段 Cron 表达式字符串。
     *
     * @param expression Cron 表达式，格式为 {@code "秒 分 时 日(月) 月 日(周)"}
     * @return 解析后的 {@link CronExpression} 实例
     * @throws CronParseException 解析失败时抛出
     */
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

    /**
     * 计算从给定时间点之后的下一次触发时间。
     *
     * @param from 基准时间（不含，即结果严格晚于此时间）
     * @return 下一次触发的 {@link ZonedDateTime}，若4年内找不到则返回 {@code null}
     */
    public ZonedDateTime nextExecution(ZonedDateTime from) {
        // 从 from+1s 开始搜索
        ZonedDateTime candidate = from.plusSeconds(1).truncatedTo(ChronoUnit.SECONDS);
        ZonedDateTime limit = from.plusYears(4);

        outer:
        while (candidate.isBefore(limit)) {
            // 检查月份
            if (!months.contains(candidate.getMonthValue())) {
                int next = nextValue(months, candidate.getMonthValue());
                if (next < 0) {
                    // 当年无更多月份，跳到下一年1月1日
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

            // 检查日（月日 + 星期日）
            if (!matchesDay(candidate)) {
                candidate = candidate.plusDays(1)
                        .withHour(0).withMinute(0).withSecond(0);
                continue;
            }

            // 检查小时
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

            // 检查分钟
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

            // 检查秒
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

            // 所有字段匹配
            return candidate;
        }
        return null;
    }

    // ─── 日期匹配 ────────────────────────────────────────────────────────────

    private boolean matchesDay(ZonedDateTime dt) {
        int dom = dt.getDayOfMonth();
        // DayOfWeek: ZonedDateTime 使用 ISO（MON=1…SUN=7），转成 Quartz（SUN=1…SAT=7）
        int dow = dt.getDayOfWeek().getValue() % 7 + 1; // ISO MON(1)→2, …, ISO SUN(7)→1

        if (domWildcard && dowWildcard) return true;
        if (!domWildcard && !dowWildcard) {
            // 两者都指定，Quartz 行为：满足任意一个即可
            return daysOfMonth.contains(dom) || daysOfWeek.contains(dow);
        }
        if (!domWildcard) return daysOfMonth.contains(dom);
        return daysOfWeek.contains(dow);
    }

    // ─── 字段解析工具 ─────────────────────────────────────────────────────────

    private static boolean isWildcard(String field) {
        return "*".equals(field) || "?".equals(field);
    }

    /**
     * 解析单个 Cron 字段，返回该字段所有合法值的有序集合。
     */
    static Set<Integer> parseField(String field, int min, int max, String[] names) {
        if ("*".equals(field) || "?".equals(field)) {
            return allValues(min, max);
        }

        Set<Integer> result = new TreeSet<>();
        for (String part : field.split(",")) {
            part = part.trim();
            if (part.contains("/")) {
                // 步进：base/step
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
                // 范围：start-end
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

    /** 将字符串解析为整数，支持名称缩写。 */
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

    // ─── 集合工具 ─────────────────────────────────────────────────────────────

    /** 返回集合中第一个（最小）元素，集合保证非空。 */
    private static int first(Set<Integer> set) {
        return set.iterator().next();
    }

    /**
     * 返回集合中第一个严格大于 {@code current} 的值；若不存在则返回 -1。
     */
    private static int nextValue(Set<Integer> set, int current) {
        for (int v : set) {
            if (v > current) return v;
        }
        return -1;
    }
}
