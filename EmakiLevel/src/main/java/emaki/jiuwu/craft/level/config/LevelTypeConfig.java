package emaki.jiuwu.craft.level.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;

public record LevelTypeConfig(String id,
        boolean enabled,
        String displayName,
        List<String> description,
        boolean primary,
        int startLevel,
        int maxLevel,
        Requirement requirement,
        Upgrade upgrade,
        boolean pdcEnabled,
        boolean attributesEnabled,
        Map<String, String> attributes) {

    public LevelTypeConfig {
        id = Texts.normalizeId(id);
        displayName = Texts.isBlank(displayName) ? id : displayName;
        description = description == null ? List.of() : List.copyOf(description);
        requirement = requirement == null ? new Requirement("global", "", Map.of()) : requirement;
        upgrade = upgrade == null ? Upgrade.defaults() : upgrade;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static LevelTypeConfig parse(YamlSection section, String fallbackId, int defaultStart, int defaultMax) {
        String id = section.getString("id", fallbackId);
        YamlSection requirement = section.getSection("requirement");
        YamlSection upgrade = section.getSection("upgrade");
        YamlSection attrs = section.getSection("attributes.values");
        Map<String, String> attributeValues = new LinkedHashMap<>();
        if (attrs != null) {
            for (String key : attrs.getKeys(false)) {
                attributeValues.put(Texts.normalizeId(key), Texts.toStringSafe(attrs.get(key)));
            }
        }
        return new LevelTypeConfig(
                id,
                section.getBoolean("enabled", true),
                section.getString("display_name", id),
                section.getStringList("description"),
                section.getBoolean("primary", false),
                section.getInt("start_level", defaultStart),
                section.getInt("max_level", defaultMax),
                Requirement.parse(requirement),
                Upgrade.parse(upgrade),
                section.getBoolean("pdc.enabled", true),
                section.getBoolean("attributes.enabled", false),
                attributeValues
        );
    }

    public record Requirement(String group, String formula, Map<Integer, Double> values) {

        public Requirement {
            group = Texts.isBlank(group) ? "global" : Texts.normalizeId(group);
            formula = Texts.toStringSafe(formula);
            values = values == null ? Map.of() : Map.copyOf(values);
        }

        public static Requirement parse(YamlSection section) {
            if (section == null) {
                return new Requirement("global", "", Map.of());
            }
            return new Requirement(section.getString("group", "global"), section.getString("formula", ""), parseValues(section.getSection("values")));
        }
    }

    public record Upgrade(boolean enabled,
            boolean autoUpgrade,
            boolean manualUpgrade,
            Cost cost,
            Rewards rewards,
            Map<String, List<String>> actions) {

        public static Upgrade defaults() {
            return new Upgrade(true, true, true, Cost.defaults(), Rewards.defaults(), Map.of("success", List.of(), "failure", List.of(), "gain", List.of()));
        }

        public static Upgrade parse(YamlSection section) {
            if (section == null) {
                return defaults();
            }
            return new Upgrade(
                    section.getBoolean("enabled", true),
                    section.getBoolean("auto_upgrade", true),
                    section.getBoolean("manual_upgrade", true),
                    Cost.parse(section.getSection("cost")),
                    Rewards.parse(section.getSection("rewards")),
                    parseActions(section.getSection("actions"))
            );
        }
    }

    public record Cost(boolean enabled, List<CurrencyCost> currencies, List<MaterialCost> materials) {

        public static Cost defaults() {
            return new Cost(false, List.of(), List.of());
        }

        public static Cost parse(YamlSection section) {
            if (section == null) {
                return defaults();
            }
            List<CurrencyCost> currencies = new ArrayList<>();
            YamlSection economy = section.getSection("economy");
            if (economy != null && economy.getBoolean("enabled", false)) {
                for (Map<?, ?> entry : economy.getMapList("currencies")) {
                    currencies.add(CurrencyCost.parse(entry));
                }
            }
            List<MaterialCost> materials = new ArrayList<>();
            for (Map<?, ?> entry : section.getMapList("materials")) {
                materials.add(MaterialCost.parse(entry));
            }
            return new Cost(section.getBoolean("enabled", false), currencies, materials);
        }
    }

    public record CurrencyCost(String provider, String currencyId, double baseCost, String costFormula, String displayName) {

        static CurrencyCost parse(Map<?, ?> values) {
            return new CurrencyCost(string(values, "provider", "auto"), string(values, "currency_id", ""), number(values, "base_cost", 0D), string(values, "cost_formula", "%base_cost% * %target_level%"), string(values, "display_name", ""));
        }
    }

    public record MaterialCost(List<String> itemSources, long baseAmount, String amountFormula) {

        static MaterialCost parse(Map<?, ?> values) {
            return new MaterialCost(stringList(values.get("item_sources")), Math.round(number(values, "base_amount", number(values, "amount", 1D))), string(values, "amount_formula", "%base_amount%"));
        }
    }

    public record Rewards(List<ItemReward> items) {

        public static Rewards defaults() {
            return new Rewards(List.of());
        }

        public static Rewards parse(YamlSection section) {
            if (section == null) {
                return defaults();
            }
            List<ItemReward> items = new ArrayList<>();
            int index = 0;
            for (Map<?, ?> entry : section.getMapList("items")) {
                items.add(ItemReward.parse(entry, index));
                index++;
            }
            return new Rewards(items);
        }
    }

    public record ItemReward(String levels, String itemSource, int amount) {

        static ItemReward parse(Map<?, ?> values, int index) {
            String path = "upgrade.rewards.items[" + index + "]";
            if (values == null) {
                throw new IllegalArgumentException(path + ": output entry must be a mapping");
            }
            if (values.containsKey("item_source") && values.containsKey("item_sources")) {
                throw new IllegalArgumentException(path + ": item_source and item_sources cannot both be declared");
            }
            if (values.containsKey("matcher")) {
                throw new IllegalArgumentException(path + ".matcher: matcher is not allowed on output nodes");
            }
            String source;
            if (values.containsKey("item_source")) {
                Object raw = values.get("item_source");
                if (raw instanceof Iterable<?> && !(raw instanceof String)) {
                    throw new IllegalArgumentException(path + ".item_source: canonical item_source must be a single source");
                }
                source = string(values, "item_source", "");
            } else if (values.containsKey("item_sources")) {
                List<String> legacy = stringList(values.get("item_sources"));
                if (legacy.size() != 1) {
                    throw new IllegalArgumentException(path + ".item_sources: legacy item_sources must contain exactly one source");
                }
                source = legacy.getFirst();
            } else {
                throw new IllegalArgumentException(path + ".item_source: output must declare item_source");
            }
            ItemSourceRef parsed = ItemSourceUtil.parse(source);
            if (parsed == null) {
                throw new IllegalArgumentException(path + ".item_source: invalid item source");
            }
            return new ItemReward(string(values, "levels", "*"), ItemSourceUtil.toShorthand(parsed),
                    (int) Math.max(1, Math.round(number(values, "amount", 1D))));
        }

        public List<String> itemSources() {
            return itemSource.isBlank() ? List.of() : List.of(itemSource);
        }
    }

    private static Map<Integer, Double> parseValues(YamlSection section) {
        Map<Integer, Double> values = new LinkedHashMap<>();
        if (section == null) {
            return values;
        }
        for (String key : section.getKeys(false)) {
            try {
                values.put(Integer.parseInt(key), Double.parseDouble(Texts.toStringSafe(section.get(key))));
            } catch (NumberFormatException _) {
            }
        }
        return values;
    }

    private static Map<String, List<String>> parseActions(YamlSection section) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                result.put(Texts.normalizeId(key), section.getStringList(key));
            }
        }
        result.putIfAbsent("gain", List.of());
        result.putIfAbsent("success", List.of());
        result.putIfAbsent("failure", List.of());
        return result;
    }

    private static String string(Map<?, ?> map, String key, String fallback) {
        Object value = map == null ? null : map.get(key);
        return Texts.isBlank(value) ? fallback : Texts.toStringSafe(value);
    }

    private static double number(Map<?, ?> map, String key, double fallback) {
        Object value = map == null ? null : map.get(key);
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
}
