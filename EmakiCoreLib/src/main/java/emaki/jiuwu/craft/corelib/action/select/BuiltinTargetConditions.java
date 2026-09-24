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
                "Entity type is one of the listed vanilla types.", BuiltinTargetConditions::entityType));
        definitions.put("player", new Definition("player",
                "Subject is, or is not, a player.", BuiltinTargetConditions::player));
        definitions.put("dead", new Definition("dead",
                "Subject is, or is not, dead.", BuiltinTargetConditions::dead));
        definitions.put("world", new Definition("world",
                "Subject stands in one of the listed worlds.", BuiltinTargetConditions::world));
        definitions.put("health", new Definition("health",
                "Current health compares against op/value.", BuiltinTargetConditions::health));
        definitions.put("health_percent", new Definition("health_percent",
                "Health ratio in percent compares against op/value.", BuiltinTargetConditions::healthPercent));
        definitions.put("distance", new Definition("distance",
                "Distance from the pipeline origin compares against op/value.", BuiltinTargetConditions::distance));
        definitions.put("level", new Definition("level",
                "Player experience level compares against op/value.", BuiltinTargetConditions::level));
        definitions.put("food", new Definition("food",
                "Player food level compares against op/value.", BuiltinTargetConditions::food));
        definitions.put("gamemode", new Definition("gamemode",
                "Player game mode is one of the listed modes.", BuiltinTargetConditions::gameMode));
        definitions.put("permission", new Definition("permission",
                "Player holds at least one of the listed permission nodes.", BuiltinTargetConditions::permission));
        definitions.put("potion_effect", new Definition("potion_effect",
                "Subject carries one of the listed effects at or above min_amplifier.", BuiltinTargetConditions::potionEffect));
        definitions.put("scoreboard_tag", new Definition("scoreboard_tag",
                "Subject carries one of the listed scoreboard tags.", BuiltinTargetConditions::scoreboardTag));
        definitions.put("state", new Definition("state",
                "Subject state key equals the expected boolean value.", BuiltinTargetConditions::state));
        return Map.copyOf(definitions);
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