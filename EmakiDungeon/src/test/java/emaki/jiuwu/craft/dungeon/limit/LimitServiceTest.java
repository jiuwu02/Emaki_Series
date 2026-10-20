package emaki.jiuwu.craft.dungeon.limit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.dungeon.config.AppConfig;
import emaki.jiuwu.craft.dungeon.config.DungeonConfig;
import emaki.jiuwu.craft.dungeon.config.DungeonConfig.DailyWindow;
import emaki.jiuwu.craft.dungeon.snapshot.PlayersDataStore;
import emaki.jiuwu.craft.dungeon.testutil.FakeDispatcher;

class LimitServiceTest {

    private static final String DUNGEON = "d1";
    private static final ZoneId UTC = ZoneId.of("UTC");

    @TempDir
    java.nio.file.Path tempDir;

    private FakeDispatcher dispatcher;
    private PlayersDataStore store;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        dispatcher = new FakeDispatcher();
        store = new PlayersDataStore(MockBukkit.createMockPlugin("EmakiDungeonTest"),
                dispatcher, tempDir.toFile(), false);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static AppConfig appConfig(String timezone) {
        return new AppConfig(
                "zh_CN", "1.0.1", false, 30, 20, "blacklist", List.of(), List.of(),
                4, "public", false, timezone, "disabled", 10, true,
                true, 5000L, false, 50, 64, 5, Map.of());
    }

    private static DungeonConfig limits(int daily, DailyWindow dailyWindow,
            int weeklyCount, List<Integer> weeklyDays,
            int monthly, List<Integer> monthlyWeeks, int cooldown) {
        DungeonConfig base = DungeonConfig.defaults();
        return new DungeonConfig(
                base.displayName(), base.poolMaxWorlds(), base.prepareWindowSeconds(),
                base.waitChannel(), base.waitTimeoutSeconds(), base.difficulties(),
                base.dropModeAllowed(), base.commandsAllow(), base.respawnCounts(),
                base.timeoutSeconds(), base.timerMessages(), base.scoreWeights(),
                base.dropLevels(), base.dropDefaultMode(), base.difficultyTunings(),
                daily, dailyWindow, weeklyCount, weeklyDays, monthly, monthlyWeeks,
                cooldown, base.leaderboardMaxEntries(), base.settlementCommands());
    }

    private LimitService service(DungeonConfig config, List<String> resets) {
        return new LimitService(
                store,
                dungeonId -> DUNGEON.equals(dungeonId) ? config : null,
                () -> appConfig("UTC"),
                new DebugLogger(MockBukkit.createMockPlugin("EmakiDungeonTest"),
                        new LanguageLoader(MockBukkit.createMockPlugin("EmakiDungeonTest"))),
                (dungeonId, period) -> {
                    if (resets != null) {
                        resets.add(period);
                    }
                });
    }

    @Test
    void dailyLimitCountsUntilExhausted() {
        LimitService service = service(limits(2, null, 0, List.of(), 0, List.of(), 0), null);
        UUID player = UUID.randomUUID();
        assertEquals(LimitService.Verdict.ALLOWED, service.check(player, DUNGEON).verdict());
        service.record(player, DUNGEON);
        assertEquals(LimitService.Verdict.ALLOWED, service.check(player, DUNGEON).verdict());
        service.record(player, DUNGEON);
        LimitService.Result denied = service.check(player, DUNGEON);
        assertEquals(LimitService.Verdict.DENY_DAILY_LIMIT, denied.verdict());
        assertTrue(denied.retrySeconds() > 0L);
        Map<String, Object> status = service.status(player, DUNGEON);
        assertEquals(2, status.get("daily-used"));
        assertEquals(2, status.get("daily-total"));
    }

    @Test
    void monthlyLimitOutranksDailyLimit() {
        LimitService service = service(limits(3, null, 0, List.of(), 1, List.of(), 0), null);
        UUID player = UUID.randomUUID();
        service.record(player, DUNGEON);
        LimitService.Result denied = service.check(player, DUNGEON);
        assertEquals(LimitService.Verdict.DENY_MONTHLY_LIMIT, denied.verdict());
        assertNotEquals(LimitService.Verdict.DENY_DAILY_LIMIT, denied.verdict());
        assertTrue(denied.retrySeconds() > 0L);
    }

    @Test
    void weeklyLimitOutranksDailyLimit() {
        LimitService service = service(limits(5, null, 1, List.of(), 0, List.of(), 0), null);
        UUID player = UUID.randomUUID();
        service.record(player, DUNGEON);
        assertEquals(LimitService.Verdict.DENY_WEEKLY_LIMIT,
                service.check(player, DUNGEON).verdict());
    }

    @Test
    void weeklyDayGateAcceptsConfiguredToday() {
        int today = LocalDate.now(UTC).getDayOfWeek().getValue();
        LimitService allowed = service(
                limits(0, null, 0, List.of(today), 0, List.of(), 0), null);
        assertEquals(LimitService.Verdict.ALLOWED,
                allowed.check(UUID.randomUUID(), DUNGEON).verdict());

        int otherDay = today == 7 ? 6 : today + 1;
        LimitService denied = service(
                limits(0, null, 0, List.of(otherDay), 0, List.of(), 0), null);
        assertEquals(LimitService.Verdict.DENY_WEEKLY_DAY,
                denied.check(UUID.randomUUID(), DUNGEON).verdict());
    }

    @Test
    void monthlyWeekGateAcceptsCurrentIsoWeek() {
        ZonedDateTime now = ZonedDateTime.now(UTC);
        LocalDate date = now.toLocalDate();
        int firstOffset = date.withDayOfMonth(1).getDayOfWeek().getValue() - 1;
        int currentWeek = (date.getDayOfMonth() - 1 + firstOffset) / 7 + 1;
        int otherWeek = currentWeek == 1 ? 2 : 1;
        LimitService allowed = service(
                limits(0, null, 0, List.of(), 0, List.of(currentWeek), 0), null);
        assertEquals(LimitService.Verdict.ALLOWED,
                allowed.check(UUID.randomUUID(), DUNGEON).verdict());
        LimitService denied = service(
                limits(0, null, 0, List.of(), 0, List.of(otherWeek), 0), null);
        assertEquals(LimitService.Verdict.DENY_MONTHLY_WEEK,
                denied.check(UUID.randomUUID(), DUNGEON).verdict());
    }

    @Test
    void dailyWindowGateRejectsOutsideMinutesAndAcceptsAnyTimeWhenOpen() {
        int nowMinute = ZonedDateTime.now(UTC).getHour() * 60 + ZonedDateTime.now(UTC).getMinute();
        int start = (nowMinute + 1) % 1440;
        int end = (nowMinute + 2) % 1440;
        LimitService outside = service(
                limits(0, new DailyWindow(start, end), 0, List.of(), 0, List.of(), 0), null);
        assertEquals(LimitService.Verdict.DENY_DAILY_WINDOW,
                outside.check(UUID.randomUUID(), DUNGEON).verdict());

        LimitService open = service(
                limits(0, new DailyWindow(720, 720), 0, List.of(), 0, List.of(), 0), null);
        assertEquals(LimitService.Verdict.ALLOWED,
                open.check(UUID.randomUUID(), DUNGEON).verdict());
    }

    @Test
    void cooldownDeniesRightAfterRecord() {
        LimitService service = service(limits(0, null, 0, List.of(), 0, List.of(), 600), null);
        UUID player = UUID.randomUUID();
        assertEquals(LimitService.Verdict.ALLOWED, service.check(player, DUNGEON).verdict());
        service.record(player, DUNGEON);
        LimitService.Result denied = service.check(player, DUNGEON);
        assertEquals(LimitService.Verdict.DENY_COOLDOWN, denied.verdict());
        assertTrue(denied.retrySeconds() > 0L && denied.retrySeconds() <= 600L);
    }

    @Test
    void staleBucketsFireResetListenerForAllPeriods() {
        List<String> resets = new ArrayList<>();
        LimitService service = service(
                limits(0, null, 0, List.of(), 0, List.of(), 0), resets);
        UUID player = UUID.randomUUID();
        PlayersDataStore.PlayerData data = store.read(player);
        Map<String, Object> dungeonLimits = new LinkedHashMap<>();
        dungeonLimits.put("monthly", new LinkedHashMap<String, Object>(
                Map.of(YearMonth.of(2020, 1).toString(), 5)));
        dungeonLimits.put("weekly", new LinkedHashMap<String, Object>(
                Map.of(LocalDate.of(2020, 1, 6).toString(), 9)));
        dungeonLimits.put("daily", new LinkedHashMap<String, Object>(
                Map.of(LocalDate.of(2020, 1, 6).toString(), 9)));
        Map<String, Object> limits = new LinkedHashMap<>();
        limits.put(DUNGEON, dungeonLimits);
        data.setLimits(limits);
        store.writeSync(player, data);

        service.check(player, DUNGEON);
        assertTrue(resets.contains("month"));
        assertTrue(resets.contains("week"));
        assertTrue(resets.contains("day"));
    }

    @Test
    void configuredTimezoneDrivesBuckets() {
        List<String> resets = new ArrayList<>();
        LimitService service = new LimitService(
                store,
                dungeonId -> DUNGEON.equals(dungeonId)
                        ? limits(1, null, 0, List.of(), 0, List.of(), 0) : null,
                () -> appConfig("Asia/Tokyo"),
                new DebugLogger(MockBukkit.createMockPlugin("EmakiDungeonTest"),
                        new LanguageLoader(MockBukkit.createMockPlugin("EmakiDungeonTest"))),
                (dungeonId, period) -> resets.add(period));
        UUID player = UUID.randomUUID();
        assertEquals(LimitService.Verdict.ALLOWED, service.check(player, DUNGEON).verdict());
        service.record(player, DUNGEON);
        Map<String, Object> status = service.status(player, DUNGEON);
        assertEquals(1, status.get("daily-used"));
        assertEquals(LimitService.Verdict.DENY_DAILY_LIMIT,
                service.check(player, DUNGEON).verdict());
    }
}
