package emaki.jiuwu.craft.strengthen.api.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * A fully resolved strengthen recipe: the rules, economy, limits, per-star
 * stages and optional branch tree that drive a strengthening profile.
 *
 * <p>This is an API model carrying already-parsed data; it does not read
 * configuration itself. It exposes cumulative calculations (stats, attributes,
 * skills) up to a given star, optionally along a branch path, plus per-star cost
 * and success-rate lookups.
 *
 * <p>Which items a recipe accepts is decided by the declarative
 * {@link #matcherConfig()} together with the top-level {@link #slotGroups()},
 * {@link #statsAny()} and {@link #sourcePatterns()} criteria, all combined as a
 * logical AND.
 *
 * <p>The nested records ({@link CurrencyEntry},
 * {@link EconomyConfig}, {@link Limits}, {@link StatLineDefinition},
 * {@link EconomyOverride}, {@link StarStageMaterial}, {@link StarStage}) model
 * the individual configuration sections. Nested collection components are defensively copied and immutable.
 */
public final class StrengthenRecipe {

    /**
     * A single currency cost entry.
     *
     * @param provider    the economy provider id (lower-cased)
     * @param currencyId  the currency id within the provider
     * @param baseCost    the base cost; clamped to {@code >= 0}
     * @param costFormula the cost expression (defaults to {@code "%base_cost%"})
     * @param displayName a human-readable label
     */
    public record CurrencyEntry(String provider,
            String currencyId,
            long baseCost,
            String costFormula,
            String displayName) {

        /** Canonical constructor; normalizes fields and defaults the formula. */
        public CurrencyEntry {
            provider = StrengthenApiValues.lower(provider);
            currencyId = StrengthenApiValues.toStringSafe(currencyId);
            baseCost = Math.max(0L, baseCost);
            costFormula = StrengthenApiValues.isBlank(costFormula) ? "%base_cost%" : StrengthenApiValues.toStringSafe(costFormula);
            displayName = StrengthenApiValues.toStringSafe(displayName);
        }
    }

    /**
     * The recipe-level economy configuration.
     *
     * @param enabled    whether currency costs apply by default
     * @param currencies the default currency entries; never {@code null}
     */
    public record EconomyConfig(boolean enabled, List<CurrencyEntry> currencies) {

        /** Canonical constructor; copies the currency list. */
        public EconomyConfig {
            currencies = currencies == null ? List.of() : List.copyOf(currencies);
        }
    }

    /**
     * Numeric limits and global tuning for the recipe.
     *
     * @param maxStar                   maximum reachable star (at least 1)
     * @param maxTemper                 maximum temper level
     * @param temperChanceBonusPerLevel success-chance bonus per temper level
     * @param successChanceCap          success-chance cap, clamped to 0-100
     */
    public record Limits(int maxStar,
            int maxTemper,
            double temperChanceBonusPerLevel,
            double successChanceCap) {

        /** Canonical constructor; clamps each limit to a sane range. */
        public Limits {
            maxStar = Math.max(1, maxStar);
            maxTemper = Math.max(0, maxTemper);
            temperChanceBonusPerLevel = Math.max(0D, temperChanceBonusPerLevel);
            successChanceCap = StrengthenApiValues.clamp(successChanceCap, 0D, 100D);
        }

        /** {@return the default limits (maxStar 12, maxTemper 4, +5/level, 90% cap)} */
        public static Limits defaults() {
            return new Limits(12, 4, 5D, 90D);
        }
    }

    /**
     * Definition of a generated stat display line.
     *
     * @param template     the line template
     * @param sectionId    the display section id
     * @param sectionOrder the ordering within the section; clamped to {@code >= 0}
     */
    public record StatLineDefinition(String template, String sectionId, int sectionOrder) {

        /** Canonical constructor; normalizes text and clamps the order. */
        public StatLineDefinition {
            template = StrengthenApiValues.toStringSafe(template);
            sectionId = StrengthenApiValues.toStringSafe(sectionId);
            sectionOrder = Math.max(0, sectionOrder);
        }
    }

    /**
     * A per-stage override of the economy currencies.
     *
     * @param currencies the overriding currency entries; never {@code null}
     */
    public record EconomyOverride(List<CurrencyEntry> currencies) {

        /** Canonical constructor; copies the currency list. */
        public EconomyOverride {
            currencies = currencies == null ? List.of() : List.copyOf(currencies);
        }
    }

    /**
     * A material requirement of a single star stage.
     *
     * @param item          the legacy material item id view
     * @param amount        the amount required (defaults to 1 when 0)
     * @param optional      whether the material is optional
     * @param protection    whether the material protects against failure
     * @param temperBoost   temper bonus granted; clamped to {@code >= 0}
     * @param materialId    the canonical rule and selection identity
     * @param countKey      the quantity aggregation and consumption identity
     * @param itemSources   accepted item source ids
     * @param matcherConfig the readable matcher configuration
     */
    public record StarStageMaterial(String item,
            int amount,
            boolean optional,
            boolean protection,
            int temperBoost,
            String materialId,
            String countKey,
            List<String> itemSources,
            Object matcherConfig,
            boolean legacyInput) {

        public StarStageMaterial {
            item = StrengthenApiValues.toStringSafe(item);
            amount = amount == 0 ? 1 : amount;
            temperBoost = Math.max(0, temperBoost);
            materialId = StrengthenApiValues.isBlank(materialId)
                    ? StrengthenApiValues.toStringSafe(item) : StrengthenApiValues.toStringSafe(materialId);
            countKey = StrengthenApiValues.isBlank(countKey)
                    ? materialId : StrengthenApiValues.toStringSafe(countKey);
            itemSources = itemSources == null ? List.of() : List.copyOf(itemSources);
            matcherConfig = StrengthenApiValues.toPlainData(matcherConfig);
        }

        public StarStageMaterial(String item,
                int amount,
                boolean optional,
                boolean protection,
                int temperBoost,
                String materialId,
                String countKey,
                List<String> itemSources,
                Object matcherConfig) {
            this(item, amount, optional, protection, temperBoost, materialId, countKey, itemSources, matcherConfig, false);
        }

        public StarStageMaterial(String item,
                int amount,
                boolean optional,
                boolean protection,
                int temperBoost) {
            this(item, amount, optional, protection, temperBoost, item, item,
                    StrengthenApiValues.isBlank(item) ? List.of() : List.of(item), null, true);
        }

        public StarStageMaterial(String materialId,
                String countKey,
                List<String> itemSources,
                Object matcherConfig,
                int amount,
                boolean optional,
                boolean protection,
                int temperBoost) {
            this(materialId, amount, optional, protection, temperBoost, materialId, countKey, itemSources, matcherConfig, false);
        }

        public List<String> sources() {
            return itemSources;
        }
    }

    /**
     * A single star stage of the recipe, holding the stats, attributes, skills,
     * materials, economy override and success/failure actions applied when the
     * stage is reached.
     *
     * @param targetStar      the star level this stage represents
     * @param name            the stage display name; never {@code null}
     * @param stats           raw stat values/expressions for the stage
     * @param attributes      attribute values for the stage
     * @param skillIds        skill ids granted at the stage (normalized)
     * @param materials       material requirements of the stage
     * @param economyOverride per-stage economy override; never {@code null}
     * @param successActions  actions run on success
     * @param failureActions  actions run on failure
     * @param nameActions     name actions applied while the stage is reached
     * @param loreActions     lore actions applied while the stage is reached
     */
    public record StarStage(int targetStar,
            String name,
            Map<String, Object> stats,
            Map<String, Object> attributes,
            List<String> skillIds,
            List<StarStageMaterial> materials,
            EconomyOverride economyOverride,
            List<String> successActions,
            List<String> failureActions,
            Object nameActions,
            Object loreActions) {

        /** Canonical constructor; normalizes and copies every collection field. */
        public StarStage {
            name = StrengthenApiValues.toStringSafe(name);
            stats = stats == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(stats));
            attributes = attributes == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(attributes));
            skillIds = normalizeList(skillIds).stream().map(StrengthenApiValues::normalizeId).filter(StrengthenApiValues::isNotBlank).distinct().toList();
            materials = materials == null ? List.of() : List.copyOf(materials);
            economyOverride = economyOverride == null ? new EconomyOverride(List.of()) : economyOverride;
            successActions = normalizeList(successActions);
            failureActions = normalizeList(failureActions);
            nameActions = StrengthenApiValues.toPlainData(nameActions);
            loreActions = StrengthenApiValues.toPlainData(loreActions);
        }
    }

    private final String id;
    private final String displayName;
    private final String guiTemplate;
    private final EconomyConfig economy;
    private final Limits limits;
    private final Map<Integer, Double> successRates;
    private final Map<String, StatLineDefinition> statLines;
    private final Map<Integer, StarStage> stars;
    private final StrengthenConditionGroup conditions;
    private final String conditionType;
    private final int conditionRequiredCount;
    private final StrengthenBranchNode branchTree;
    private final Object nameActions;
    private final Object loreActions;
    private final Object matcherConfig;
    private final List<String> slotGroups;
    private final List<String> statsAny;
    private final List<String> sourcePatterns;

    /**
     * Creates a recipe carrying only its structural configuration, with no
     * matching criteria and no name/lore actions.
     *
     * @param id                    the recipe id
     * @param displayName           the display name
     * @param guiTemplate           the GUI template id
     * @param economy               the economy configuration
     * @param limits                the numeric limits
     * @param successRates          per-target-star success rates
     * @param statLines             stat-line definitions keyed by id
     * @param stars                 star stages keyed by star level
     * @param conditions            activation conditions
     * @param conditionType         condition combination type
     * @param conditionRequiredCount required count for any-of conditions
     * @param branchTree            the branching star tree, may be {@code null}
     */
    public StrengthenRecipe(String id,
            String displayName,
            String guiTemplate,
            EconomyConfig economy,
            Limits limits,
            Map<Integer, Double> successRates,
            Map<String, StatLineDefinition> statLines,
            Map<Integer, StarStage> stars,
            StrengthenConditionGroup conditions,
            String conditionType,
            int conditionRequiredCount,
            StrengthenBranchNode branchTree) {
        this(id, displayName, guiTemplate, economy, limits, successRates, statLines, stars,
                conditions, conditionType, conditionRequiredCount, branchTree, null, null, null,
                List.of(), List.of(), List.of());
    }

    /**
     * Full constructor.
     *
     * @param id                    the recipe id
     * @param displayName           the display name
     * @param guiTemplate           the GUI template id (defaults to
     *                              {@code "strengthen_gui"})
     * @param economy               the economy configuration
     * @param limits                the numeric limits
     * @param successRates          per-target-star success rates
     * @param statLines             stat-line definitions keyed by id
     * @param stars                 star stages keyed by star level
     * @param conditions            activation conditions
     * @param conditionType         condition combination type (defaults to
     *                              {@code "all_of"})
     * @param conditionRequiredCount required count for any-of conditions
     * @param branchTree            the branching star tree, may be {@code null}
     * @param nameActions           raw name-action config, may be {@code null}
     * @param loreActions           raw lore-action config, may be {@code null}
     * @param matcherConfig         raw {@code matcher} config, may be
     *                              {@code null}
     * @param slotGroups            accepted slot groups (lower-cased)
     * @param statsAny              any-of stat ids that must be present
     *                              (lower-cased)
     * @param sourcePatterns        accepted item source shorthand patterns
     */
    public StrengthenRecipe(String id,
            String displayName,
            String guiTemplate,
            EconomyConfig economy,
            Limits limits,
            Map<Integer, Double> successRates,
            Map<String, StatLineDefinition> statLines,
            Map<Integer, StarStage> stars,
            StrengthenConditionGroup conditions,
            String conditionType,
            int conditionRequiredCount,
            StrengthenBranchNode branchTree,
            Object nameActions,
            Object loreActions,
            Object matcherConfig,
            List<String> slotGroups,
            List<String> statsAny,
            List<String> sourcePatterns) {
        this.id = StrengthenApiValues.trim(id);
        this.displayName = StrengthenApiValues.toStringSafe(displayName);
        this.guiTemplate = StrengthenApiValues.isBlank(guiTemplate) ? "strengthen_gui" : StrengthenApiValues.toStringSafe(guiTemplate);
        this.economy = economy == null ? new EconomyConfig(false, List.of()) : economy;
        this.limits = limits == null ? Limits.defaults() : limits;
        this.successRates = successRates == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(successRates));
        this.statLines = statLines == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(statLines));
        this.stars = stars == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(stars));
        this.conditions = conditions == null ? StrengthenConditionGroup.empty() : conditions;
        this.conditionType = StrengthenApiValues.isBlank(conditionType) ? "all_of" : StrengthenApiValues.lower(conditionType);
        this.conditionRequiredCount = Math.max(0, conditionRequiredCount);
        this.branchTree = branchTree;
        this.nameActions = StrengthenApiValues.toPlainData(nameActions);
        this.loreActions = StrengthenApiValues.toPlainData(loreActions);
        this.matcherConfig = StrengthenApiValues.toPlainData(matcherConfig);
        this.slotGroups = normalizeLower(slotGroups);
        this.statsAny = normalizeLower(statsAny);
        this.sourcePatterns = normalizeList(sourcePatterns);
    }


    /**
     * Computes the cumulative stat variables granted up to a star level.
     *
     * @param currentStar the inclusive star ceiling
     * @return resolved stat id to value mapping
     */
    public Map<String, Double> cumulativeVariables(int currentStar) {
        Map<String, Object> rawValues = new LinkedHashMap<>();
        for (Map.Entry<Integer, StarStage> entry : stars.entrySet()) {
            if (entry.getKey() > currentStar || entry.getValue() == null) {
                continue;
            }
            mergeRaw(rawValues, entry.getValue().stats());
        }
        return resolveExpressions(rawValues, Map.of("star", (double) currentStar));
    }

    /**
     * Computes the cumulative variables as text-aware values for operation templates.
     * Numeric stat calculations should continue using {@link #cumulativeVariables(int)}.
     *
     * @param currentStar the inclusive star ceiling
     * @return resolved variable id to value mapping
     */
    public Map<String, Object> cumulativeMixedVariables(int currentStar) {
        Map<String, Object> rawValues = new LinkedHashMap<>();
        for (Map.Entry<Integer, StarStage> entry : stars.entrySet()) {
            if (entry.getKey() > currentStar || entry.getValue() == null) {
                continue;
            }
            mergeRaw(rawValues, entry.getValue().stats());
        }
        return resolveMixedExpressions(rawValues, Map.of("star", (double) currentStar));
    }

    /**
     * Computes the cumulative attributes granted up to a star level.
     *
     * @param currentStar the inclusive star ceiling
     * @return attribute id to value mapping
     */
    public Map<String, Double> cumulativeAttributes(int currentStar) {
        Map<String, Object> rawValues = new LinkedHashMap<>();
        for (Map.Entry<Integer, StarStage> entry : stars.entrySet()) {
            if (entry.getKey() > currentStar || entry.getValue() == null) {
                continue;
            }
            mergeRaw(rawValues, entry.getValue().attributes());
        }
        return rawValues.isEmpty() ? Map.of() : resolveExpressions(rawValues, Map.of("star", (double) currentStar));
    }

    /**
     * Collects the distinct skill ids granted up to a star level.
     *
     * @param currentStar the inclusive star ceiling
     * @return the ordered, de-duplicated skill ids
     */
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

    /**
     * Computes the stat delta between two star levels.
     *
     * @param fromStar the starting star level
     * @param toStar   the ending star level
     * @return stat id to delta mapping, excluding near-zero changes
     */
    public Map<String, Double> deltaStats(int fromStar, int toStar) {
        Map<String, Double> delta = new LinkedHashMap<>();
        Map<String, Double> from = cumulativeVariables(fromStar);
        Map<String, Double> to = cumulativeVariables(toStar);
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

    /**
     * {@return the stages reached up to a star level, ordered by target star}
     *
     * @param currentStar the inclusive star ceiling
     */
    public List<StarStage> reachedStages(int currentStar) {
        List<StarStage> result = new ArrayList<>();
        for (Map.Entry<Integer, StarStage> entry : stars.entrySet()) {
            if (entry.getKey() <= currentStar && entry.getValue() != null) {
                result.add(entry.getValue());
            }
        }
        result.sort(Comparator.comparingInt(StarStage::targetStar));
        return List.copyOf(result);
    }

    /**
     * {@return the stage for a target star, or {@code null} if undefined}
     *
     * @param targetStar the target star level
     */
    public StarStage stage(int targetStar) {
        return stars.get(targetStar);
    }

    /**
     * Branch-aware variant of {@link #stage(int)}.
     *
     * <p>Branching recipes may declare their star stages inside {@code branch_tree}
     * instead of the top-level {@code stars} block, in which case {@link #stage(int)}
     * resolves nothing. This variant falls back to the stage owned by the node
     * reached along {@code branchPath}, so a star defined only on a branch is still
     * found.
     *
     * @param targetStar the target star level
     * @param branchPath the slash-separated branch path
     * @return the stage for the target star, or {@code null} if undefined
     */
    public StarStage stage(int targetStar, String branchPath) {
        StarStage direct = stars.get(targetStar);
        if (direct != null || branchTree == null) {
            return direct;
        }
        return branchTree.collectStages(branchPath, targetStar).get(targetStar);
    }

    /**
     * {@return the success actions configured for a target star}
     *
     * @param targetStar the target star level
     */
    public List<String> successActionsForTargetStar(int targetStar) {
        StarStage stage = stage(targetStar);
        return stage == null ? List.of() : stage.successActions();
    }

    /**
     * {@return the failure actions configured for a resulting star}
     *
     * @param resultingStar the star level after a failed attempt
     */
    public List<String> failureActionsForResultStar(int resultingStar) {
        StarStage stage = stage(resultingStar);
        return stage == null ? List.of() : stage.failureActions();
    }

    /**
     * Resolves the effective currencies for a target star, preferring a stage
     * override, then the recipe economy (when enabled).
     *
     * @param targetStar the target star level
     * @return the applicable currency entries; empty when economy is disabled
     */
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

    /**
     * Resolves the success rate for a target star, preferring the recipe-level
     * rate over the supplied global rates.
     *
     * @param globalSuccessRates global per-star rates, may be {@code null}
     * @param targetStar         the target star level
     * @return the success rate as a percentage
     */
    public double successRateForTargetStar(Map<Integer, Double> globalSuccessRates, int targetStar) {
        if (successRates.containsKey(targetStar)) {
            return successRates.getOrDefault(targetStar, 0D);
        }
        return globalSuccessRates == null ? 0D : globalSuccessRates.getOrDefault(targetStar, 0D);
    }

    /** {@return the recipe id} */
    public String id() {
        return id;
    }

    /** {@return the recipe display name} */
    public String displayName() {
        return displayName;
    }

    /** {@return the GUI template id used to render this recipe} */
    public String guiTemplate() {
        return guiTemplate;
    }

    /** {@return the recipe-level economy configuration} */
    public EconomyConfig economy() {
        return economy;
    }

    /** {@return the numeric limits of the recipe} */
    public Limits limits() {
        return limits;
    }

    /** {@return the per-target-star success rates} */
    public Map<Integer, Double> successRates() {
        return successRates;
    }

    /**
     * {@return the accepted slot groups, empty when unrestricted}
     *
     * <p>Combined with {@link #matcherConfig()}, {@link #statsAny()} and
     * {@link #sourcePatterns()} as a logical AND.
     */
    public List<String> slotGroups() {
        return slotGroups;
    }

    /**
     * {@return the any-of stat ids that must be present, empty when unrestricted}
     *
     * <p>Combined with {@link #matcherConfig()}, {@link #slotGroups()} and
     * {@link #sourcePatterns()} as a logical AND.
     */
    public List<String> statsAny() {
        return statsAny;
    }

    /**
     * {@return the accepted item source shorthand patterns, empty when unrestricted}
     *
     * <p>Combined with {@link #matcherConfig()}, {@link #slotGroups()} and
     * {@link #statsAny()} as a logical AND.
     */
    public List<String> sourcePatterns() {
        return sourcePatterns;
    }

    /** {@return whether any matching criterion is configured at all} */
    public boolean matchingConfigured() {
        return matcherConfig != null
                || !slotGroups.isEmpty()
                || !statsAny.isEmpty()
                || !sourcePatterns.isEmpty();
    }

    /** {@return the stat-line definitions keyed by id} */
    public Map<String, StatLineDefinition> statLines() {
        return statLines;
    }

    /** {@return the star stages keyed by star level} */
    public Map<Integer, StarStage> stars() {
        return stars;
    }

    /** {@return the activation conditions of the recipe} */
    public StrengthenConditionGroup conditions() {
        return conditions;
    }

    /** {@return the condition combination type (e.g. {@code all_of}/{@code any_of})} */
    public String conditionType() {
        return conditionType;
    }

    /** {@return the required count for any-of conditions} */
    public int conditionRequiredCount() {
        return conditionRequiredCount;
    }

    /** {@return the branching star tree, or {@code null} when linear} */
    public StrengthenBranchNode branchTree() {
        return branchTree;
    }

    /** {@return whether this recipe has a non-empty branch tree} */
    public boolean hasBranchTree() {
        return branchTree != null && !branchTree.children().isEmpty();
    }

    /** {@return the raw name-action configuration, or {@code null}} */
    public Object nameActions() {
        return nameActions;
    }

    /** {@return the raw lore-action configuration, or {@code null}} */
    public Object loreActions() {
        return loreActions;
    }

    /**
     * {@return the raw {@code matcher} configuration, or {@code null} when absent}
     *
     * <p>Evaluated as a logical AND together with {@link #slotGroups()},
     * {@link #statsAny()} and {@link #sourcePatterns()}.
     */
    public Object matcherConfig() {
        return matcherConfig;
    }

    /** {@return whether an optional {@code matcher} configuration is present} */
    public boolean matcherConfigured() {
        return matcherConfig != null;
    }

    /**
     * Collects recipe-level and reached star-stage name actions up to a star.
     *
     * @param currentStar the inclusive star ceiling
     * @param branchPath  the slash-separated branch path
     * @return ordered raw name action configs
     */
    public List<Object> cumulativeNameActions(int currentStar, String branchPath) {
        return cumulativeStageActions(currentStar, branchPath, true);
    }

    /**
     * Collects recipe-level and reached star-stage lore actions up to a star.
     *
     * @param currentStar the inclusive star ceiling
     * @param branchPath  the slash-separated branch path
     * @return ordered raw lore action configs
     */
    public List<Object> cumulativeLoreActions(int currentStar, String branchPath) {
        return cumulativeStageActions(currentStar, branchPath, false);
    }

    private List<Object> cumulativeStageActions(int currentStar, String branchPath, boolean name) {
        List<Object> result = new ArrayList<>();
        Object recipeActions = name ? nameActions : loreActions;
        if (recipeActions != null) {
            result.add(recipeActions);
        }
        List<StarStage> stages = branchTree == null
                ? reachedStages(currentStar)
                : branchTree.collectStages(branchPath, currentStar).values().stream()
                        .sorted(Comparator.comparingInt(StarStage::targetStar))
                        .toList();
        for (StarStage stage : stages) {
            Object actions = name ? stage.nameActions() : stage.loreActions();
            if (actions != null) {
                result.add(actions);
            }
        }
        return List.copyOf(result);
    }

    /**
     * Branch-aware variant of {@link #cumulativeVariables(int)}.
     *
     * @param currentStar the inclusive star ceiling
     * @param branchPath  the slash-separated branch path
     * @return resolved stat id to value mapping
     */
    public Map<String, Double> cumulativeVariables(int currentStar, String branchPath) {
        if (branchTree != null) {
            Map<String, Object> rawValues = new LinkedHashMap<>();
            Map<Integer, StarStage> collected = branchTree.collectStages(branchPath, currentStar);
            for (StarStage stage : collected.values()) {
                if (stage != null) {
                    mergeRaw(rawValues, stage.stats());
                }
            }
            return resolveExpressions(rawValues, Map.of("star", (double) currentStar));
        }
        return cumulativeVariables(currentStar);
    }

    /**
     * Branch-aware variant of {@link #cumulativeMixedVariables(int)}.
     *
     * @param currentStar the inclusive star ceiling
     * @param branchPath  the slash-separated branch path
     * @return resolved variable id to value mapping
     */
    public Map<String, Object> cumulativeMixedVariables(int currentStar, String branchPath) {
        if (branchTree != null) {
            Map<String, Object> rawValues = new LinkedHashMap<>();
            Map<Integer, StarStage> collected = branchTree.collectStages(branchPath, currentStar);
            for (StarStage stage : collected.values()) {
                if (stage != null) {
                    mergeRaw(rawValues, stage.stats());
                }
            }
            return resolveMixedExpressions(rawValues, Map.of("star", (double) currentStar));
        }
        return cumulativeMixedVariables(currentStar);
    }

    /**
     * Branch-aware variant of {@link #cumulativeAttributes(int)}.
     *
     * @param currentStar the inclusive star ceiling
     * @param branchPath  the slash-separated branch path
     * @return attribute id to value mapping
     */
    public Map<String, Double> cumulativeAttributes(int currentStar, String branchPath) {
        if (branchTree != null) {
            Map<String, Object> rawValues = new LinkedHashMap<>();
            Map<Integer, StarStage> collected = branchTree.collectStages(branchPath, currentStar);
            for (StarStage stage : collected.values()) {
                if (stage != null) {
                    mergeRaw(rawValues, stage.attributes());
                }
            }
            return rawValues.isEmpty() ? Map.of() : resolveExpressions(rawValues, Map.of("star", (double) currentStar));
        }
        return cumulativeAttributes(currentStar);
    }

    /**
     * Branch-aware variant of {@link #cumulativeSkillIds(int)}.
     *
     * @param currentStar the inclusive star ceiling
     * @param branchPath  the slash-separated branch path
     * @return the ordered, de-duplicated skill ids
     */
    public List<String> cumulativeSkillIds(int currentStar, String branchPath) {
        if (branchTree != null) {
            LinkedHashSet<String> values = new LinkedHashSet<>();
            Map<Integer, StarStage> collected = branchTree.collectStages(branchPath, currentStar);
            for (StarStage stage : collected.values()) {
                if (stage != null) {
                    values.addAll(stage.skillIds());
                }
            }
            return List.copyOf(values);
        }
        return cumulativeSkillIds(currentStar);
    }

    private static void mergeRaw(Map<String, Object> target, Map<String, Object> source) {
        if (target == null || source == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String key = StrengthenApiValues.lower(entry.getKey());
            Object existing = target.get(key);
            Object incoming = entry.getValue();
            Double existingNum = toDouble(existing);
            Double incomingNum = toDouble(incoming);
            if (existingNum != null && incomingNum != null) {
                target.put(key, existingNum + incomingNum);
            } else {
                target.put(key, incoming);
            }
        }
    }

    static Map<String, Double> resolveExpressions(Map<String, Object> rawValues, Map<String, ?> context) {
        Map<String, Double> resolved = new LinkedHashMap<>();
        if (rawValues == null || rawValues.isEmpty()) {
            return resolved;
        }
        Map<String, Object> evalContext = new LinkedHashMap<>();
        if (context != null) {
            evalContext.putAll(context);
        }
        for (Map.Entry<String, Object> entry : rawValues.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String key = StrengthenApiValues.lower(entry.getKey());
            double value = resolveNumericValue(entry.getValue(), evalContext);
            resolved.put(key, value);
            evalContext.put(key, value);
        }
        return resolved;
    }

    static Map<String, Object> resolveMixedExpressions(Map<String, Object> rawValues, Map<String, ?> context) {
        if (rawValues == null || rawValues.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> resolved = new LinkedHashMap<>();
        Map<String, Object> evalContext = new LinkedHashMap<>();
        if (context != null) {
            evalContext.putAll(context);
        }
        for (Map.Entry<String, Object> entry : rawValues.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String key = StrengthenApiValues.lower(entry.getKey());
            Object value = StrengthenApiValues.evaluateMixed(entry.getValue(), evalContext);
            resolved.put(key, value);
            evalContext.put(key, value);
        }
        return Map.copyOf(resolved);
    }

    private static double resolveNumericValue(Object raw, Map<String, ?> variables) {
        return StrengthenApiValues.evaluateNumber(raw, variables);
    }

    private static Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            return StrengthenApiValues.tryParseDouble(text, null);
        }
        return null;
    }

    static List<String> normalizeLower(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String normalized = StrengthenApiValues.lower(value);
            if (StrengthenApiValues.isNotBlank(normalized)) {
                result.add(normalized);
            }
        }
        return List.copyOf(result);
    }

    static List<String> normalizeStripped(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String normalized = StrengthenApiValues.stripMiniTags(value);
            if (StrengthenApiValues.isNotBlank(normalized)) {
                result.add(normalized);
            }
        }
        return List.copyOf(result);
    }

    static List<String> normalizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String normalized = StrengthenApiValues.toStringSafe(value);
            if (StrengthenApiValues.isNotBlank(normalized)) {
                result.add(normalized);
            }
        }
        return List.copyOf(result);
    }
}
