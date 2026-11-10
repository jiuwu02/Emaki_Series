package emaki.jiuwu.craft.cooking.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.matcher.ItemRequirement;
import emaki.jiuwu.craft.corelib.matcher.Matcher;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.cooking.model.NutritionComboThreshold;
import emaki.jiuwu.craft.cooking.model.NutritionCompare;
import emaki.jiuwu.craft.cooking.model.NutritionFoodSource;
import emaki.jiuwu.craft.cooking.model.NutritionSingleThreshold;
import emaki.jiuwu.craft.cooking.model.StationInteraction;
import emaki.jiuwu.craft.cooking.model.StationInteractionType;
import emaki.jiuwu.craft.cooking.model.StationType;

final class CookingSettingsServiceDelegate2 {

    private final CookingSettingsService ownerRef;

    CookingSettingsServiceDelegate2(CookingSettingsService ownerRef) {
        this.ownerRef = ownerRef;
    }

    int nutritionSaveIntervalSeconds() {
        return Math.max(0, ownerRef.configuration.getInt("nutrition.save_interval_seconds", 300));
    }

    List<NutritionFoodSource> nutritionFoodSources() {
        List<NutritionFoodSource> result = new ArrayList<>();
        for (Map<?, ?> entry : ownerRef.configuration.getMapList("nutrition.food_sources")) {
            Map<String, Object> normalized = MapYamlSection.normalizeMap(entry);
            List<ItemSourceRef> sources = CookingSettingsService.parseSources(normalized.get("item_sources"));
            Matcher matcher = CookingMatchers.parse(normalized, "matcher");
            if (sources.isEmpty() && matcher == null) {
                continue;
            }
            Map<String, Double> nutrition = ownerRef.parseNutritionAmounts(normalized.get("nutrition"));
            List<String> actions = ownerRef.stringList(normalized.get("actions"));
            if (nutrition.isEmpty() && actions.isEmpty()) {
                continue;
            }
            result.add(new NutritionFoodSource(sources, nutrition, actions, matcher));
        }
        return List.copyOf(result);
    }

    Map<String, Double> parseNutritionAmounts(Object raw) {
        Map<String, Object> normalized = MapYamlSection.normalizeMap(raw instanceof Map<?, ?> map ? map : Map.of());
        Map<String, Double> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : normalized.entrySet()) {
            String typeId = Texts.normalizeId(entry.getKey());
            if (Texts.isBlank(typeId)) {
                continue;
            }
            Double amount = ownerRef.configurationValueToDouble(entry.getValue(), Double.NaN);
            if (amount != null && !amount.isNaN()) {
                result.put(typeId, amount);
            }
        }
        return result;
    }

    List<String> stringList(Object raw) {
        List<String> result = new ArrayList<>();
        for (Object token : ConfigNodes.asObjectList(raw)) {
            String value = Texts.toStringSafe(token);
            if (Texts.isNotBlank(value)) {
                result.add(value);
            }
        }
        return result;
    }

    List<String> normalizeIds(List<String> raw) {
        List<String> result = new ArrayList<>();
        for (String token : raw) {
            String id = Texts.normalizeId(token);
            if (Texts.isNotBlank(id)) {
                result.add(id);
            }
        }
        return result;
    }

    Double configurationValueToDouble(Object raw, double fallback) {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        if (raw instanceof String text && Texts.isNotBlank(text)) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

}
