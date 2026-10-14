package emaki.jiuwu.craft.corelib.action.select;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.BiFunction;

import emaki.jiuwu.craft.corelib.api.action.CoreTargetConditionArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetIdentity;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetOutcome;
import emaki.jiuwu.craft.corelib.api.action.TargetComparisons;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class BuiltinTargetConditions {

    public record Definition(String id, String description,
            BiFunction<TargetFacts, CoreTargetConditionArguments, CoreTargetOutcome> test) {
    }

    private static final Map<String, Definition> DEFINITIONS = define();

    private BuiltinTargetConditions() {
    }

    public static boolean contains(String id) {
        return id != null && DEFINITIONS.containsKey(Texts.lower(id));
    }

    public static Definition find(String id) {
        return id == null ? null : DEFINITIONS.get(Texts.lower(id));
    }

    public static List<String> ids() {
        return DEFINITIONS.keySet().stream().sorted().toList();
    }

    public static CoreTargetOutcome evaluate(String id, TargetFacts facts, CoreTargetConditionArguments arguments) {
        Definition definition = find(id);
        if (definition == null) {
            return CoreTargetOutcome.UNKNOWN;
        }
        return definition.test().apply(facts, arguments);
    }

    public static CoreTargetOutcome identity(String systemId, TargetFacts facts,
            CoreTargetConditionArguments arguments) {
        CoreTargetIdentity identity = facts.identity(Texts.lower(systemId));
        if (identity == null) {
            return CoreTargetOutcome.FAIL;
        }
        List<String> expected = arguments.strings("id");
        if (!expected.isEmpty() && expected.stream().noneMatch(identity.id()::equals)) {
            return CoreTargetOutcome.FAIL;
        }
        OptionalDouble level = arguments.doubleValue("level");
        if (level.isEmpty()) {
            return CoreTargetOutcome.PASS;
        }
        Boolean matched = TargetComparisons.compare(identity.level(), arguments.string("op", ""), level.getAsDouble());
        if (matched == null) {
            return CoreTargetOutcome.UNKNOWN;
        }
        return matched ? CoreTargetOutcome.PASS : CoreTargetOutcome.FAIL;
    }

    private static Map<String, Definition> define() {
        Map<String, Definition> definitions = new LinkedHashMap<>();
        definitions.put("entity_type", new Definition("entity_type",
                "实体类型为所列原版类型之一。", BuiltinTargetConditions::entityType));
        definitions.put("player", new Definition("player",
                "主体是否为玩家。", BuiltinTargetConditions::player));
        definitions.put("dead", new Definition("dead",
                "主体是否已死亡。", BuiltinTargetConditions::dead));
        definitions.put("world", new Definition("world",
                "主体位于所列世界之一。", BuiltinTargetConditions::world));
        definitions.put("health", new Definition("health",
                "当前生命值与 op/value 比较。", BuiltinTargetConditions::health));
        definitions.put("health_percent", new Definition("health_percent",
                "生命值百分比与 op/value 比较。", BuiltinTargetConditions::healthPercent));
        definitions.put("distance", new Definition("distance",
                "到管道原点的距离与 op/value 比较。", BuiltinTargetConditions::distance));
        definitions.put("level", new Definition("level",
                "玩家经验等级与 op/value 比较。", BuiltinTargetConditions::level));
        definitions.put("food", new Definition("food",
                "玩家食物等级与 op/value 比较。", BuiltinTargetConditions::food));
        definitions.put("gamemode", new Definition("gamemode",
                "玩家游戏模式为所列模式之一。", BuiltinTargetConditions::gameMode));
        definitions.put("permission", new Definition("permission",
                "玩家拥有所列权限节点中的至少一个。", BuiltinTargetConditions::permission));
        definitions.put("potion_effect", new Definition("potion_effect",
                "主体带有所列效果之一，且等级不低于 min_amplifier。", BuiltinTargetConditions::potionEffect));
        definitions.put("scoreboard_tag", new Definition("scoreboard_tag",
                "主体带有所列记分板标签之一。", BuiltinTargetConditions::scoreboardTag));
        definitions.put("state", new Definition("state",
                "主体状态键等于期望的布尔值。", BuiltinTargetConditions::state));
        definitions.put("on_fire", new Definition("on_fire",
                "主体是否着火。", BuiltinTargetConditions.flag(TargetFactsReader.FLAG_ON_FIRE)));
        definitions.put("invulnerable", new Definition("invulnerable",
                "主体是否无敌。", BuiltinTargetConditions.flag(TargetFactsReader.FLAG_INVULNERABLE)));
        definitions.put("glowing", new Definition("glowing",
                "主体是否发光。", BuiltinTargetConditions.flag(TargetFactsReader.FLAG_GLOWING)));
        definitions.put("silent", new Definition("silent",
                "主体是否静音。", BuiltinTargetConditions.flag(TargetFactsReader.FLAG_SILENT)));
        definitions.put("has_ai", new Definition("has_ai",
                "主体是否启用 AI。", BuiltinTargetConditions.flag(TargetFactsReader.FLAG_HAS_AI)));
        definitions.put("tamed", new Definition("tamed",
                "主体是否已被驯服。", BuiltinTargetConditions.flag(TargetFactsReader.FLAG_TAMED)));
        definitions.put("baby", new Definition("baby",
                "主体是否为幼年体。", BuiltinTargetConditions.flag(TargetFactsReader.FLAG_BABY)));
        definitions.put("has_gravity", new Definition("has_gravity",
                "主体是否受重力影响。", BuiltinTargetConditions.flag(TargetFactsReader.FLAG_HAS_GRAVITY)));
        definitions.put("leashed", new Definition("leashed",
                "主体是否被拴绳系住。", BuiltinTargetConditions.flag(TargetFactsReader.FLAG_LEASHED)));
        definitions.put("breathing", new Definition("breathing",
                "主体是否仍有剩余呼吸值。", BuiltinTargetConditions.flag(TargetFactsReader.FLAG_BREATHING)));
        definitions.put("world_time", new Definition("world_time",
                "所在世界时间与 op/value 比较（tick，0-24000）。", BuiltinTargetConditions::worldTime));
        definitions.put("name", new Definition("name",
                "主体名称等于所列名称之一。", BuiltinTargetConditions::name));
        definitions.put("chunk", new Definition("chunk",
                "主体所在区块坐标（cx,cz）与 value 比较。", BuiltinTargetConditions::chunk));
        definitions.put("weather", new Definition("weather",
                "所在世界天气为所列天气之一。", BuiltinTargetConditions::weather));
        definitions.put("is_day", new Definition("is_day",
                "所在世界当前是否为白天。", BuiltinTargetConditions::isDay));
        definitions.put("slot", new Definition("slot",
                "玩家指定物品栏槽位是否为空。", BuiltinTargetConditions::slotEmpty));
        return Map.copyOf(definitions);
    }

    private static CoreTargetOutcome chunk(TargetFacts facts, CoreTargetConditionArguments arguments) {
        if (!facts.present() || facts.chunkX() == Integer.MIN_VALUE || facts.chunkZ() == Integer.MIN_VALUE) {
            return CoreTargetOutcome.UNKNOWN;
        }
        String expected = Texts.trim(arguments.string("value", ""));
        if (expected.isEmpty()) {
            return CoreTargetOutcome.UNKNOWN;
        }
        String[] parts = expected.split(",");
        if (parts.length != 2) {
            return CoreTargetOutcome.UNKNOWN;
        }
        Integer expectedX = parseInteger(parts[0]);
        Integer expectedZ = parseInteger(parts[1]);
        if (expectedX == null || expectedZ == null) {
            return CoreTargetOutcome.UNKNOWN;
        }
        return facts.chunkX() == expectedX && facts.chunkZ() == expectedZ
                ? CoreTargetOutcome.PASS
                : CoreTargetOutcome.FAIL;
    }

    private static CoreTargetOutcome weather(TargetFacts facts, CoreTargetConditionArguments arguments) {
        if (!facts.present() || facts.worldName().isEmpty()) {
            return CoreTargetOutcome.UNKNOWN;
        }
        List<String> expected = arguments.strings("value").stream().map(Texts::lower).toList();
        if (expected.isEmpty()) {
            return CoreTargetOutcome.UNKNOWN;
        }
        String actual = Texts.lower(facts.worldWeather());
        if (actual.isEmpty()) {
            return CoreTargetOutcome.UNKNOWN;
        }
        return expected.contains(actual) ? CoreTargetOutcome.PASS : CoreTargetOutcome.FAIL;
    }

    private static CoreTargetOutcome isDay(TargetFacts facts, CoreTargetConditionArguments arguments) {
        if (!facts.present() || facts.worldName().isEmpty()) {
            return CoreTargetOutcome.UNKNOWN;
        }
        boolean day = facts.worldTime() >= 0L && facts.worldTime() <= 12000L;
        Optional<Boolean> expected = arguments.booleanValue("value");
        if (expected.isEmpty()) {
            return CoreTargetOutcome.UNKNOWN;
        }
        return day == expected.get() ? CoreTargetOutcome.PASS : CoreTargetOutcome.FAIL;
    }

    private static CoreTargetOutcome slotEmpty(TargetFacts facts, CoreTargetConditionArguments arguments) {
        if (!facts.player()) {
            return CoreTargetOutcome.UNKNOWN;
        }
        String rawSlot = Texts.trim(arguments.string("slot", ""));
        if (rawSlot.isEmpty()) {
            return CoreTargetOutcome.UNKNOWN;
        }
        Integer slot = parseInteger(rawSlot);
        if (slot == null || slot < 0 || slot >= 36) {
            return CoreTargetOutcome.UNKNOWN;
        }
        Boolean empty = facts.slotEmpty(slot);
        if (empty == null) {
            return CoreTargetOutcome.UNKNOWN;
        }
        Optional<Boolean> expected = arguments.booleanValue("value");
        if (expected.isEmpty()) {
            return CoreTargetOutcome.UNKNOWN;
        }
        return empty == expected.get() ? CoreTargetOutcome.PASS : CoreTargetOutcome.FAIL;
    }

    private static Integer parseInteger(String raw) {
        String normalized = Texts.trim(raw);
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(normalized);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static java.util.function.BiFunction<TargetFacts, CoreTargetConditionArguments, CoreTargetOutcome> flag(String key) {
        return (facts, arguments) -> {
            if (!facts.present()) {
                return CoreTargetOutcome.UNKNOWN;
            }
            Boolean actual = facts.flag(key);
            if (actual == null) {
                return CoreTargetOutcome.UNKNOWN;
            }
            Optional<Boolean> expected = arguments.booleanValue("value");
            if (expected.isEmpty()) {
                return CoreTargetOutcome.UNKNOWN;
            }
            return actual == expected.get() ? CoreTargetOutcome.PASS : CoreTargetOutcome.FAIL;
        };
    }

    private static CoreTargetOutcome worldTime(TargetFacts facts, CoreTargetConditionArguments arguments) {
        if (!facts.present()) {
            return CoreTargetOutcome.UNKNOWN;
        }
        return compare(arguments, facts.worldTime());
    }

    private static CoreTargetOutcome name(TargetFacts facts, CoreTargetConditionArguments arguments) {
        if (!facts.present()) {
            return CoreTargetOutcome.UNKNOWN;
        }
        List<String> expected = arguments.strings("value");
        if (expected.isEmpty()) {
            return CoreTargetOutcome.UNKNOWN;
        }
        for (String candidate : expected) {
            if (facts.plainName().equalsIgnoreCase(Texts.trim(candidate))) {
                return CoreTargetOutcome.PASS;
            }
        }
        return CoreTargetOutcome.FAIL;
    }

    private static CoreTargetOutcome entityType(TargetFacts facts, CoreTargetConditionArguments arguments) {
        if (!facts.present()) {
            return CoreTargetOutcome.UNKNOWN;
        }
        List<String> expected = arguments.strings("value").stream().map(BuiltinTargetConditions::normalizeType).toList();
        if (expected.isEmpty()) {
            return CoreTargetOutcome.UNKNOWN;
        }
        return expected.contains(normalizeType(facts.entityType()))
                ? CoreTargetOutcome.PASS
                : CoreTargetOutcome.FAIL;
    }

    private static CoreTargetOutcome player(TargetFacts facts, CoreTargetConditionArguments arguments) {
        return flag(facts.present(), facts.player(), arguments);
    }

    private static CoreTargetOutcome dead(TargetFacts facts, CoreTargetConditionArguments arguments) {
        return flag(facts.present(), facts.dead(), arguments);
    }

    private static CoreTargetOutcome world(TargetFacts facts, CoreTargetConditionArguments arguments) {
        if (!facts.present()) {
            return CoreTargetOutcome.UNKNOWN;
        }
        List<String> expected = arguments.strings("value");
        if (expected.isEmpty()) {
            return CoreTargetOutcome.UNKNOWN;
        }
        String actual = facts.worldName();
        for (String candidate : expected) {
            if (sameWorld(actual, candidate)) {
                return CoreTargetOutcome.PASS;
            }
        }
        return CoreTargetOutcome.FAIL;
    }

    private static CoreTargetOutcome health(TargetFacts facts, CoreTargetConditionArguments arguments) {
        return facts.entityPresent()
                ? compare(arguments, facts.health())
                : CoreTargetOutcome.UNKNOWN;
    }

    private static CoreTargetOutcome healthPercent(TargetFacts facts, CoreTargetConditionArguments arguments) {
        return facts.entityPresent()
                ? compare(arguments, facts.healthPercent())
                : CoreTargetOutcome.UNKNOWN;
    }

    private static CoreTargetOutcome distance(TargetFacts facts, CoreTargetConditionArguments arguments) {
        if (!facts.present() || facts.distance() < 0D) {
            return CoreTargetOutcome.UNKNOWN;
        }
        return compare(arguments, facts.distance());
    }

    private static CoreTargetOutcome level(TargetFacts facts, CoreTargetConditionArguments arguments) {
        return facts.player() ? compare(arguments, facts.experienceLevel()) : CoreTargetOutcome.UNKNOWN;
    }

    private static CoreTargetOutcome food(TargetFacts facts, CoreTargetConditionArguments arguments) {
        return facts.player() ? compare(arguments, facts.foodLevel()) : CoreTargetOutcome.UNKNOWN;
    }

    private static CoreTargetOutcome gameMode(TargetFacts facts, CoreTargetConditionArguments arguments) {
        if (!facts.player()) {
            return CoreTargetOutcome.UNKNOWN;
        }
        List<String> expected = arguments.strings("value").stream().map(Texts::lower).toList();
        if (expected.isEmpty()) {
            return CoreTargetOutcome.UNKNOWN;
        }
        return expected.contains(Texts.lower(facts.gameMode()))
                ? CoreTargetOutcome.PASS
                : CoreTargetOutcome.FAIL;
    }

    private static CoreTargetOutcome permission(TargetFacts facts, CoreTargetConditionArguments arguments) {
        if (!facts.present()) {
            return CoreTargetOutcome.UNKNOWN;
        }
        List<String> nodes = arguments.strings("value");
        if (nodes.isEmpty()) {
            return CoreTargetOutcome.UNKNOWN;
        }
        boolean unanswered = false;
        for (String node : nodes) {
            Boolean allowed = facts.permissions().apply(node);
            if (allowed == null) {
                unanswered = true;
                continue;
            }
            if (allowed) {
                return CoreTargetOutcome.PASS;
            }
        }
        return unanswered ? CoreTargetOutcome.UNKNOWN : CoreTargetOutcome.FAIL;
    }

    private static CoreTargetOutcome potionEffect(TargetFacts facts, CoreTargetConditionArguments arguments) {
        if (!facts.entityPresent()) {
            return CoreTargetOutcome.UNKNOWN;
        }
        List<String> effects = arguments.strings("effect");
        if (effects.isEmpty()) {
            return CoreTargetOutcome.UNKNOWN;
        }
        int minimum = arguments.intValue("min_amplifier").orElse(0);
        for (String raw : effects) {
            Integer amplifier = facts.potionAmplifier(normalizeEffect(raw));
            if (amplifier != null && amplifier >= minimum) {
                return CoreTargetOutcome.PASS;
            }
        }
        return CoreTargetOutcome.FAIL;
    }

    private static CoreTargetOutcome scoreboardTag(TargetFacts facts, CoreTargetConditionArguments arguments) {
        if (!facts.present()) {
            return CoreTargetOutcome.UNKNOWN;
        }
        List<String> expected = arguments.strings("value");
        if (expected.isEmpty()) {
            return CoreTargetOutcome.UNKNOWN;
        }
        for (String tag : expected) {
            if (facts.scoreboardTags().contains(tag)) {
                return CoreTargetOutcome.PASS;
            }
        }
        return CoreTargetOutcome.FAIL;
    }

    private static CoreTargetOutcome state(TargetFacts facts, CoreTargetConditionArguments arguments) {
        if (!facts.present()) {
            return CoreTargetOutcome.UNKNOWN;
        }
        String key = Texts.lower(arguments.string("key", ""));
        if (key.isEmpty() || !TargetFactsReader.knownStates().contains(key)) {
            return CoreTargetOutcome.UNKNOWN;
        }
        Boolean actual = facts.state(key);
        if (actual == null) {
            return CoreTargetOutcome.UNKNOWN;
        }
        boolean expected = arguments.booleanValue("value").orElse(Boolean.TRUE);
        return actual == expected ? CoreTargetOutcome.PASS : CoreTargetOutcome.FAIL;
    }

    private static CoreTargetOutcome flag(boolean answerable, boolean actual, CoreTargetConditionArguments arguments) {
        if (!answerable) {
            return CoreTargetOutcome.UNKNOWN;
        }
        Optional<Boolean> expected = arguments.booleanValue("value");
        if (expected.isEmpty()) {
            return CoreTargetOutcome.UNKNOWN;
        }
        return actual == expected.get() ? CoreTargetOutcome.PASS : CoreTargetOutcome.FAIL;
    }

    private static CoreTargetOutcome compare(CoreTargetConditionArguments arguments, double actual) {
        Optional<Boolean> result = TargetComparisons.evaluate(arguments, actual);
        if (result.isEmpty()) {
            return CoreTargetOutcome.UNKNOWN;
        }
        return result.get() ? CoreTargetOutcome.PASS : CoreTargetOutcome.FAIL;
    }

    private static String normalizeType(String raw) {
        return Texts.trim(Texts.toStringSafe(raw))
                .replace("minecraft:", "")
                .replace('.', '_')
                .toUpperCase(Locale.ROOT);
    }

    private static String normalizeEffect(String raw) {
        return TargetEffectKeys.normalize(raw);
    }

    private static boolean sameWorld(String actual, String candidate) {
        String expected = Texts.trim(Texts.toStringSafe(candidate));
        if (expected.isEmpty()) {
            return false;
        }
        if (actual.equalsIgnoreCase(expected)) {
            return true;
        }
        int separator = expected.indexOf(':');
        return separator >= 0
                && separator + 1 < expected.length()
                && actual.equalsIgnoreCase(expected.substring(separator + 1));
    }
}
