package emaki.jiuwu.craft.level.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.matcher.Matcher;

public record SourceRuleConfig(String id,
        boolean enabled,
        String type,
        String trigger,
        boolean includePlayers,
        boolean ignorePlayerPlacedBlocks,
        int expCooldownTicks,
        boolean ignoreNearSpawner,
        int spawnerScanRadius,
        boolean countResultAmount,
        int attributionExpireTicks,
        List<Rule> rules,
        List<String> gainActions) {

    public SourceRuleConfig {
        id = Texts.normalizeId(id);
        type = Texts.normalizeId(type);
        trigger = Texts.normalizeId(trigger);
        rules = rules == null ? List.of() : List.copyOf(rules);
        gainActions = gainActions == null ? List.of() : List.copyOf(gainActions);
    }

    public static SourceRuleConfig parse(String id, Map<?, ?> values) {
        List<Rule> rules = new ArrayList<>();
        Object rawRules = values == null ? null : values.get("rules");
        if (rawRules instanceof Iterable<?> iterable) {
            for (Object raw : iterable) {
                if (raw instanceof Map<?, ?> map) {
                    rules.add(Rule.parse(map));
                }
            }
        }
        List<String> gainActions = List.of();
        Object actions = values == null ? null : values.get("actions");
        if (actions instanceof Map<?, ?> actionMap) {
            gainActions = stringList(actionMap.get("gain"));
        }
        boolean ignorePlaced = false;
        int cooldownTicks = 0;
        boolean ignoreNearSpawner = false;
        int spawnerScanRadius = 8;
        Object antiAbuse = values == null ? null : values.get("anti_abuse");
        if (antiAbuse instanceof Map<?, ?> antiMap) {
            ignorePlaced = bool(antiMap.get("ignore_player_placed_blocks"), false);
            cooldownTicks = (int) number(antiMap.get("exp_cooldown_ticks"), cooldownTicks);
            ignoreNearSpawner = bool(antiMap.get("ignore_near_spawner"), false);
            spawnerScanRadius = (int) number(antiMap.get("spawner_scan_radius"), spawnerScanRadius);
        }
        int expireTicks = 1200;
        Object attribution = values == null ? null : values.get("attribution");
        if (attribution instanceof Map<?, ?> attributionMap) {
            expireTicks = (int) number(attributionMap.get("expire_ticks"), expireTicks);
        }
        return new SourceRuleConfig(
                id,
                bool(get(values, "enabled"), true),
                string(get(values, "type"), "main"),
                string(get(values, "trigger"), ""),
                bool(get(values, "include_players"), false),
                ignorePlaced,
                Math.max(0, cooldownTicks),
                ignoreNearSpawner,
                Math.max(0, spawnerScanRadius),
                bool(get(values, "count_result_amount"), false),
                expireTicks,
                rules,
                gainActions
        );
    }

    public record Rule(Set<String> entityTypes,
            Set<String> blocks,
            Set<String> states,
            Set<String> potionTypes,
            Set<String> mobIds,
            String expFormula,
            Matcher matcher) {

        public Rule {
            entityTypes = normalizeSet(entityTypes);
            blocks = normalizeSet(blocks);
            states = normalizeSet(states);
            potionTypes = normalizeSet(potionTypes);
            mobIds = normalizeSet(mobIds);
            expFormula = Texts.isBlank(expFormula) ? "0" : expFormula;
        }

        static Rule parse(Map<?, ?> map) {
            return new Rule(
                    normalizedStringSet(map.get("entity_types")),
                    normalizedStringSet(map.get("blocks")),
                    normalizedStringSet(map.get("states")),
                    normalizedStringSet(map.get("potion_types")),
                    normalizedStringSet(map.get("mob_ids")),
                    string(map.get("exp_formula"), "0"),
                    map.get("matcher") == null ? null : Matcher.fromConfig(map.get("matcher"))
            );
        }
    }

    private static Object get(Map<?, ?> map, String key) {
        return map == null ? null : map.get(key);
    }

    private static String string(Object value, String fallback) {
        return Texts.isBlank(value) ? fallback : Texts.toStringSafe(value);
    }

    private static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (Texts.isBlank(value)) {
            return fallback;
        }
        return Boolean.parseBoolean(Texts.toStringSafe(value));
    }

    private static double number(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(Texts.toStringSafe(value));
        } catch (Exception _) {
            return fallback;
        }
    }

    private static List<String> stringList(Object raw) {
        if (raw instanceof Iterable<?> iterable) {
            List<String> result = new ArrayList<>();
            for (Object entry : iterable) {
                if (Texts.isNotBlank(entry)) {
                    result.add(Texts.toStringSafe(entry));
                }
            }
            return List.copyOf(result);
        }
        return Texts.isBlank(raw) ? List.of() : List.of(Texts.toStringSafe(raw));
    }

    private static Set<String> normalizedStringSet(Object raw) {
        Set<String> result = new LinkedHashSet<>();
        for (String value : stringList(raw)) {
            result.add(value.toUpperCase(Locale.ROOT));
        }
        return result;
    }

    private static Set<String> normalizeSet(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (Texts.isNotBlank(value)) {
                result.add(value.toUpperCase(Locale.ROOT));
            }
        }
        return Set.copyOf(result);
    }
}
