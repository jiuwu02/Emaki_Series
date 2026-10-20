package emaki.jiuwu.craft.dungeon.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.dungeon.score.ScoreWeights;

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
        Map<Integer, Integer> limitsWeekly,
        int limitsMonthly,
        int cooldownSeconds,
        int leaderboardMaxEntries,
        List<String> settlementCommands
) {

    public record DifficultyTuning(double mobMultiplier, double dropMultiplier) {
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
                Map.of(),
                0,
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
        Integer limitsDaily = section.getInt("limits.daily", 0);
        Map<Integer, Integer> limitsWeekly = new LinkedHashMap<>();
        YamlSection weeklySection = section.getSection("limits.weekly");
        if (weeklySection != null) {
            for (String day : weeklySection.getKeys(false)) {
                try {
                    int dayOfWeek = Integer.parseInt(day.trim());
                    if (dayOfWeek < 1 || dayOfWeek > 7) {
                        continue;
                    }
                    Integer count = weeklySection.getInt(day, 0);
                    if (count != null && count > 0) {
                        limitsWeekly.put(dayOfWeek, count);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        Integer limitsMonthly = section.getInt("limits.monthly", 0);
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
                Map.copyOf(limitsWeekly),
                limitsMonthly == null || limitsMonthly < 0 ? 0 : limitsMonthly,
                cooldownSeconds == null || cooldownSeconds < 0 ? 0 : cooldownSeconds,
                leaderboardMaxEntries == null || leaderboardMaxEntries < 1
                        ? 100 : leaderboardMaxEntries,
                List.copyOf(settlementCommands)
        );
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
