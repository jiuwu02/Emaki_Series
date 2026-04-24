package emaki.jiuwu.craft.strengthen.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

public final class StrengthenRecipe {

    public record MatchRule(List<String> sourceTypes,
            List<String> sourceIds,
            List<String> sourcePatterns,
            List<String> slotGroups,
            List<String> loreContains,
            List<String> statsAny) {

        public MatchRule {
            sourceTypes = normalizeLower(sourceTypes);
            sourceIds = normalizeLower(sourceIds);
            sourcePatterns = normalizeList(sourcePatterns);
            slotGroups = normalizeLower(slotGroups);
            loreContains = normalizeStripped(loreContains);
            statsAny = normalizeLower(statsAny);
        }

        public boolean empty() {
            return sourceTypes.isEmpty()
                    && sourceIds.isEmpty()
                    && sourcePatterns.isEmpty()
                    && slotGroups.isEmpty()
                    && loreContains.isEmpty()
                    && statsAny.isEmpty();
        }
    }

    public record CurrencyEntry(String provider,
            String currencyId,
            long baseCost,
            String costFormula,
            String displayName) {

        public CurrencyEntry {
            provider = Texts.lower(provider);
            currencyId = Texts.toStringSafe(currencyId);
            baseCost = Math.max(0L, baseCost);
            costFormula = Texts.isBlank(costFormula) ? "{base_cost}" : Texts.toStringSafe(costFormula);
            displayName = Texts.toStringSafe(displayName);
        }
    }

    public record EconomyConfig(boolean enabled, List<CurrencyEntry> currencies) {

        public EconomyConfig {
            currencies = currencies == null ? List.of() : List.copyOf(currencies);
        }
    }

    public record Limits(int maxStar,
            int maxTemper,
            double temperChanceBonusPerLevel,
            double successChanceCap) {

        public Limits {
            maxStar = Math.max(1, maxStar);
            maxTemper = Math.max(0, maxTemper);
            temperChanceBonusPerLevel = Math.max(0D, temperChanceBonusPerLevel);
            successChanceCap = Numbers.clamp(successChanceCap, 0D, 100D);
        }

        public static Limits defaults() {
            return new Limits(12, 4, 5D, 90D);
        }
    }

    public record StatLineDefinition(String template, String sectionId, int sectionOrder) {

        public StatLineDefinition {
            template = Texts.toStringSafe(template);
            sectionId = Texts.toStringSafe(sectionId);
            sectionOrder = Math.max(0, sectionOrder);
        }
    }

    public record EconomyOverride(List<CurrencyEntry> currencies) {

        public EconomyOverride {
            currencies = currencies == null ? List.of() : List.copyOf(currencies);
        }
    }

    public record StarStageMaterial(String item,
            int amount,
            boolean optional,
            boolean protection,
            int temperBoost) {

        public StarStageMaterial {
            item = Texts.toStringSafe(item);
            amount = amount == 0 ? 1 : amount;
            temperBoost = Math.max(0, temperBoost);
        }
    }

    public record StarStage(int targetStar,
            String name,
            Map<String, Double> stats,
            Map<String, Double> attributes,
            List<String> skillIds,
            List<StarStageMaterial> materials,
            EconomyOverride economyOverride,
            Object structuredPresentation,
            List<String> successActions,
            List<String> failureActions) {

        public StarStage {
            name = Texts.toStringSafe(name);
            stats = stats == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(stats));
            attributes = attributes == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(attributes));
            skillIds = normalizeList(skillIds).stream().map(Texts::normalizeId).filter(Texts::isNotBlank).distinct().toList();
            materials = materials == null ? List.of() : List.copyOf(materials);
            economyOverride = economyOverride == null ? new EconomyOverride(List.of()) : economyOverride;
            structuredPresentation = ConfigNodes.toPlainData(structuredPresentation);
            successActions = normalizeList(successActions);
            failureActions = normalizeList(failureActions);
        }
    }

    private final String id;
    private final String displayName;
    private final String guiTemplate;
    private final EconomyConfig economy;
    private final Limits limits;
    private final Map<Integer, Double> successRates;
    private final MatchRule matchRule;
    private final Map<String, StatLineDefinition> statLines;
    private final Map<Integer, StarStage> stars;
    private final Object structuredPresentation;

    public StrengthenRecipe(String id,
            String displayName,
            String guiTemplate,
            EconomyConfig economy,
            Limits limits,
            Map<Integer, Double> successRates,
            MatchRule matchRule,
            Map<String, StatLineDefinition> statLines,
            Map<Integer, StarStage> stars,
            Object structuredPresentation) {
        this.id = Texts.trim(id);
        this.displayName = Texts.toStringSafe(displayName);
        this.guiTemplate = Texts.isBlank(guiTemplate) ? "strengthen_gui" : Texts.toStringSafe(guiTemplate);
        this.economy = economy == null ? new EconomyConfig(false, List.of()) : economy;
        this.limits = limits == null ? Limits.defaults() : limits;
        this.successRates = successRates == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(successRates));
        this.matchRule = matchRule == null ? new MatchRule(List.of(), List.of(), List.of(), List.of(), List.of(), List.of()) : matchRule;
        this.statLines = statLines == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(statLines));
        this.stars = stars == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(stars));
        this.structuredPresentation = ConfigNodes.toPlainData(structuredPresentation);
    }

    public static StrengthenRecipe fromConfig(YamlSection section) {
        if (section == null) {
            return null;
        }
        String id = section.getString("id");
        if (Texts.isBlank(id)) {
            return null;
        }
        return new StrengthenRecipe(
                id,
                section.getString("display_name", id),
                section.getString("gui_template", "strengthen_gui"),
                parseEconomy(section.getSection("economy")),
                parseLimits(section.getSection("limits")),
                parseSuccessRates(section.getSection("success_rates")),
                parseMatchRule(section.getSection("match")),
                parseStatLines(section.getSection("stat_lines")),
                parseStars(section.getSection("stars")),
                section.get("structured_presentation")
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
        return values;
    }

    public Map<String, Double> cumulativeAttributes(int currentStar) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (Map.Entry<Integer, StarStage> entry : stars.entrySet()) {
            if (entry.getKey() > currentStar || entry.getValue() == null) {
                continue;
            }
            merge(values, entry.getValue().attributes());
        }
        return values;
    }

    public List<String> cumulativeSkillIds(int currentStar) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (Map.Entry<Integer, StarStage> entry : stars.entrySet()) {
            if (entry.getKey() > currentStar || entry.getValue() == null) {
                continue;
            }
            values.addAll(entry.getValue().skillIds());
        }
        return List.copyOf(values);
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

    public List<StarStage> reachedStages(int currentStar) {
        List<StarStage> result = new ArrayList<>();
        for (Map.Entry<Integer, StarStage> entry : stars.entrySet()) {
            if (entry.getKey() <= currentStar && entry.getValue() != null) {
                result.add(entry.getValue());
            }
        }
        result.sort(java.util.Comparator.comparingInt(StarStage::targetStar));
        return List.copyOf(result);
    }

    public StarStage stage(int targetStar) {
        return stars.get(targetStar);
    }

    public List<String> successActionsForTargetStar(int targetStar) {
        StarStage stage = stage(targetStar);
        return stage == null ? List.of() : stage.successActions();
    }

    public List<String> failureActionsForResultStar(int resultingStar) {
        StarStage stage = stage(resultingStar);
        return stage == null ? List.of() : stage.failureActions();
    }

    public List<CurrencyEntry> effectiveCurrencies(int targetStar) {
        StarStage stage = stage(targetStar);
        if (stage != null && stage.economyOverride() != null && !stage.economyOverride().currencies().isEmpty()) {
            return stage.economyOverride().currencies();
        }
        if (!economy.enabled()) {
            return List.of();
        }
        return economy.currencies();
    }

    public double successRateForTargetStar(Map<Integer, Double> globalSuccessRates, int targetStar) {
        if (successRates.containsKey(targetStar)) {
            return successRates.getOrDefault(targetStar, 0D);
        }
        return globalSuccessRates == null ? 0D : globalSuccessRates.getOrDefault(targetStar, 0D);
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String guiTemplate() {
        return guiTemplate;
    }

    public EconomyConfig economy() {
        return economy;
    }

    public Limits limits() {
        return limits;
    }

    public Map<Integer, Double> successRates() {
        return successRates;
    }

    public MatchRule matchRule() {
        return matchRule;
    }

    public Map<String, StatLineDefinition> statLines() {
        return statLines;
    }

    public Map<Integer, StarStage> stars() {
        return stars;
    }

    public Object structuredPresentation() {
        return structuredPresentation;
    }

    private static EconomyConfig parseEconomy(YamlSection section) {
        if (section == null) {
            return new EconomyConfig(false, List.of());
        }
        List<CurrencyEntry> currencies = parseCurrencies(section.getMapList("currencies"));
        Boolean enabledValue = section.getBoolean("enabled");
        boolean enabled = enabledValue != null ? enabledValue : !currencies.isEmpty();
        return new EconomyConfig(enabled, currencies);
    }

    private static EconomyOverride parseEconomyOverride(YamlSection section) {
        if (section == null) {
            return new EconomyOverride(List.of());
        }
        return new EconomyOverride(parseCurrencies(section.getMapList("currencies")));
    }

    private static List<CurrencyEntry> parseCurrencies(List<Map<?, ?>> rawEntries) {
        if (rawEntries == null || rawEntries.isEmpty()) {
            return List.of();
        }
        List<CurrencyEntry> currencies = new ArrayList<>();
        for (Map<?, ?> rawEntry : rawEntries) {
            if (rawEntry == null) {
                continue;
            }
            currencies.add(new CurrencyEntry(
                    ConfigNodes.string(rawEntry, "provider", ""),
                    ConfigNodes.string(rawEntry, "currency_id", ""),
                    Numbers.tryParseLong(ConfigNodes.get(rawEntry, "base_cost"), 0L),
                    ConfigNodes.string(rawEntry, "cost_formula", ""),
                    ConfigNodes.string(rawEntry, "display_name", "")
            ));
        }
        return List.copyOf(currencies);
    }

    private static Limits parseLimits(YamlSection section) {
        if (section == null) {
            return Limits.defaults();
        }
        Limits defaults = Limits.defaults();
        return new Limits(
                Numbers.tryParseInt(section.get("max_star"), defaults.maxStar()),
                Numbers.tryParseInt(section.get("max_temper"), defaults.maxTemper()),
                Numbers.tryParseDouble(section.get("temper_chance_bonus_per_level"), defaults.temperChanceBonusPerLevel()),
                Numbers.tryParseDouble(section.get("success_chance_cap"), defaults.successChanceCap())
        );
    }

    private static Map<Integer, Double> parseSuccessRates(YamlSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<Integer, Double> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Integer star = Numbers.tryParseInt(key, null);
            Double value = Numbers.tryParseDouble(section.get(key), null);
            if (star != null && value != null) {
                result.put(star, value);
            }
        }
        return result;
    }

    private static MatchRule parseMatchRule(YamlSection section) {
        if (section == null) {
            return new MatchRule(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }
        return new MatchRule(
                section.getStringList("source_types"),
                section.getStringList("source_ids"),
                section.getStringList("source_patterns"),
                section.getStringList("slot_groups"),
                section.getStringList("lore_contains"),
                section.getStringList("stats_any")
        );
    }

    private static Map<String, StatLineDefinition> parseStatLines(YamlSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, StatLineDefinition> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            YamlSection statSection = section.getSection(key);
            if (statSection == null) {
                String template = Texts.toStringSafe(section.get(key));
                result.put(Texts.lower(key), new StatLineDefinition(template, "", 0));
                continue;
            }
            result.put(Texts.lower(key), new StatLineDefinition(
                    statSection.getString("template", ""),
                    statSection.getString("section_id", ""),
                    Numbers.tryParseInt(statSection.get("section_order"), 0)
            ));
        }
        return result;
    }

    private static Map<Integer, StarStage> parseStars(YamlSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<Integer, StarStage> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Integer targetStar = Numbers.tryParseInt(key, null);
            if (targetStar == null || targetStar <= 0) {
                continue;
            }
            YamlSection stageSection = section.getSection(key);
            if (stageSection == null) {
                continue;
            }
            result.put(targetStar, new StarStage(
                    targetStar,
                    stageSection.getString("name", ""),
                    parseDoubleMap(stageSection.getSection("stats")),
                    parseDoubleMap(stageSection.getSection("attributes")),
                    parseSkillEffects(stageSection.getMapList("effects")),
                    parseStageMaterials(stageSection.getMapList("materials")),
                    parseEconomyOverride(stageSection.getSection("economy_override")),
                    stageSection.get("structured_presentation"),
                    stageSection.getStringList("success_actions"),
                    stageSection.getStringList("failure_actions")
            ));
        }
        return result;
    }

    private static List<StarStageMaterial> parseStageMaterials(List<Map<?, ?>> rawEntries) {
        if (rawEntries == null || rawEntries.isEmpty()) {
            return List.of();
        }
        List<StarStageMaterial> result = new ArrayList<>();
        for (Map<?, ?> rawEntry : rawEntries) {
            if (rawEntry == null) {
                continue;
            }
            result.add(new StarStageMaterial(
                    ConfigNodes.string(rawEntry, "item", ""),
                    Numbers.tryParseInt(ConfigNodes.get(rawEntry, "amount"), 1),
                    ConfigNodes.bool(rawEntry, "optional", false),
                    ConfigNodes.bool(rawEntry, "protection", false),
                    Numbers.tryParseInt(ConfigNodes.get(rawEntry, "temper_boost"), 0)
            ));
        }
        return List.copyOf(result);
    }

    private static List<String> parseSkillEffects(List<Map<?, ?>> rawEffects) {
        if (rawEffects == null || rawEffects.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Map<?, ?> rawEffect : rawEffects) {
            if (!"skill".equals(Texts.lower(ConfigNodes.string(rawEffect, "type", "")))) {
                continue;
            }
            for (Object rawSkill : ConfigNodes.asObjectList(ConfigNodes.get(rawEffect, "skills"))) {
                String skillId = Texts.normalizeId(Texts.toStringSafe(rawSkill));
                if (Texts.isNotBlank(skillId)) {
                    result.add(skillId);
                }
            }
            String skillId = Texts.normalizeId(ConfigNodes.string(rawEffect, "skill", ""));
            if (Texts.isNotBlank(skillId)) {
                result.add(skillId);
            }
        }
        return List.copyOf(result);
    }

    private static Map<String, Double> parseDoubleMap(YamlSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, Double> values = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Double value = Numbers.tryParseDouble(section.get(key), null);
            if (value != null) {
                values.put(Texts.lower(key), value);
            }
        }
        return values;
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

    private static List<String> normalizeLower(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String normalized = Texts.lower(value);
            if (Texts.isNotBlank(normalized)) {
                result.add(normalized);
            }
        }
        return List.copyOf(result);
    }

    private static List<String> normalizeStripped(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String normalized = Texts.stripMiniTags(value);
            if (Texts.isNotBlank(normalized)) {
                result.add(normalized);
            }
        }
        return List.copyOf(result);
    }

    private static List<String> normalizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String normalized = Texts.toStringSafe(value);
            if (Texts.isNotBlank(normalized)) {
                result.add(normalized);
            }
        }
        return List.copyOf(result);
    }
}
