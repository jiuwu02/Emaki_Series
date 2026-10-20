package emaki.jiuwu.craft.dungeon.flow;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ConditionEvaluator {

    public record EvalContext(UUID playerId, Map<String, Object> facts) {

        public static EvalContext of(Map<String, Object> facts) {
            return new EvalContext(null, facts == null ? Map.of() : facts);
        }

        public Object fact(String key) {
            return facts == null ? null : facts.get(key);
        }

        public boolean factFlag(String key) {
            return Boolean.TRUE.equals(fact(key));
        }

        public int factInt(String key, int fallback) {
            Object value = fact(key);
            if (value instanceof Number number) {
                return number.intValue();
            }
            try {
                return value == null ? fallback : Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException exception) {
                return fallback;
            }
        }
    }

    public boolean evaluate(ConditionDef condition, EvalContext context) {
        if (condition == null) {
            return true;
        }
        if (condition.composite()) {
            return evaluateComposite(condition, context);
        }
        return evaluateLeaf(condition, context);
    }

    public boolean evaluateAll(List<ConditionDef> conditions, String logic, EvalContext context) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }
        boolean or = "OR".equalsIgnoreCase(logic);
        for (ConditionDef condition : conditions) {
            boolean result = evaluate(condition, context);
            if (or && result) {
                return true;
            }
            if (!or && !result) {
                return false;
            }
        }
        return or;
    }

    private boolean evaluateComposite(ConditionDef condition, EvalContext context) {
        return evaluateAll(condition.children(), condition.logic(), context);
    }

    private boolean evaluateLeaf(ConditionDef condition, EvalContext context) {
        String type = condition.type().toLowerCase(Locale.ROOT);
        switch (type) {
            case "range" -> {
                int required = condition.paramInt("min_players", 1);
                String target = condition.param("target", "player");
                int actual = "mob".equals(target)
                        ? context.factInt(Facts.RANGE_MOBS, 0)
                        : context.factInt(Facts.RANGE_PLAYERS, 0);
                return actual >= required;
            }
            case "region" -> {
                String direction = condition.param("direction", "enter");
                return "leave".equals(direction)
                        ? context.factFlag(Facts.REGION_LEFT)
                        : context.factFlag(Facts.REGION_ENTERED);
            }
            case "block_interact" -> {
                String expected = condition.param("click", "right");
                return expected.equalsIgnoreCase(String.valueOf(context.fact(Facts.CLICK)));
            }
            case "block_activate" -> {
                return context.factFlag(Facts.BLOCK_ACTIVATE);
            }
            case "redstone" -> {
                String expected = condition.param("level", "change");
                String actual = String.valueOf(context.fact(Facts.REDSTONE_LEVEL));
                if ("change".equals(expected)) {
                    return !actual.isEmpty() && !"null".equals(actual);
                }
                return expected.equalsIgnoreCase(actual);
            }
            case "clear" -> {
                return context.factFlag(Facts.waveCleared(condition.param("spawn", "")));
            }
            case "boss_kill" -> {
                return context.factFlag(Facts.bossKilled(condition.param("boss_id", "")));
            }
            case "kill_count" -> {
                int required = condition.paramInt("count", 1);
                return context.factInt(Facts.killCount(condition.param("target", "")), 0) >= required;
            }
            case "timer" -> {
                return context.factFlag(Facts.timerMark(condition.param("module", "")));
            }
            case "item" -> {
                return context.factFlag(Facts.ITEM_INTERACTED);
            }
            case "command" -> {
                String expected = condition.param("expected", "true");
                return expected.equalsIgnoreCase(String.valueOf(context.fact(Facts.COMMAND_RESULT)));
            }
            case "player_state" -> {
                String expected = condition.param("state", "dead");
                int remainingRequired = condition.paramInt("remaining_lives", -1);
                if (remainingRequired >= 0
                        && context.factInt(Facts.REMAINING_LIVES, Integer.MAX_VALUE) > remainingRequired) {
                    return false;
                }
                String actual = String.valueOf(context.fact(Facts.PLAYER_STATE));
                if (actual.isEmpty() || "null".equals(actual)) {
                    return false;
                }
                return expected.equalsIgnoreCase(actual);
            }
            case "script" -> {
                Object result = context.fact(Facts.SCRIPT_RESULT);
                return result == null || Boolean.TRUE.equals(result);
            }
            default -> {
                return false;
            }
        }
    }
}
