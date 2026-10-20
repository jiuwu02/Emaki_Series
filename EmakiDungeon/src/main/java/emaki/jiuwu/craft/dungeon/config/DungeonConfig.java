package emaki.jiuwu.craft.dungeon.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public record DungeonConfig(
        String displayName,
        int poolMaxWorlds,
        int prepareWindowSeconds,
        String waitChannel,
        int waitTimeoutSeconds,
        List<String> difficulties,
        List<String> dropModeAllowed,
        boolean inventoryIsolation,
        List<String> commandsAllow,
        Map<String, Integer> respawnCounts,
        int timeoutSeconds,
        List<Integer> timerMessages
) {

    private static final List<String> DEFAULT_DIFFICULTIES = List.of("normal", "hard");
    private static final List<String> DEFAULT_MODES = List.of("free", "roll", "killer", "score", "share");

    public static DungeonConfig defaults() {
        return new DungeonConfig(
                "dungeon",
                5,
                10,
                "bossbar",
                60,
                DEFAULT_DIFFICULTIES,
                DEFAULT_MODES,
                false,
                List.of(),
                Map.of("normal", 3, "hard", 1),
                1800,
                List.of(300, 60, 10)
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
        return new DungeonConfig(
                section.getString("display-name", "dungeon"),
                poolMaxWorlds,
                prepareWindow,
                waitChannel,
                waitTimeout,
                List.copyOf(difficulties),
                List.copyOf(modeValidated),
                section.getBoolean("inventory.isolation-enabled", false),
                List.copyOf(commandsAllow),
                Map.copyOf(respawnCounts),
                timeoutSeconds == null || timeoutSeconds < 30 ? 1800 : timeoutSeconds,
                List.copyOf(timerMessages)
        );
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
