package emaki.jiuwu.craft.station.recipe;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.condition.ConditionBlock;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.matcher.Matcher;
import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;

public final class RecipeLoader extends YamlDirectoryLoader<RecipeDefinition> {

    private final int warnMaterialTypes;

    public RecipeLoader(JavaPlugin plugin, int warnMaterialTypes) {
        super(plugin);
        this.warnMaterialTypes = Math.max(1, warnMaterialTypes);
    }

    @Override
    protected String directoryName() {
        return "recipes";
    }

    @Override
    protected String typeName() {
        return "recipe";
    }

    @Override
    protected String idOf(RecipeDefinition value) {
        return value == null ? null : value.id();
    }

    @Override
    protected RecipeDefinition parse(File file, YamlSection configuration) {
        if (configuration == null) {
            return null;
        }
        String id = normalizeId(configuration.getString("id"));
        if (id == null) {
            issue("station.recipe_missing_id", Map.of("file", fileName(file)));
            return null;
        }
        List<MaterialRequirement> requirements = parseRequirements(file, id, configuration);
        if (requirements.isEmpty()) {
            issue("station.recipe_no_materials", Map.of("file", fileName(file), "recipe", id));
            return null;
        }
        if (requirements.size() > warnMaterialTypes) {
            issue("station.recipe_many_materials", Map.of("recipe", id,
                    "count", String.valueOf(requirements.size()),
                    "limit", String.valueOf(warnMaterialTypes)));
        }
        YamlSection result = configuration.getSection("result");
        List<RecipeOutput> outputs = parseOutputs(file, id, result);
        if (outputs.isEmpty()) {
            issue("station.recipe_no_outputs", Map.of("file", fileName(file), "recipe", id));
            return null;
        }
        long duration = readLong(configuration.get("duration_seconds"), 0L);
        YamlSection actions = configuration.getSection("actions");
        return new RecipeDefinition(id,
                configuration.getString("display_name", id),
                parseTags(configuration),
                parseStationIds(configuration),
                requirements,
                duration,
                outputs,
                result == null ? List.of() : result.getStringList("actions"),
                configuration.getString("permission", ""),
                ConditionBlock.fromRoot(configuration, true, false),
                actions == null ? List.of() : actions.getStringList("pre"),
                actions == null ? List.of() : actions.getStringList("success"),
                actions == null ? List.of() : actions.getStringList("failure"),
                parseCost(id, configuration),
                configuration.getBoolean("visible", Boolean.TRUE),
                ConditionBlock.fromConfig(configuration.getSection("display_condition"), true, false));
    }

    private RecipeCost parseCost(String recipeId, YamlSection configuration) {
        YamlSection cost = configuration.getSection("cost");
        if (cost == null) {
            return RecipeCost.none();
        }
        YamlSection currency = cost.getSection("currency");
        if (currency == null) {
            return RecipeCost.none();
        }
        String type = currency.getString("type", "");
        long amount = readLong(currency.get("amount"), 0L);
        if (type.isBlank() && amount <= 0L) {
            return RecipeCost.none();
        }
        if (amount <= 0L) {
            issue("station.recipe_bad_cost_amount", Map.of("recipe", recipeId,
                    "amount", String.valueOf(amount)));
            return RecipeCost.none();
        }
        RecipeCost parsed = RecipeCost.fromToken(type, amount);
        if (parsed == null) {
            issue("station.recipe_bad_currency", Map.of("recipe", recipeId, "type", type));
            return RecipeCost.none();
        }
        return parsed;
    }

    private Set<String> parseTags(YamlSection configuration) {
        Set<String> tags = new LinkedHashSet<>();
        for (String tag : configuration.getStringList("tags")) {
            if (tag != null && !tag.isBlank()) {
                tags.add(tag.trim().toLowerCase(Locale.ROOT));
            }
        }
        return tags;
    }

    private Set<String> parseStationIds(YamlSection configuration) {
        Set<String> stationIds = new LinkedHashSet<>();
        for (String stationId : configuration.getStringList("station_ids")) {
            if (stationId != null && !stationId.isBlank()) {
                stationIds.add(stationId.trim().toLowerCase(Locale.ROOT));
            }
        }
        return stationIds;
    }

    private List<MaterialRequirement> parseRequirements(File file, String recipeId, YamlSection configuration) {
        List<MaterialRequirement> parsed = new ArrayList<>();
        for (Map<?, ?> raw : configuration.getMapList("materials")) {
            YamlSection entry = section(raw);
            if (entry == null) {
                continue;
            }
            List<ItemSourceRef> sources = parseSources(file, recipeId, entry);
            YamlSection matcherSection = entry.getSection("matcher");
            Matcher matcher = matcherSection == null ? null : Matcher.fromConfig(matcherSection);
            if (sources.isEmpty() && matcher == null) {
                continue;
            }
            long amount = readLong(entry.get("amount"), 1L);
            if (amount <= 0L) {
                issue("station.recipe_bad_amount", Map.of("recipe", recipeId,
                        "amount", String.valueOf(amount)));
                continue;
            }
            parsed.add(new MaterialRequirement(sources, amount,
                    entry.getBoolean("consume", Boolean.TRUE), matcher));
        }
        return parsed;
    }

    private List<RecipeOutput> parseOutputs(File file, String recipeId, YamlSection result) {
        if (result == null) {
            return List.of();
        }
        List<RecipeOutput> parsed = new ArrayList<>();
        for (Map<?, ?> raw : result.getMapList("outputs")) {
            YamlSection entry = section(raw);
            if (entry == null) {
                continue;
            }
            List<ItemSourceRef> sources = parseSources(file, recipeId, entry);
            if (sources.isEmpty()) {
                continue;
            }
            long amount = readLong(entry.get("amount"), 1L);
            if (amount <= 0L) {
                issue("station.recipe_bad_amount", Map.of("recipe", recipeId,
                        "amount", String.valueOf(amount)));
                continue;
            }
            parsed.add(new RecipeOutput(sources.getFirst(), amount));
        }
        return parsed;
    }

    private List<ItemSourceRef> parseSources(File file, String recipeId, YamlSection entry) {
        List<String> tokens = new ArrayList<>(entry.getStringList("item_sources"));
        String single = entry.getString("item_source");
        if (single != null && !single.isBlank()) {
            tokens.add(single);
        }
        List<ItemSourceRef> refs = new ArrayList<>();
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            ItemSourceRef ref = ItemSourceUtil.parse(token);
            if (ref == null) {
                issue("station.recipe_bad_source", Map.of("recipe", recipeId,
                        "source", token, "file", fileName(file)));
                continue;
            }
            if (!refs.contains(ref)) {
                refs.add(ref);
            }
        }
        return refs;
    }

    private static YamlSection section(Map<?, ?> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null) {
                normalized.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return normalized.isEmpty() ? null : new MapYamlSection(normalized);
    }

    private static long readLong(Object raw, long fallback) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static String normalizeId(String raw) {
        if (Texts.isBlank(raw)) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private static String fileName(File file) {
        return file == null ? "?" : file.getName();
    }
}
