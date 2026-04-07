package emaki.jiuwu.craft.strengthen.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;

import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class StrengthenProfile {

    public record StarStage(int targetStar, Map<String, Double> stats) {

        public StarStage {
            stats = stats == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(stats));
        }
    }

    public record Milestone(int star, String name, Map<String, Double> stats, List<String> presentation, List<String> action) {

        public Milestone {
            stats = stats == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(stats));
            presentation = presentation == null ? List.of() : List.copyOf(presentation);
            action = action == null ? List.of() : List.copyOf(action);
        }
    }

    private final String id;
    private final String displayName;
    private final long baseCost;
    private final Map<String, String> statLineTemplates;
    private final Map<Integer, StarStage> stars;
    private final Map<Integer, Milestone> milestones;

    public StrengthenProfile(String id,
            String displayName,
            long baseCost,
            Map<String, String> statLineTemplates,
            Map<Integer, StarStage> stars,
            Map<Integer, Milestone> milestones) {
        this.id = Texts.trim(id);
        this.displayName = displayName;
        this.baseCost = Math.max(0L, baseCost);
        this.statLineTemplates = statLineTemplates == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(statLineTemplates));
        this.stars = stars == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(stars));
        this.milestones = milestones == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(milestones));
    }

    public static StrengthenProfile fromConfig(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String id = section.getString("id");
        if (Texts.isBlank(id)) {
            return null;
        }
        Map<String, String> statLines = new LinkedHashMap<>();
        ConfigurationSection statLineSection = section.getConfigurationSection("stat_lines");
        if (statLineSection != null) {
            for (String key : statLineSection.getKeys(false)) {
                statLines.put(Texts.lower(key), Texts.toStringSafe(statLineSection.get(key)));
            }
        }
        Map<Integer, StarStage> stars = new LinkedHashMap<>();
        ConfigurationSection starsSection = section.getConfigurationSection("stars");
        if (starsSection != null) {
            for (String key : starsSection.getKeys(false)) {
                int targetStar = Numbers.tryParseInt(key, -1);
                if (targetStar <= 0) {
                    continue;
                }
                stars.put(targetStar, new StarStage(targetStar, parseDoubleMap(starsSection.getConfigurationSection(key), "stats")));
            }
        }
        Map<Integer, Milestone> milestones = new LinkedHashMap<>();
        ConfigurationSection milestoneSection = section.getConfigurationSection("milestones");
        if (milestoneSection != null) {
            for (String key : milestoneSection.getKeys(false)) {
                int star = Numbers.tryParseInt(key, -1);
                if (star <= 0) {
                    continue;
                }
                ConfigurationSection value = milestoneSection.getConfigurationSection(key);
                if (value == null) {
                    continue;
                }
                milestones.put(star, new Milestone(
                        star,
                        value.getString("name", "+" + star),
                        parseDoubleMap(value.getConfigurationSection("stats"), null),
                        List.copyOf(value.getStringList("presentation")),
                        List.copyOf(value.getStringList("action"))
                ));
            }
        }
        return new StrengthenProfile(
                id,
                section.getString("display_name", id),
                Numbers.tryParseLong(section.get("base_cost"), 0L),
                statLines,
                stars,
                milestones
        );
    }

    public Map<String, Double> cumulativeStats(int currentStar) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (Map.Entry<Integer, StarStage> entry : stars.entrySet()) {
            if (entry.getKey() > currentStar || entry.getValue() == null) {
                continue;
            }
            merge(values, entry.getValue().stats());
        }
        for (Milestone milestone : reachedMilestones(currentStar)) {
            merge(values, milestone.stats());
        }
        return values;
    }

    public Map<String, Double> deltaStats(int fromStar, int toStar) {
        Map<String, Double> delta = new LinkedHashMap<>();
        Map<String, Double> from = cumulativeStats(fromStar);
        Map<String, Double> to = cumulativeStats(toStar);
        Set<String> ids = new LinkedHashSet<>();
        ids.addAll(from.keySet());
        ids.addAll(to.keySet());
        for (String id : ids) {
            double value = to.getOrDefault(id, 0D) - from.getOrDefault(id, 0D);
            if (Math.abs(value) > 1.0E-9D) {
                delta.put(id, value);
            }
        }
        return delta;
    }

    public List<Milestone> reachedMilestones(int currentStar) {
        List<Milestone> result = new ArrayList<>();
        for (Map.Entry<Integer, Milestone> entry : milestones.entrySet()) {
            if (entry.getKey() <= currentStar && entry.getValue() != null) {
                result.add(entry.getValue());
            }
        }
        result.sort(java.util.Comparator.comparingInt(Milestone::star));
        return result;
    }

    public Milestone milestone(int star) {
        return milestones.get(star);
    }

    private static void merge(Map<String, Double> target, Map<String, Double> source) {
        if (target == null || source == null) {
            return;
        }
        for (Map.Entry<String, Double> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            target.merge(Texts.lower(entry.getKey()), entry.getValue(), Double::sum);
        }
    }

    private static Map<String, Double> parseDoubleMap(ConfigurationSection section, String nestedPath) {
        ConfigurationSection actual = section;
        if (actual != null && Texts.isNotBlank(nestedPath)) {
            actual = actual.getConfigurationSection(nestedPath);
        }
        if (actual == null) {
            return Map.of();
        }
        Map<String, Double> values = new LinkedHashMap<>();
        for (String key : actual.getKeys(false)) {
            Double value = Numbers.tryParseDouble(actual.get(key), null);
            if (value == null) {
                continue;
            }
            values.put(Texts.lower(key), value);
        }
        return values;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public long baseCost() {
        return baseCost;
    }

    public Map<String, String> statLineTemplates() {
        return statLineTemplates;
    }

    public Map<Integer, StarStage> stars() {
        return stars;
    }

    public Map<Integer, Milestone> milestones() {
        return milestones;
    }
}
