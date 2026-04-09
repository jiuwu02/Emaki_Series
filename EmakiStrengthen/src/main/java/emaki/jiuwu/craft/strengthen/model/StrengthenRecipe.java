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

    public record PresentationOperation(String type, Map<String, Object> values) {

        public PresentationOperation {
            type = Texts.lower(type);
            values = values == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(values));
        }

        public static PresentationOperation fromRaw(Map<?, ?> raw) {
            if (raw == null || raw.isEmpty()) {
                return null;
            }
            Map<String, Object> values = new LinkedHashMap<>();
            String type = "";
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                String key = Texts.lower(String.valueOf(entry.getKey()));
                Object value = entry.getValue();
                values.put(key, value);
                if ("type".equals(key)) {
                    type = Texts.lower(value);
                }
            }
            if (Texts.isBlank(type)) {
                return null;
            }
            return new PresentationOperation(type, values);
        }

        public String string(String key) {
            return Texts.toStringSafe(values.get(Texts.lower(key)));
        }

        public boolean bool(String key, boolean defaultValue) {
            Object value = values.get(Texts.lower(key));
            if (value == null) {
                return defaultValue;
            }
            if (value instanceof Boolean boolValue) {
                return boolValue;
            }
            return Boolean.parseBoolean(Texts.toStringSafe(value));
        }

        public int intValue(String key, int defaultValue) {
            return Numbers.tryParseInt(values.get(Texts.lower(key)), defaultValue);
        }
    }

    public record StatLineDefinition(String template,
            List<PresentationOperation> loreOperations,
            List<PresentationOperation> nameOperations) {

        public StatLineDefinition {
            template = Texts.toStringSafe(template);
            loreOperations = loreOperations == null ? List.of() : List.copyOf(loreOperations);
            nameOperations = nameOperations == null ? List.of() : List.copyOf(nameOperations);
        }
    }

    public record PresentationConfig(List<PresentationOperation> nameOperations,
            List<String> lorePrepend) {

        public PresentationConfig {
            nameOperations = nameOperations == null ? List.of() : List.copyOf(nameOperations);
            lorePrepend = normalizeList(lorePrepend);
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
            List<StarStageMaterial> materials,
            EconomyOverride economyOverride,
            List<String> presentation,
            List<String> successActions,
            List<String> failureActions) {

        public StarStage {
            name = Texts.toStringSafe(name);
            stats = stats == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(stats));
            materials = materials == null ? List.of() : List.copyOf(materials);
            economyOverride = economyOverride == null ? new EconomyOverride(List.of()) : economyOverride;
            presentation = normalizeList(presentation);
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
    private final PresentationConfig presentation;

    public StrengthenRecipe(String id,
            String displayName,
            String guiTemplate,
            EconomyConfig economy,
            Limits limits,
            Map<Integer, Double> successRates,
            MatchRule matchRule,
            Map<String, StatLineDefinition> statLines,
            Map<Integer, StarStage> stars,
            PresentationConfig presentation) {
        this.id = Texts.trim(id);
        this.displayName = Texts.toStringSafe(displayName);
        this.guiTemplate = Texts.isBlank(guiTemplate) ? "strengthen_gui" : Texts.toStringSafe(guiTemplate);
        this.economy = economy == null ? new EconomyConfig(false, List.of()) : economy;
        this.limits = limits == null ? Limits.defaults() : limits;
        this.successRates = successRates == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(successRates));
        this.matchRule = matchRule == null ? new MatchRule(List.of(), List.of(), List.of(), List.of(), List.of(), List.of()) : matchRule;
        this.statLines = statLines == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(statLines));
        this.stars = stars == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(stars));
        this.presentation = presentation == null ? new PresentationConfig(List.of(), List.of()) : presentation;
    }

    public static StrengthenRecipe fromConfig(ConfigurationSection section) {
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
                parseEconomy(section.getConfigurationSection("economy")),
                parseLimits(section.getConfigurationSection("limits")),
                parseSuccessRates(section.getConfigurationSection("success_rates")),
                parseMatchRule(section.getConfigurationSection("match")),
                parseStatLines(section.getConfigurationSection("stat_lines")),
                parseStars(section.getConfigurationSection("stars")),
                parsePresentation(section.getConfigurationSection("presentation"))
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

    public PresentationConfig presentation() {
        return presentation;
    }

    private static EconomyConfig parseEconomy(ConfigurationSection section) {
        if (section == null) {
            return new EconomyConfig(false, List.of());
        }
        List<CurrencyEntry> currencies = parseCurrencies(section.getMapList("currencies"));
        boolean enabled = section.contains("enabled") ? section.getBoolean("enabled") : !currencies.isEmpty();
        return new EconomyConfig(enabled, currencies);
    }

    private static EconomyOverride parseEconomyOverride(ConfigurationSection section) {
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
                    Texts.toStringSafe(rawEntry.get("provider")),
                    Texts.toStringSafe(rawEntry.get("currency_id")),
                    Numbers.tryParseLong(rawEntry.get("base_cost"), 0L),
                    Texts.toStringSafe(rawEntry.get("cost_formula")),
                    Texts.toStringSafe(rawEntry.get("display_name"))
            ));
        }
        return List.copyOf(currencies);
    }

    private static Limits parseLimits(ConfigurationSection section) {
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

    private static Map<Integer, Double> parseSuccessRates(ConfigurationSection section) {
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

    private static MatchRule parseMatchRule(ConfigurationSection section) {
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

    private static Map<String, StatLineDefinition> parseStatLines(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, StatLineDefinition> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection statSection = section.getConfigurationSection(key);
            if (statSection == null) {
                String template = Texts.toStringSafe(section.get(key));
                result.put(Texts.lower(key), new StatLineDefinition(template, List.of(), List.of()));
                continue;
            }
            result.put(Texts.lower(key), new StatLineDefinition(
                    statSection.getString("template", ""),
                    parseOperations(statSection.getMapList("lore_operations")),
                    parseOperations(statSection.getMapList("name_operations"))
            ));
        }
        return result;
    }

    private static Map<Integer, StarStage> parseStars(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<Integer, StarStage> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Integer targetStar = Numbers.tryParseInt(key, null);
            if (targetStar == null || targetStar <= 0) {
                continue;
            }
            ConfigurationSection stageSection = section.getConfigurationSection(key);
            if (stageSection == null) {
                continue;
            }
            result.put(targetStar, new StarStage(
                    targetStar,
                    stageSection.getString("name", ""),
                    parseDoubleMap(stageSection.getConfigurationSection("stats")),
                    parseStageMaterials(stageSection.getMapList("materials")),
                    parseEconomyOverride(stageSection.getConfigurationSection("economy_override")),
                    stageSection.getStringList("presentation"),
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
                    Texts.toStringSafe(rawEntry.get("item")),
                    Numbers.tryParseInt(rawEntry.get("amount"), 1),
                    Boolean.TRUE.equals(rawEntry.get("optional")),
                    Boolean.TRUE.equals(rawEntry.get("protection")),
                    Numbers.tryParseInt(rawEntry.get("temper_boost"), 0)
            ));
        }
        return List.copyOf(result);
    }

    private static PresentationConfig parsePresentation(ConfigurationSection section) {
        if (section == null) {
            return new PresentationConfig(List.of(), List.of());
        }
        return new PresentationConfig(
                parseOperations(section.getMapList("name_operations")),
                section.getStringList("lore_prepend")
        );
    }

    private static List<PresentationOperation> parseOperations(List<Map<?, ?>> rawEntries) {
        if (rawEntries == null || rawEntries.isEmpty()) {
            return List.of();
        }
        List<PresentationOperation> result = new ArrayList<>();
        for (Map<?, ?> rawEntry : rawEntries) {
            PresentationOperation operation = PresentationOperation.fromRaw(rawEntry);
            if (operation != null) {
                result.add(operation);
            }
        }
        return List.copyOf(result);
    }

    private static Map<String, Double> parseDoubleMap(ConfigurationSection section) {
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
