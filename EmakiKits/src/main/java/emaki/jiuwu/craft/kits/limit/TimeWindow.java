package emaki.jiuwu.craft.kits.limit;

import emaki.jiuwu.craft.corelib.schedule.cron.CronExpression;
import emaki.jiuwu.craft.corelib.schedule.cron.CronParseException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class TimeWindow {

    public enum Mode { ALWAYS, RANGE, CRON }

    private final Mode mode;
    private final long startMillis;
    private final long endMillis;
    private final CronExpression cron;
    private final long durationSeconds;
    private final String zoneId;

    private TimeWindow(Mode mode, long startMillis, long endMillis,
                       CronExpression cron, long durationSeconds, String zoneId) {
        this.mode = mode;
        this.startMillis = startMillis;
        this.endMillis = endMillis;
        this.cron = cron;
        this.durationSeconds = Math.max(0L, durationSeconds);
        this.zoneId = zoneId;
    }

    public static TimeWindow always() {
        return new TimeWindow(Mode.ALWAYS, 0L, 0L, null, 0L, "Asia/Shanghai");
    }

    public static TimeWindow range(long startMillis, long endMillis) {
        return new TimeWindow(Mode.RANGE, startMillis, endMillis, null, 0L, "Asia/Shanghai");
    }

    public static TimeWindow cron(String expression, long durationSeconds, String zoneId)
            throws CronParseException {
        return new TimeWindow(Mode.CRON, 0L, 0L,
                CronExpression.parse(expression), durationSeconds,
                zoneId == null ? "Asia/Shanghai" : zoneId);
    }

    public boolean contains(long nowMillis) {
        return switch (mode) {
            case ALWAYS -> true;
            case RANGE -> nowMillis >= startMillis && nowMillis <= endMillis;
            case CRON -> cronMatches(nowMillis);
        };
    }

    private boolean cronMatches(long nowMillis) {
        ZoneId zone = ZoneId.of(zoneId);
        ZonedDateTime now = Instant.ofEpochMilli(nowMillis).atZone(zone);
        ZonedDateTime probe = now.minusSeconds(Math.max(1L, durationSeconds));
        ZonedDateTime lastFire = null;
        for (int i = 0; i < 8; i++) {
            ZonedDateTime candidate = cron.nextExecution(probe);
            if (candidate == null || candidate.isAfter(now)) {
                break;
            }
            lastFire = candidate;
            probe = candidate;
        }
        if (lastFire == null) {
            return false;
        }
        return !now.isAfter(lastFire.plusSeconds(durationSeconds));
    }

    public Mode mode() {
        return mode;
    }

    public long durationSeconds() {
        return durationSeconds;
    }

    public String zoneId() {
        return zoneId;
    }
}
