package emaki.jiuwu.craft.dummy.scenario;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.configuration.file.YamlConfiguration;

public final class ScenarioLoader {

    private static final Set<String> NO_PARAM_ACTIONS = Set.of("join_all", "leave_all");
    private static final Set<String> KNOWN_ACTIONS = Set.of(
            "join_all", "leave_all", "cmd", "say", "walk", "look", "interact", "attack",
            "click", "wait_event", "wait_log", "expect", "assert", "sleep");
    private static final Set<String> PARAM_PLAYER_ACTIONS = Set.of(
            "cmd", "say", "walk", "look", "interact", "attack", "click");
    private static final Set<String> OPTIONAL_PARAMS = Set.of(
            "timeout", "on_fail", "x", "y", "z", "where", "file", "contains", "type",
            "expr", "player", "command", "text", "yaw", "pitch", "seconds", "slot");

    private ScenarioLoader() {
    }

    public static ScenarioDefinition load(File file) throws ScenarioLoadException {
        if (!file.isFile()) {
            throw new ScenarioLoadException("file not found: " + file.getName());
        }
        String id = file.getName().replaceFirst("\\.ya?ml$", "");
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        List<String> players = configuration.getStringList("players");
        if (players.isEmpty()) {
            throw new ScenarioLoadException("players must not be empty");
        }
        List<Map<?, ?>> rawSteps = configuration.getMapList("steps");
        if (rawSteps.isEmpty()) {
            throw new ScenarioLoadException("steps must not be empty");
        }
        List<ScenarioStep> steps = new ArrayList<>(rawSteps.size());
        Set<String> declaredPlayers = new LinkedHashSet<>(players);
        for (int index = 0; index < rawSteps.size(); index++) {
            steps.add(parseStep(rawSteps.get(index), index, declaredPlayers));
        }
        int timeout = configuration.getInt("timeout_seconds", 0);
        return new ScenarioDefinition(id, List.copyOf(players), timeout, List.copyOf(steps),
                file.getName());
    }

    public static ScenarioDefinition parse(String yamlText, String id) throws ScenarioLoadException {
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(
                new StringReader(yamlText));
        List<String> players = configuration.getStringList("players");
        if (players.isEmpty()) {
            throw new ScenarioLoadException("players must not be empty");
        }
        List<Map<?, ?>> rawSteps = configuration.getMapList("steps");
        if (rawSteps.isEmpty()) {
            throw new ScenarioLoadException("steps must not be empty");
        }
        List<ScenarioStep> steps = new ArrayList<>(rawSteps.size());
        Set<String> declaredPlayers = new LinkedHashSet<>(players);
        for (int index = 0; index < rawSteps.size(); index++) {
            steps.add(parseStep(rawSteps.get(index), index, declaredPlayers));
        }
        int timeout = configuration.getInt("timeout_seconds", 0);
        return new ScenarioDefinition(id, List.copyOf(players), timeout, List.copyOf(steps),
                id + ".yml");
    }

    private static ScenarioStep parseStep(Map<?, ?> raw, int index, Set<String> declaredPlayers)
            throws ScenarioLoadException {
        Object actionValue = raw.get("action");
        if (!(actionValue instanceof String action) || action.isBlank()) {
            throw new ScenarioLoadException("step " + index + ": action is required");
        }
        String normalized = action.trim().toLowerCase(Locale.ROOT);
        if (!KNOWN_ACTIONS.contains(normalized)) {
            throw new ScenarioLoadException("step " + index + ": unknown action " + normalized);
        }
        Map<String, Object> params = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if ("action".equals(key)) {
                continue;
            }
            if (!OPTIONAL_PARAMS.contains(key)) {
                throw new ScenarioLoadException("step " + index + ": unknown param " + key);
            }
            params.put(key, entry.getValue());
        }
        validate(normalized, params, index, declaredPlayers);
        return new ScenarioStep(normalized, Map.copyOf(params));
    }

    private static void validate(String action, Map<String, Object> params, int index,
            Set<String> declaredPlayers) throws ScenarioLoadException {
        if (NO_PARAM_ACTIONS.contains(action)) {
            return;
        }
        if (PARAM_PLAYER_ACTIONS.contains(action)) {
            require(params, "player", index, action);
            String player = String.valueOf(params.get("player"));
            if (!declaredPlayers.contains(player)) {
                throw new ScenarioLoadException("step " + index + ": player " + player
                        + " is not declared in players");
            }
        }
        switch (action) {
            case "cmd" -> require(params, "command", index, action);
            case "say" -> require(params, "text", index, action);
            case "walk" -> {
                require(params, "x", index, action);
                require(params, "y", index, action);
                require(params, "z", index, action);
            }
            case "look" -> {
                require(params, "yaw", index, action);
                require(params, "pitch", index, action);
            }
            case "wait_event" -> require(params, "type", index, action);
            case "wait_log" -> {
                require(params, "file", index, action);
                require(params, "contains", index, action);
            }
            case "expect", "assert" -> require(params, "expr", index, action);
            case "click" -> require(params, "slot", index, action);
            case "sleep" -> require(params, "seconds", index, action);
            default -> {
            }
        }
    }

    private static void require(Map<String, Object> params, String key, int index, String action)
            throws ScenarioLoadException {
        Object value = params.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new ScenarioLoadException("step " + index + ": " + action + " requires " + key);
        }
    }
}
