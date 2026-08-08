package emaki.jiuwu.craft.station.dismantle;

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
import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;

/**
 * Loads {@code recipes_dismantle/**.yml} into {@link DismantleRecipeDefinition}s.
 *
 * <p>Item tokens are resolved through CoreLib's public item-source contract. A file whose
 * {@code input_source} or {@code pool} cannot be parsed is skipped with a recorded issue.
 */
public final class DismantleRecipeLoader extends YamlDirectoryLoader<DismantleRecipeDefinition> {

    /**
     * Creates the loader.
     *
     * @param plugin the owning plugin
     */
    public DismantleRecipeLoader(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    protected String directoryName() {
        return "recipes_dismantle";
    }

    @Override
    protected String typeName() {
        return "dismantle_recipe";
    }

    @Override
    protected String idOf(DismantleRecipeDefinition value) {
        return value == null ? null : value.id();
    }

    @Override
    protected DismantleRecipeDefinition parse(File file, YamlSection configuration) {
        if (configuration == null) {
            return null;
        }
        String id = normalizeId(configuration.getString("id"));
        if (id == null) {
            issue("station.dismantle_recipe_missing_id", Map.of("file", fileName(file)));
            return null;
        }

        // input_source
        String inputToken = configuration.getString("input_source");
        if (inputToken == null || inputToken.isBlank()) {
            issue("station.dismantle_recipe_no_input", Map.of("recipe", id, "file", fileName(file)));
            return null;
        }
        ItemSourceRef inputSource = ItemSourceUtil.parse(inputToken);
        if (inputSource == null) {
            issue("station.dismantle_recipe_bad_input", Map.of("recipe", id,
                    "source", inputToken, "file", fileName(file)));
            return null;
        }

        // rolls
        RollsRange rolls = parseRolls(configuration);

        // pool
        List<DismantlePoolEntry> pool = parsePool(file, id, configuration);
        if (pool.isEmpty()) {
            issue("station.dismantle_recipe_empty_pool", Map.of("recipe", id, "file", fileName(file)));
            return null;
        }

        return new DismantleRecipeDefinition(
                id,
                configuration.getString("display_name", id),
                configuration.getString("station_id", ""),
                parseTags(configuration),
                inputSource,
                rolls,
                pool,
                configuration.getString("permission", ""),
                ConditionBlock.fromRoot(configuration, true, false));
    }

    private RollsRange parseRolls(YamlSection configuration) {
        YamlSection rolls = configuration.getSection("rolls");
        if (rolls != null) {
            return RollsRange.of(rolls.get("min"), rolls.get("max"));
        }
        // Support shorthand: rolls: 2  (scalar)
        Object raw = configuration.get("rolls");
        if (raw instanceof Number n) {
            int v = n.intValue();
            return new RollsRange(v, v);
        }
        return RollsRange.one();
    }

    private List<DismantlePoolEntry> parsePool(File file, String recipeId, YamlSection configuration) {
        List<DismantlePoolEntry> parsed = new ArrayList<>();
        for (Map<?, ?> raw : configuration.getMapList("pool")) {
            YamlSection entry = section(raw);
            if (entry == null) {
                continue;
            }
            String token = entry.getString("item_source");
            if (token == null || token.isBlank()) {
                continue;
            }
            ItemSourceRef source = ItemSourceUtil.parse(token);
            if (source == null) {
                issue("station.dismantle_recipe_bad_source", Map.of("recipe", recipeId,
                        "source", token, "file", fileName(file)));
                continue;
            }
            AmountRange amount = parseAmount(entry);
            double weight = readDouble(entry.get("weight"), 1.0);
            if (weight <= 0.0) {
                weight = 1.0;
            }
            parsed.add(new DismantlePoolEntry(source, amount, weight));
        }
        return parsed;
    }

    private AmountRange parseAmount(YamlSection entry) {
        YamlSection amountSection = entry.getSection("amount");
        if (amountSection != null) {
            return AmountRange.of(amountSection.get("min"), amountSection.get("max"));
        }
        Object raw = entry.get("amount");
        if (raw != null) {
            return AmountRange.of(raw, raw);
        }
        return AmountRange.one();
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

    private static double readDouble(Object raw, double fallback) {
        if (raw instanceof Number n) {
            return n.doubleValue();
        }
        if (raw instanceof String s && !s.isBlank()) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
                // fall through
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
