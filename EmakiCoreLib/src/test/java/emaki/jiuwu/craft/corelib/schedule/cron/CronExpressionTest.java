package emaki.jiuwu.craft.corelib.schedule.cron;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Cron 表达式解析与下次执行时刻")
class CronExpressionTest {

    private static final ZoneId ZONE = ZoneId.of("UTC");

    private static ZonedDateTime at(int year, int month, int day, int hour, int minute, int second) {
        return ZonedDateTime.of(year, month, day, hour, minute, second, 0, ZONE);
    }

    @Test
    @DisplayName("字段数不是 6 时拒绝解析")
    void rejectsWrongFieldCount() {
        assertThrows(CronParseException.class, () -> CronExpression.parse("* * * * *"));
        assertThrows(CronParseException.class, () -> CronExpression.parse("* * * * * * *"));
    }

    @Test
    @DisplayName("null 与空白表达式拒绝解析")
    void rejectsBlank() {
        assertThrows(CronParseException.class, () -> CronExpression.parse(null));
        assertThrows(CronParseException.class, () -> CronExpression.parse("   "));
    }

    @Test
    @DisplayName("越界数值拒绝解析")
    void rejectsOutOfRange() {
        assertThrows(CronParseException.class, () -> CronExpression.parse("60 * * * * *"));
        assertThrows(CronParseException.class, () -> CronExpression.parse("* * 24 * * *"));
        assertThrows(CronParseException.class, () -> CronExpression.parse("* * * * 13 *"));
    }

    @Test
    @DisplayName("倒序区间拒绝解析")
    void rejectsInvertedRange() {
        assertThrows(CronParseException.class, () -> CronExpression.parse("* * 10-5 * * *"));
    }

    @Test
    @DisplayName("每秒表达式的下次执行是下一秒")
    void everySecondAdvancesOneSecond() {
        ZonedDateTime next = CronExpression.parse("* * * * * *")
                .nextExecution(at(2026, 3, 2, 14, 37, 10));
        assertEquals(at(2026, 3, 2, 14, 37, 11), next);
    }

    @Test
    @DisplayName("整分表达式跳到下一个 0 秒")
    void everyMinuteJumpsToNextZeroSecond() {
        ZonedDateTime next = CronExpression.parse("0 * * * * *")
                .nextExecution(at(2026, 3, 2, 14, 37, 10));
        assertEquals(at(2026, 3, 2, 14, 38, 0), next);
    }

    @Test
    @DisplayName("每日固定时刻在当日已过时顺延到次日")
    void dailyTimeRollsToNextDay() {
        CronExpression daily = CronExpression.parse("0 30 3 * * *");

        assertEquals(at(2026, 3, 2, 3, 30, 0), daily.nextExecution(at(2026, 3, 2, 1, 0, 0)));
        assertEquals(at(2026, 3, 3, 3, 30, 0), daily.nextExecution(at(2026, 3, 2, 5, 0, 0)));
    }

    @Test
    @DisplayName("步进值按间隔展开")
    void stepValuesExpand() {
        CronExpression everyQuarter = CronExpression.parse("0 0/15 * * * *");

        assertEquals(at(2026, 3, 2, 14, 15, 0), everyQuarter.nextExecution(at(2026, 3, 2, 14, 3, 0)));
        assertEquals(at(2026, 3, 2, 14, 30, 0), everyQuarter.nextExecution(at(2026, 3, 2, 14, 15, 0)));
        assertEquals(at(2026, 3, 2, 15, 0, 0), everyQuarter.nextExecution(at(2026, 3, 2, 14, 45, 0)));
    }

    @Test
    @DisplayName("列举值只在列出的时刻命中")
    void listedValuesOnly() {
        CronExpression twice = CronExpression.parse("0 0 6,18 * * *");

        assertEquals(at(2026, 3, 2, 6, 0, 0), twice.nextExecution(at(2026, 3, 2, 0, 0, 0)));
        assertEquals(at(2026, 3, 2, 18, 0, 0), twice.nextExecution(at(2026, 3, 2, 6, 0, 0)));
        assertEquals(at(2026, 3, 3, 6, 0, 0), twice.nextExecution(at(2026, 3, 2, 18, 0, 0)));
    }

    @Test
    @DisplayName("月份名与星期名可用英文缩写")
    void namedFieldsParse() {
        assertNotNull(CronExpression.parse("0 0 12 * JAN *"));
        assertNotNull(CronExpression.parse("0 0 12 ? * MON"));
    }

    @Test
    @DisplayName("星期字段是 1=周日 的 Quartz 约定")
    void dayOfWeekUsesSundayAsOne() {
        ZonedDateTime next = CronExpression.parse("0 0 12 ? * 1")
                .nextExecution(at(2026, 3, 2, 0, 0, 0));

        assertNotNull(next);
        assertEquals(DayOfWeek.SUNDAY, next.getDayOfWeek());
        assertEquals(12, next.getHour());
    }

    @Test
    @DisplayName("星期一对应字段值 2")
    void mondayIsTwo() {
        ZonedDateTime next = CronExpression.parse("0 0 12 ? * 2")
                .nextExecution(at(2026, 3, 2, 13, 0, 0));

        assertNotNull(next);
        assertEquals(DayOfWeek.MONDAY, next.getDayOfWeek());
    }

    @Test
    @DisplayName("指定月内日期跨月顺延")
    void dayOfMonthRollsToNextMonth() {
        ZonedDateTime next = CronExpression.parse("0 0 0 1 * *")
                .nextExecution(at(2026, 3, 2, 0, 0, 0));
        assertEquals(at(2026, 4, 1, 0, 0, 0), next);
    }

    @Test
    @DisplayName("指定月份跨年顺延")
    void monthRollsToNextYear() {
        ZonedDateTime next = CronExpression.parse("0 0 0 1 1 *")
                .nextExecution(at(2026, 3, 2, 0, 0, 0));
        assertEquals(at(2027, 1, 1, 0, 0, 0), next);
    }

    @Test
    @DisplayName("闰日只在闰年命中")
    void leapDayOnlyInLeapYear() {
        ZonedDateTime next = CronExpression.parse("0 0 0 29 2 *")
                .nextExecution(at(2026, 3, 1, 0, 0, 0));

        assertNotNull(next);
        assertEquals(2028, next.getYear());
        assertEquals(2, next.getMonthValue());
        assertEquals(29, next.getDayOfMonth());
    }

    @Test
    @DisplayName("下次执行时刻严格晚于起点，不会返回起点自身")
    void nextIsStrictlyAfterFrom() {
        ZonedDateTime from = at(2026, 3, 2, 14, 37, 10);
        assertTrue(CronExpression.parse("* * * * * *").nextExecution(from).isAfter(from));
        assertTrue(CronExpression.parse("10 37 14 * * *").nextExecution(from).isAfter(from));
    }

    @Test
    @DisplayName("区间字段展开为闭区间")
    void rangeIsInclusive() {
        CronExpression businessHours = CronExpression.parse("0 0 9-17 * * *");

        assertEquals(at(2026, 3, 2, 9, 0, 0), businessHours.nextExecution(at(2026, 3, 2, 8, 0, 0)));
        assertEquals(at(2026, 3, 2, 17, 0, 0), businessHours.nextExecution(at(2026, 3, 2, 16, 30, 0)));
        assertEquals(at(2026, 3, 3, 9, 0, 0), businessHours.nextExecution(at(2026, 3, 2, 17, 0, 0)));
    }

    @Test
    @DisplayName("? 与 * 在日期字段等价")
    void questionMarkEqualsWildcard() {
        ZonedDateTime from = at(2026, 3, 2, 0, 0, 0);
        assertEquals(CronExpression.parse("0 0 12 * * *").nextExecution(from),
                CronExpression.parse("0 0 12 ? * ?").nextExecution(from));
    }
}
