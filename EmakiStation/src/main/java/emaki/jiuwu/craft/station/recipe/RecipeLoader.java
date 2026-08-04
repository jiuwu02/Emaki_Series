package emaki.jiuwu.craft.station.recipe;

import java.io.File;
import java.util.ArrayList;
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
import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;

/**
 * Loads {@code recipes/**.yml} into {@link RecipeDefinition}s.
 *
 * <p>Item tokens are resolved through CoreLib's public item-source contract rather than by hard-coding
 * prefixes, so a recipe may name any source a provider plugin has registered without EmakiStation
 * knowing that plugin exists.
 *
 * <p>A file whose requirements or outputs cannot be parsed is skipped with a recorded issue instead of
 * aborting the whole directory: one bad recipe must not cost an administrator every other one.
 */
public final class RecipeLoader extends YamlDirectoryLoader<RecipeDefinition> {

    private final int warnMaterialTypes;

    /**
     * Creates the loader.
     *
     * @param plugin            the owning plugin
     * @param warnMaterialTypes the requirement count above which a recipe only logs a warning
     */
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
                requirements,
                duration,
                outputs,
                result == null ? List.of() : result.getStringList("actions"),
                configuration.getString("permission", ""),
                ConditionBlock.fromRoot(configuration, true, false),
                actions == null ? List.of() : actions.getStringList("pre"),
                actions == null ? List.of() : actions.getStringList("success"),
                actions == null ? List.of() : actions.getStringList("failure"));
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

    private List<MaterialRequirement> parseRequirements(File file, String recipeId, YamlSection configuration) {
        List<MaterialRequirement> parsed = new ArrayList<>();
        for (Map<?, ?> raw : configuration.getMapList("materials")) {
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
            parsed.add(new MaterialRequirement(sources, amount,
                    entry.getBoolean("consume", Boolean.TRUE)));
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

    /**
     * Wraps a raw YAML map entry as a section.
     *
     * <p>{@code getMapList} yields {@code Map<?, ?>} whose keys are untyped, so they are re-keyed by
     * their string form before being handed to {@link MapYamlSection}.
     *
     * @param raw the raw map; {@code null} and empty yield {@code null}
     * @return a section view, or {@code null} when there is nothing to read
     */
    private static YamlSection section(Map<?, ?> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        Map<String, Object> normalized = new java.util.LinkedHashMap<>();
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
