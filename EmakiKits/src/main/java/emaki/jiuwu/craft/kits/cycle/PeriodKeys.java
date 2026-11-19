package emaki.jiuwu.craft.kits.cycle;

import emaki.jiuwu.craft.kits.model.KitType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.time.Instant;

public final class PeriodKeys {

    private PeriodKeys() {
    }

    public static String current(KitType type, String timeZone, long nowMillis) {
        ZoneId zone = ZoneId.of(timeZone);
        LocalDate date = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate();
        return switch (type) {
            case DAILY -> "d:" + date;
            case WEEKLY -> "w:" + date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTHLY -> "m:" + date.withDayOfMonth(1);
            default -> "once";
        };
    }

    public static String currentFor(KitType type, String timeZone) {
        if (type != KitType.DAILY && type != KitType.WEEKLY && type != KitType.MONTHLY) {
            return "once";
        }
        return current(type, timeZone, System.currentTimeMillis());
    }

    public static String previous(KitType type, String timeZone, long nowMillis) {
        ZoneId zone = ZoneId.of(timeZone);
        LocalDate date = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate();
        return switch (type) {
            case DAILY -> "d:" + date.minusDays(1);
            case WEEKLY -> "w:" + date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
            case MONTHLY -> "m:" + date.withDayOfMonth(1).minusMonths(1);
            default -> "once";
        };
    }

    public static String previousFor(KitType type, String timeZone) {
        if (type != KitType.DAILY && type != KitType.WEEKLY && type != KitType.MONTHLY) {
            return "once";
        }
        return previous(type, timeZone, System.currentTimeMillis());
    }
}
