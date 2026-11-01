package emaki.jiuwu.craft.dungeon.limit;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.dungeon.config.AppConfig;
import emaki.jiuwu.craft.dungeon.config.DungeonConfig;
import emaki.jiuwu.craft.dungeon.config.DungeonConfig.DailyWindow;
import emaki.jiuwu.craft.dungeon.snapshot.PlayersDataStore;
import emaki.jiuwu.craft.dungeon.snapshot.PlayersDataStore.PlayerData;
import java.time.DayOfWeek;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class LimitService {

    public enum Verdict {
        ALLOWED,
        DENY_COOLDOWN,
        DENY_DAILY_LIMIT,
        DENY_WEEKLY_LIMIT,
        DENY_MONTHLY_LIMIT,
        DENY_DAILY_WINDOW,
        DENY_WEEKLY_DAY,
        DENY_MONTHLY_WEEK
    }

    public record Result(Verdict verdict, long retrySeconds) {

        public static Result allowed() {
            return new Result(Verdict.ALLOWED, 0L);
        }
    }

    private final PlayersDataStore store;
    private final Function<String, DungeonConfig> configOf;
    private final Supplier<AppConfig> appConfig;
    private final DebugLogger debug;
    private final BiConsumer<String, String> resetListener;

    public LimitService(PlayersDataStore store,
            Function<String, DungeonConfig> configOf,
            Supplier<AppConfig> appConfig,
            DebugLogger debug,
            BiConsumer<String, String> resetListener) {
        this.store = store;
        this.configOf = configOf;
        this.appConfig = appConfig;
        this.debug = debug;
        this.resetListener = resetListener;
    }

    public Result check(UUID playerId, String dungeonId) {
        DungeonConfig config = configOf.apply(dungeonId);
        if (config == null || playerId == null) {
            return Result.allowed();
        }
        PlayerData data = store.read(playerId);
        Map<String, Object> limits = data.limits();
        @SuppressWarnings("unchecked")
        Map<String, Object> dungeonLimits = limits.get(dungeonId) instanceof Map<?, ?> raw
                ? (Map<String, Object>) raw
                : new LinkedHashMap<>();
        ZonedDateTime now = now();
        long cooldownUntil = longOf(dungeonLimits.get("cooldown-until"));
        if (cooldownUntil > now.toInstant().toEpochMilli()) {
            return new Result(Verdict.DENY_COOLDOWN,
                    (cooldownUntil - now.toInstant().toEpochMilli() + 999L) / 1000L);
        }
        if (bucketChanged(dungeonLimits, "monthly", monthBucket(now))) {
            fireReset(dungeonId, "month");
        }
        if (!config.limitsMonthlyWeeks().isEmpty()
                && !config.limitsMonthlyWeeks().contains(monthWeek(now))) {
            return new Result(Verdict.DENY_MONTHLY_WEEK, secondsUntilNextWeek(now));
        }
        if (config.limitsMonthly() > 0) {
            int used = bucketCount(dungeonLimits, "monthly", monthBucket(now));
            if (used >= config.limitsMonthly()) {
                return new Result(Verdict.DENY_MONTHLY_LIMIT, secondsUntilNextMonth(now));
            }
        }
        if (bucketChanged(dungeonLimits, "weekly", weekBucket(now))) {
            fireReset(dungeonId, "week");
        }
        if (!config.limitsWeeklyDays().isEmpty()
                && !config.limitsWeeklyDays().contains(now.getDayOfWeek().getValue())) {
            return new Result(Verdict.DENY_WEEKLY_DAY,
                    secondsUntilNextAllowedDay(now, config.limitsWeeklyDays()));
        }
        if (config.limitsWeeklyCount() > 0) {
            int used = bucketCount(dungeonLimits, "weekly", weekBucket(now));
            if (used >= config.limitsWeeklyCount()) {
                return new Result(Verdict.DENY_WEEKLY_LIMIT, secondsUntilNextWeek(now));
            }
        }
        if (bucketChanged(dungeonLimits, "daily", dayBucket(now))) {
            fireReset(dungeonId, "day");
        }
        DailyWindow window = config.limitsDailyWindow();
        if (window != null && !window.contains(now.getHour() * 60 + now.getMinute())) {
            return new Result(Verdict.DENY_DAILY_WINDOW,
                    secondsUntilWindowStart(now, window.startMinute()));
        }
        if (config.limitsDaily() > 0) {
            int used = bucketCount(dungeonLimits, "daily", dayBucket(now));
            if (used >= config.limitsDaily()) {
                return new Result(Verdict.DENY_DAILY_LIMIT, secondsUntilTomorrow(now));
            }
        }
        return Result.allowed();
    }

    private static boolean bucketChanged(Map<String, Object> dungeonLimits, String kind, String bucket) {
        if (!(dungeonLimits.get(kind) instanceof Map<?, ?> counts) || counts.isEmpty()) {
            return false;
        }
        return !counts.containsKey(bucket);
    }

    public void record(UUID playerId, String dungeonId) {
        DungeonConfig config = configOf.apply(dungeonId);
        if (config == null || playerId == null) {
            return;
        }
        ZonedDateTime now = now();
        String dayBucket = dayBucket(now);
        store.updateAsync(playerId, data -> {
            Map<String, Object> limits = new LinkedHashMap<>(data.limits());
            @SuppressWarnings("unchecked")
            Map<String, Object> dungeonLimits = limits.get(dungeonId) instanceof Map<?, ?> raw
                    ? cast(raw)
                    : new LinkedHashMap<>();
            if (config.limitsDaily() > 0) {
                increment(dungeonLimits, "daily", dayBucket);
            }
            if (config.limitsWeeklyCount() > 0) {
                increment(dungeonLimits, "weekly", weekBucket(now));
            }
            if (config.limitsMonthly() > 0) {
                increment(dungeonLimits, "monthly", monthBucket(now));
            }
            if (config.cooldownSeconds() > 0) {
                dungeonLimits.put("cooldown-until",
                        now.toInstant().toEpochMilli() + config.cooldownSeconds() * 1000L);
            }
            limits.put(dungeonId, dungeonLimits);
            data.setLimits(limits);
        });
        debug.log("dungeon", playerId, "limit_recorded", Map.of(
                "dungeon", dungeonId,
                "day", String.valueOf(dayBucket)
        ));
    }

    public Map<String, Object> snapshot(UUID playerId, String dungeonId) {
        if (playerId == null) {
            return Map.of();
        }
        PlayerData data = store.read(playerId);
        if (dungeonId != null && data.limits().get(dungeonId) instanceof Map<?, ?> raw) {
            return cast(raw);
        }
        return data.limits();
    }

    public Map<String, Object> status(UUID playerId, String dungeonId) {
        DungeonConfig config = configOf.apply(dungeonId);
        Map<String, Object> result = new LinkedHashMap<>();
        if (config == null || playerId == null) {
            return result;
        }
        PlayerData data = store.read(playerId);
        @SuppressWarnings("unchecked")
        Map<String, Object> dungeonLimits = data.limits().get(dungeonId) instanceof Map<?, ?> raw
                ? (Map<String, Object>) raw
                : new LinkedHashMap<>();
        ZonedDateTime now = now();
        long cooldownUntil = longOf(dungeonLimits.get("cooldown-until"));
        long cooldownRemaining = Math.max(0L,
                (cooldownUntil - now.toInstant().toEpochMilli() + 999L) / 1000L);
        result.put("cooldown-seconds", cooldownRemaining);
        result.put("daily-total", config.limitsDaily());
        result.put("daily-used",
                config.limitsDaily() <= 0 ? 0
                        : bucketCount(dungeonLimits, "daily", dayBucket(now)));
        result.put("weekly-total", config.limitsWeeklyCount());
        result.put("weekly-used",
                config.limitsWeeklyCount() <= 0 ? 0
                        : bucketCount(dungeonLimits, "weekly", weekBucket(now)));
        result.put("monthly-total", config.limitsMonthly());
        result.put("monthly-used",
                config.limitsMonthly() <= 0 ? 0
                        : bucketCount(dungeonLimits, "monthly", monthBucket(now)));
        return result;
    }

    private void fireReset(String dungeonId, String period) {
        if (resetListener != null) {
            resetListener.accept(dungeonId, period);
        }
    }

    private ZonedDateTime now() {
        String timezone = appConfig.get().timezone();
        ZoneId zone = timezone == null || timezone.isBlank()
                ? ZoneId.systemDefault()
                : ZoneId.of(timezone);
        return ZonedDateTime.now(zone);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static String dayBucket(ZonedDateTime now) {
        return now.toLocalDate().toString();
    }

    private static String weekBucket(ZonedDateTime now) {
        LocalDate monday = now.toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return monday.toString();
    }

    private static String monthBucket(ZonedDateTime now) {
        return YearMonth.from(now).toString();
    }

    private static int monthWeek(ZonedDateTime now) {
        LocalDate date = now.toLocalDate();
        int firstOffset = date.withDayOfMonth(1).getDayOfWeek().getValue() - 1;
        return (date.getDayOfMonth() - 1 + firstOffset) / 7 + 1;
    }

    private static int bucketCount(Map<String, Object> dungeonLimits, String kind, String bucket) {
        if (!(dungeonLimits.get(kind) instanceof Map<?, ?> counts)) {
            return 0;
        }
        Object value = counts.get(bucket);
        return value instanceof Number number ? number.intValue() : 0;
    }

    @SuppressWarnings("unchecked")
    private static void increment(Map<String, Object> dungeonLimits, String kind, String bucket) {
        Map<String, Object> counts = dungeonLimits.get(kind) instanceof Map<?, ?> raw
                ? cast(raw)
                : new LinkedHashMap<>();
        Object current = counts.get(bucket);
        int value = current instanceof Number number ? number.intValue() : 0;
        counts.put(bucket, value + 1);
        dungeonLimits.put(kind, counts);
    }

    private static long longOf(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static long secondsUntilTomorrow(ZonedDateTime now) {
        ZonedDateTime tomorrow = now.toLocalDate().plusDays(1).atStartOfDay(now.getZone());
        return Math.max(1L, now.until(tomorrow, ChronoUnit.SECONDS));
    }

    private static long secondsUntilNextWeek(ZonedDateTime now) {
        LocalDate monday = now.toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .plusWeeks(1);
        ZonedDateTime start = monday.atStartOfDay(now.getZone());
        return Math.max(1L, now.until(start, ChronoUnit.SECONDS));
    }

    private static long secondsUntilNextMonth(ZonedDateTime now) {
        ZonedDateTime next = YearMonth.from(now).plusMonths(1).atDay(1).atStartOfDay(now.getZone());
        return Math.max(1L, now.until(next, ChronoUnit.SECONDS));
    }

    private static long secondsUntilNextAllowedDay(ZonedDateTime now, List<Integer> days) {
        for (int offset = 1; offset <= 7; offset++) {
            LocalDate day = now.toLocalDate().plusDays(offset);
            if (days.contains(day.getDayOfWeek().getValue())) {
                ZonedDateTime start = day.atStartOfDay(now.getZone());
                return Math.max(1L, now.until(start, ChronoUnit.SECONDS));
            }
        }
        return 86_400L;
    }

    private static long secondsUntilWindowStart(ZonedDateTime now, int startMinute) {
        ZonedDateTime start = now.toLocalDate().atStartOfDay(now.getZone()).plusMinutes(startMinute);
        if (!start.isAfter(now)) {
            start = start.plusDays(1);
        }
        return Math.max(1L, now.until(start, ChronoUnit.SECONDS));
    }
}
