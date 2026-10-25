package emaki.jiuwu.craft.dungeon.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.dungeon.score.ScoreWeights;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record DungeonConfig(
        String displayName,
        int poolMaxWorlds,
        int prepareWindowSeconds,
        String waitChannel,
        int waitTimeoutSeconds,
        List<String> difficulties,
        List<String> dropModeAllowed,
        List<String> commandsAllow,
        Map<String, Integer> respawnCounts,
        int timeoutSeconds,
        List<Integer> timerMessages,
        ScoreWeights scoreWeights,
        Map<String, Integer> dropLevels,
        String dropDefaultMode,
        Map<String, DifficultyTuning> difficultyTunings,
        int limitsDaily,
        DailyWindow limitsDailyWindow,
        int limitsWeeklyCount,
        List<Integer> limitsWeeklyDays,
        int limitsMonthly,
        List<Integer> limitsMonthlyWeeks,
        int cooldownSeconds,
        int leaderboardMaxEntries,
        List<String> settlementCommands
) {

    public record DifficultyTuning(double mobMultiplier, double dropMultiplier) {
    }

    public record DailyWindow(int startMinute, int endMinute) {

        public boolean contains(int minuteOfDay) {
            if (startMinute == endMinute) {
                return true;
            }
            if (startMinute < endMinute) {
                return minuteOfDay >= startMinute && minuteOfDay < endMinute;
            }
            return minuteOfDay >= startMinute || minuteOfDay < endMinute;
        }

        public String format() {
            return minute(startMinute) + "-" + minute(endMinute);
        }

        private static String minute(int value) {
            int hour = value / 60;
            int minute = value % 60;
            return (hour < 10 ? "0" : "") + hour + ":" + (minute < 10 ? "0" : "") + minute;
        }
    }

    private static final List<String> DEFAULT_DIFFICULTIES = List.of("normal", "hard");
    private static final List<String> DEFAULT_MODES = List.of("free", "roll", "killer", "score", "share");
    private static final List<String> DEFAULT_DROP_LEVELS = List.of("common", "rare", "epic");

    public static DungeonConfig defaults() {
        Map<String, DifficultyTuning> tunings = new LinkedHashMap<>();
        tunings.put("normal", new DifficultyTuning(1.0D, 1.0D));
        tunings.put("hard", new DifficultyTuning(1.5D, 1.2D));
        return new DungeonConfig(
                "dungeon",
                5,
                10,
                "bossbar",
                60,
                DEFAULT_DIFFICULTIES,
                DEFAULT_MODES,
                List.of(),
                Map.of("normal", 3, "hard", 1),
                1800,
                List.of(300, 60, 10),
                ScoreWeights.defaults(),
                defaultDropLevels(),
                "free",
                Map.copyOf(tunings),
                1,
                null,
                0,
                List.of(),
                0,
                List.of(),
                0,
                100,
                List.of()
        );
    }

    public static DungeonConfig parse(YamlSection section) {
        if (section == null || section.isEmpty()) {
            throw new IllegalArgumentException("config empty");
        }
        Integer poolMaxWorlds = section.getInt("pool.max-worlds");
        if (poolMaxWorlds == null || poolMaxWorlds < 1) {
            throw new IllegalArgumentException("pool.max-worlds must be >= 1");
        }
        Integer prepareWindow = section.getInt("enter.prepare-window-seconds");
        if (prepareWindow == null || prepareWindow < 1) {
            throw new IllegalArgumentException("enter.prepare-window-seconds must be >= 1");
        }
        Integer waitTimeout = section.getInt("enter.wait-timeout-seconds");
        if (waitTimeout == null || waitTimeout < 1) {
            throw new IllegalArgumentException("enter.wait-timeout-seconds must be >= 1");
        }
        String waitChannel = section.getString("enter.wait-channel", "bossbar");
        if (!"bossbar".equals(waitChannel) && !"scoreboard".equals(waitChannel) && !"chat".equals(waitChannel)) {
            throw new IllegalArgumentException("enter.wait-channel must be bossbar/scoreboard/chat");
        }
        YamlSection difficultySection = section.getSection("difficulty");
        List<String> difficulties = new ArrayList<>();
        if (difficultySection != null) {
            difficulties.addAll(difficultySection.getKeys(false));
        }
        if (difficulties.isEmpty()) {
            difficulties = DEFAULT_DIFFICULTIES;
        }
        List<String> modeAllowed = section.getStringList("drop.mode-allowed");
        if (modeAllowed.isEmpty()) {
            modeAllowed = DEFAULT_MODES;
        }
        List<String> modeValidated = new ArrayList<>();
        for (String mode : modeAllowed) {
            if (DEFAULT_MODES.contains(mode)) {
                modeValidated.add(mode);
            }
        }
        if (modeValidated.isEmpty()) {
            modeValidated = DEFAULT_MODES;
        }
        List<String> commandsAllow = section.getStringList("commands.allow");
        Map<String, Integer> respawnCounts = new LinkedHashMap<>();
        YamlSection difficultyEntries = section.getSection("difficulty");
        if (difficultyEntries != null) {
            for (String difficulty : difficultyEntries.getKeys(false)) {
                YamlSection difficultyEntry = difficultyEntries.getSection(difficulty);
                if (difficultyEntry != null) {
                    respawnCounts.put(difficulty, difficultyEntry.getInt("respawn-count", 3));
                }
            }
        }
        if (respawnCounts.isEmpty()) {
            respawnCounts.put("normal", 3);
            respawnCounts.put("hard", 1);
        }
        Integer timeoutSeconds = section.getInt("enter.timeout-seconds", 1800);
        List<Integer> timerMessages = new ArrayList<>();
        for (Object raw : section.getList("timer.messages", List.of(300, 60, 10))) {
            if (raw instanceof Number number) {
                timerMessages.add(number.intValue());
            }
        }
        if (timerMessages.isEmpty()) {
            timerMessages = new ArrayList<>(List.of(300, 60, 10));
        }
        ScoreWeights scoreWeights = new ScoreWeights(
                section.getDouble("score.damage-weight", 1.0D),
                section.getDouble("score.kill-weight", 1.0D),
                section.getDouble("score.heal-weight", 1.0D),
                section.getDouble("score.death-weight", 1.0D),
                section.getDouble("score.defense-weight", 1.0D),
                section.getDouble("score.max", 100.0D)
        );
        if (scoreWeights.max() <= 0.0D) {
            scoreWeights = ScoreWeights.defaults();
        }
        Map<String, Integer> dropLevels = new LinkedHashMap<>();
        YamlSection levelsSection = section.getSection("drop.levels");
        if (levelsSection != null) {
            for (String level : levelsSection.getKeys(false)) {
                Integer weight = levelsSection.getInt(level, 1);
                dropLevels.put(level, weight == null || weight < 1 ? 1 : weight);
            }
        }
        if (dropLevels.isEmpty()) {
            dropLevels = defaultDropLevels();
        }
        String dropDefaultMode = section.getString("drop.default-mode", "free");
        if (!DEFAULT_MODES.contains(dropDefaultMode)) {
            dropDefaultMode = "free";
        }
        Map<String, DifficultyTuning> tunings = new LinkedHashMap<>();
        if (difficultyEntries != null) {
            for (String difficulty : difficultyEntries.getKeys(false)) {
                YamlSection difficultyEntry = difficultyEntries.getSection(difficulty);
                if (difficultyEntry != null) {
                    tunings.put(difficulty, new DifficultyTuning(
                            difficultyEntry.getDouble("mob-multiplier", 1.0D),
                            difficultyEntry.getDouble("drop-multiplier", 1.0D)));
                }
            }
        }
        if (tunings.isEmpty()) {
            tunings.put("normal", new DifficultyTuning(1.0D, 1.0D));
            tunings.put("hard", new DifficultyTuning(1.5D, 1.2D));
        }
        Integer limitsDaily = section.getInt("limits.daily.count", 0);
        DailyWindow limitsDailyWindow = parseWindow(
                section.getString("limits.daily.time-window", ""));
        Integer limitsWeeklyCount = section.getInt("limits.weekly.count", 0);
        List<Integer> limitsWeeklyDays = parseIntList(
                section.getList("limits.weekly.days"), 1, 7, "limits.weekly.days");
        Integer limitsMonthly = section.getInt("limits.monthly.count", 0);
        List<Integer> limitsMonthlyWeeks = parseIntList(
                section.getList("limits.monthly.weeks"), 1, 6, "limits.monthly.weeks");
        Integer cooldownSeconds = section.getInt("limits.cooldown-seconds", 0);
        Integer leaderboardMaxEntries = section.getInt("leaderboard.max-entries", 100);
        List<String> settlementCommands = new ArrayList<>();
        for (Object raw : section.getList("settlement.commands")) {
            if (raw != null && !String.valueOf(raw).isBlank()) {
                settlementCommands.add(String.valueOf(raw));
            }
        }
        return new DungeonConfig(
                section.getString("display-name", "dungeon"),
                poolMaxWorlds,
                prepareWindow,
                waitChannel,
                waitTimeout,
                List.copyOf(difficulties),
                List.copyOf(modeValidated),
                List.copyOf(commandsAllow),
                Map.copyOf(respawnCounts),
                timeoutSeconds == null || timeoutSeconds < 30 ? 1800 : timeoutSeconds,
                List.copyOf(timerMessages),
                scoreWeights,
                Map.copyOf(dropLevels),
                dropDefaultMode,
                Map.copyOf(tunings),
                limitsDaily == null || limitsDaily < 0 ? 0 : limitsDaily,
                limitsDailyWindow,
                limitsWeeklyCount == null || limitsWeeklyCount < 0 ? 0 : limitsWeeklyCount,
                limitsWeeklyDays,
                limitsMonthly == null || limitsMonthly < 0 ? 0 : limitsMonthly,
                limitsMonthlyWeeks,
                cooldownSeconds == null || cooldownSeconds < 0 ? 0 : cooldownSeconds,
                leaderboardMaxEntries == null || leaderboardMaxEntries < 1
                        ? 100 : leaderboardMaxEntries,
                List.copyOf(settlementCommands)
        );
    }

    private static DailyWindow parseWindow(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();
        Matcher matcher = Pattern
                .compile("^(\\d{1,2}):(\\d{2})-(\\d{1,2}):(\\d{2})$").matcher(text);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("invalid limits.daily.time-window: " + raw);
        }
        int startHour = Integer.parseInt(matcher.group(1));
        int startMinute = Integer.parseInt(matcher.group(2));
        int endHour = Integer.parseInt(matcher.group(3));
        int endMinute = Integer.parseInt(matcher.group(4));
        if (startHour > 23 || endHour > 23 || startMinute > 59 || endMinute > 59) {
            throw new IllegalArgumentException("invalid limits.daily.time-window: " + raw);
        }
        return new DailyWindow(startHour * 60 + startMinute, endHour * 60 + endMinute);
    }

    private static List<Integer> parseIntList(List<?> raw, int min, int max, String key) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<Integer> values = new ArrayList<>();
        for (Object entry : raw) {
            Integer parsed = null;
            if (entry instanceof Number number) {
                parsed = number.intValue();
            } else {
                try {
                    parsed = Integer.parseInt(String.valueOf(entry).trim());
                } catch (NumberFormatException ignored) {
                    parsed = null;
                }
            }
            if (parsed == null || parsed < min || parsed > max) {
                throw new IllegalArgumentException("invalid " + key + ": " + entry);
            }
            values.add(parsed);
        }
        return List.copyOf(values);
    }

    private static Map<String, Integer> defaultDropLevels() {
        Map<String, Integer> levels = new LinkedHashMap<>();
        for (String level : DEFAULT_DROP_LEVELS) {
            levels.put(level, 1);
        }
        levels.put("rare", 3);
        levels.put("epic", 5);
        return levels;
    }

    public double dropMultiplier(String difficulty) {
        DifficultyTuning tuning = difficultyTunings.get(difficulty);
        return tuning == null ? 1.0D : Math.max(0.0D, tuning.dropMultiplier());
    }

    public double mobMultiplier(String difficulty) {
        DifficultyTuning tuning = difficultyTunings.get(difficulty);
        return tuning == null ? 1.0D : Math.max(0.0D, tuning.mobMultiplier());
    }

    public boolean supportsDifficulty(String difficulty) {
        return difficulty != null && difficulties.contains(difficulty);
    }

    public boolean supportsDropMode(String mode) {
        return mode != null && dropModeAllowed.contains(mode);
    }

    public static Set<String> knownDropModes() {
        return Set.copyOf(DEFAULT_MODES);
    }
}
