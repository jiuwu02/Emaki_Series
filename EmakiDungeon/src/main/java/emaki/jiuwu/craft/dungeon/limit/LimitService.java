package emaki.jiuwu.craft.dungeon.limit;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.dungeon.config.AppConfig;
import emaki.jiuwu.craft.dungeon.config.DungeonConfig;
import emaki.jiuwu.craft.dungeon.snapshot.PlayersDataStore;
import emaki.jiuwu.craft.dungeon.snapshot.PlayersDataStore.PlayerData;

public final class LimitService {

    public enum Verdict {
        ALLOWED,
        DENY_COOLDOWN,
        DENY_DAILY_LIMIT
    }

    public record Result(Verdict verdict, long retrySeconds) {

        public static Result allowed() {
            return new Result(Verdict.ALLOWED, 0L);
        }
    }

    private final PlayersDataStore store;
    private final java.util.function.Function<String, DungeonConfig> configOf;
    private final java.util.function.Supplier<AppConfig> appConfig;
    private final DebugLogger debug;
    private final java.util.function.BiConsumer<String, String> resetListener;

    public LimitService(PlayersDataStore store,
            java.util.function.Function<String, DungeonConfig> configOf,
            java.util.function.Supplier<AppConfig> appConfig,
            DebugLogger debug,
            java.util.function.BiConsumer<String, String> resetListener) {
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
        String dayBucket = dayBucket(now);
        if (bucketChanged(dungeonLimits, "daily", dayBucket)) {
            fireReset(dungeonId, "day");
        }
        if (config.limitsDaily() > 0) {
            int used = bucketCount(dungeonLimits, "daily", dayBucket);
            if (used >= config.limitsDaily()) {
                return new Result(Verdict.DENY_DAILY_LIMIT, secondsUntilTomorrow(now));
            }
        }
        int weeklyLimit = config.limitsWeekly().getOrDefault(now.getDayOfWeek().getValue(), 0);
        if (bucketChanged(dungeonLimits, "weekly", weekBucket(now))) {
            fireReset(dungeonId, "week");
        }
        if (weeklyLimit > 0) {
            String bucket = weekBucket(now);
            int used = bucketCount(dungeonLimits, "weekly", bucket);
            if (used >= weeklyLimit) {
                return new Result(Verdict.DENY_DAILY_LIMIT, secondsUntilTomorrow(now));
            }
        }
        if (bucketChanged(dungeonLimits, "monthly", monthBucket(now))) {
            fireReset(dungeonId, "month");
        }
        if (config.limitsMonthly() > 0) {
            String bucket = monthBucket(now);
            int used = bucketCount(dungeonLimits, "monthly", bucket);
            if (used >= config.limitsMonthly()) {
                return new Result(Verdict.DENY_DAILY_LIMIT, secondsUntilNextMonth(now));
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
        PlayerData data = store.read(playerId);
        Map<String, Object> limits = new LinkedHashMap<>(data.limits());
        @SuppressWarnings("unchecked")
        Map<String, Object> dungeonLimits = limits.get(dungeonId) instanceof Map<?, ?> raw
                ? cast(raw)
                : new LinkedHashMap<>();
        ZonedDateTime now = now();
        String dayBucket = dayBucket(now);
        if (config.limitsDaily() > 0) {
            increment(dungeonLimits, "daily", dayBucket);
        }
        int weeklyLimit = config.limitsWeekly().getOrDefault(now.getDayOfWeek().getValue(), 0);
        if (weeklyLimit > 0) {
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
        store.writeAsync(playerId, data);
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
        result.put("weekly-total",
                config.limitsWeekly().getOrDefault(now.getDayOfWeek().getValue(), 0));
        result.put("weekly-used",
                bucketCount(dungeonLimits, "weekly", weekBucket(now)));
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
                .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        return monday + "-D" + now.getDayOfWeek().getValue();
    }

    private static String monthBucket(ZonedDateTime now) {
        return YearMonth.from(now).toString();
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
                ? (Map<String, Object>) raw
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
        return Math.max(1L, now.until(tomorrow, java.time.temporal.ChronoUnit.SECONDS));
    }

    private static long secondsUntilNextMonth(ZonedDateTime now) {
        ZonedDateTime next = YearMonth.from(now).plusMonths(1).atDay(1).atStartOfDay(now.getZone());
        return Math.max(1L, now.until(next, java.time.temporal.ChronoUnit.SECONDS));
    }
}
