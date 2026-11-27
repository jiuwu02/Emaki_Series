package emaki.jiuwu.craft.dungeon.flow;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.condition.ConditionContext;
import emaki.jiuwu.craft.corelib.api.condition.CoreCondition;
import emaki.jiuwu.craft.corelib.api.condition.CoreConditionOutcome;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class DungeonConditions {

    public static final String FACT_PREFIX = "dungeon.fact.";

    private DungeonConditions() {
    }

    public static @NotNull List<CoreCondition> all() {
        return List.of(
                new Range(),
                new Region(),
                new BlockInteract(),
                new BlockActivate(),
                new Redstone(),
                new Clear(),
                new BossKill(),
                new KillCount(),
                new Timer(),
                new Item(),
                new Command(),
                new PlayerState(),
                new Script()
        );
    }

    static Object fact(ConditionContext context, String key) {
        if (context == null || context.variables() == null) {
            return null;
        }
        return context.variables().get(FACT_PREFIX + key);
    }

    static boolean factFlag(ConditionContext context, String key) {
        return Boolean.TRUE.equals(fact(context, key));
    }

    static int factInt(ConditionContext context, String key, int fallback) {
        Object value = fact(context, key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    static String field(Map<String, Object> fields, String key, String fallback) {
        Object value = fields == null ? null : fields.get(key);
        String text = Texts.toStringSafe(value).trim();
        return Texts.isBlank(text) ? fallback : text;
    }

    static int intField(Map<String, Object> fields, String key, int fallback) {
        Object value = fields == null ? null : fields.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    static CoreConditionOutcome verdict(boolean passed) {
        return passed ? CoreConditionOutcome.PASS : CoreConditionOutcome.FAIL;
    }

    static String lower(Object value) {
        return Texts.toStringSafe(value).trim().toLowerCase(Locale.ROOT);
    }

    static final class Range implements CoreCondition {

        @Override
        public @NotNull String id() {
            return "range";
        }

        @Override
        public @NotNull String description() {
            return "范围内玩家或生物数量达标";
        }

        @Override
        public @NotNull CoreConditionOutcome test(@NotNull Map<String, Object> fields, @NotNull ConditionContext context) {
            int required = intField(fields, "min_players", 1);
            boolean mob = "mob".equals(field(fields, "target", "player"));
            int actual = mob
                    ? factInt(context, Facts.RANGE_MOBS, 0)
                    : factInt(context, Facts.RANGE_PLAYERS, 0);
            return verdict(actual >= required);
        }
    }

    static final class Region implements CoreCondition {

        @Override
        public @NotNull String id() {
            return "region";
        }

        @Override
        public @NotNull String description() {
            return "区域进入或离开";
        }

        @Override
        public @NotNull CoreConditionOutcome test(@NotNull Map<String, Object> fields, @NotNull ConditionContext context) {
            boolean leave = "leave".equals(field(fields, "direction", "enter"));
            return verdict(factFlag(context, leave ? Facts.REGION_LEFT : Facts.REGION_ENTERED));
        }
    }

    static final class BlockInteract implements CoreCondition {

        @Override
        public @NotNull String id() {
            return "block_interact";
        }

        @Override
        public @NotNull String description() {
            return "方块点击方式匹配";
        }

        @Override
        public @NotNull CoreConditionOutcome test(@NotNull Map<String, Object> fields, @NotNull ConditionContext context) {
            String expected = field(fields, "click", "right");
            return verdict(expected.equalsIgnoreCase(Texts.toStringSafe(fact(context, Facts.CLICK))));
        }
    }

    static final class BlockActivate implements CoreCondition {

        @Override
        public @NotNull String id() {
            return "block_activate";
        }

        @Override
        public @NotNull String description() {
            return "方块已被激活";
        }

        @Override
        public @NotNull CoreConditionOutcome test(@NotNull Map<String, Object> fields, @NotNull ConditionContext context) {
            return verdict(factFlag(context, Facts.BLOCK_ACTIVATE));
        }
    }

    static final class Redstone implements CoreCondition {

        @Override
        public @NotNull String id() {
            return "redstone";
        }

        @Override
        public @NotNull String description() {
            return "红石等级匹配";
        }

        @Override
        public @NotNull CoreConditionOutcome test(@NotNull Map<String, Object> fields, @NotNull ConditionContext context) {
            String expected = field(fields, "level", "change");
            String actual = Texts.toStringSafe(fact(context, Facts.REDSTONE_LEVEL));
            if ("change".equals(expected)) {
                return verdict(!actual.isEmpty() && !"null".equals(actual));
            }
            return verdict(expected.equalsIgnoreCase(actual));
        }
    }

    static final class Clear implements CoreCondition {

        @Override
        public @NotNull String id() {
            return "clear";
        }

        @Override
        public @NotNull String description() {
            return "指定刷怪点波次已清空";
        }

        @Override
        public @NotNull CoreConditionOutcome test(@NotNull Map<String, Object> fields, @NotNull ConditionContext context) {
            return verdict(factFlag(context, Facts.waveCleared(field(fields, "spawn", ""))));
        }
    }

    static final class BossKill implements CoreCondition {

        @Override
        public @NotNull String id() {
            return "boss_kill";
        }

        @Override
        public @NotNull String description() {
            return "指定 Boss 已被击杀";
        }

        @Override
        public @NotNull CoreConditionOutcome test(@NotNull Map<String, Object> fields, @NotNull ConditionContext context) {
            return verdict(factFlag(context, Facts.bossKilled(field(fields, "boss_id", ""))));
        }
    }

    static final class KillCount implements CoreCondition {

        @Override
        public @NotNull String id() {
            return "kill_count";
        }

        @Override
        public @NotNull String description() {
            return "击杀计数达标";
        }

        @Override
        public @NotNull CoreConditionOutcome test(@NotNull Map<String, Object> fields, @NotNull ConditionContext context) {
            int required = intField(fields, "count", 1);
            return verdict(factInt(context, Facts.killCount(field(fields, "target", "")), 0) >= required);
        }
    }

    static final class Timer implements CoreCondition {

        @Override
        public @NotNull String id() {
            return "timer";
        }

        @Override
        public @NotNull String description() {
            return "模块计时器已到点";
        }

        @Override
        public @NotNull CoreConditionOutcome test(@NotNull Map<String, Object> fields, @NotNull ConditionContext context) {
            return verdict(factFlag(context, Facts.timerMark(field(fields, "module", ""))));
        }
    }

    static final class Item implements CoreCondition {

        @Override
        public @NotNull String id() {
            return "item";
        }

        @Override
        public @NotNull String description() {
            return "玩家交互了要求物品";
        }

        @Override
        public @NotNull CoreConditionOutcome test(@NotNull Map<String, Object> fields, @NotNull ConditionContext context) {
            return verdict(factFlag(context, Facts.ITEM_INTERACTED));
        }
    }

    static final class Command implements CoreCondition {

        @Override
        public @NotNull String id() {
            return "command";
        }

        @Override
        public @NotNull String description() {
            return "命令返回结果匹配";
        }

        @Override
        public @NotNull CoreConditionOutcome test(@NotNull Map<String, Object> fields, @NotNull ConditionContext context) {
            String expected = field(fields, "expected", "true");
            return verdict(expected.equalsIgnoreCase(Texts.toStringSafe(fact(context, Facts.COMMAND_RESULT))));
        }
    }

    static final class PlayerState implements CoreCondition {

        @Override
        public @NotNull String id() {
            return "player_state";
        }

        @Override
        public @NotNull String description() {
            return "玩家状态与剩余生命数匹配";
        }

        @Override
        public @NotNull CoreConditionOutcome test(@NotNull Map<String, Object> fields, @NotNull ConditionContext context) {
            int remainingRequired = intField(fields, "remaining_lives", -1);
            if (remainingRequired >= 0
                    && factInt(context, Facts.REMAINING_LIVES, Integer.MAX_VALUE) > remainingRequired) {
                return CoreConditionOutcome.FAIL;
            }
            String actual = Texts.toStringSafe(fact(context, Facts.PLAYER_STATE));
            if (actual.isEmpty() || "null".equals(actual)) {
                return CoreConditionOutcome.FAIL;
            }
            return verdict(field(fields, "state", "dead").equalsIgnoreCase(actual));
        }
    }

    static final class Script implements CoreCondition {

        @Override
        public @NotNull String id() {
            return "script";
        }

        @Override
        public @NotNull String description() {
            return "脚本阶段结果未被拒绝";
        }

        @Override
        public @NotNull CoreConditionOutcome test(@NotNull Map<String, Object> fields, @NotNull ConditionContext context) {
            Object result = fact(context, Facts.SCRIPT_RESULT);
            return verdict(result == null || Boolean.TRUE.equals(result));
        }
    }
}
