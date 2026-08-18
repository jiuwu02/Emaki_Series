package emaki.jiuwu.craft.gem.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public final class GemDefinition {

    private final String id;
    private final String displayName;
    private final List<String> lore;
    private final String gemType;
    private final int level;
    private final ItemSourceRef itemSource;
    private final Integer customModelData;
    private final Map<String, Object> stats;
    private final Map<String, Object> attributes;
    private final List<String> skillIds;
    private final Set<String> socketCompatibility;
    private final List<String> dependencies;
    private final List<String> conflicts;
    private final Object nameActions;
    private final Object loreActions;
    private final CostConfig inlayCost;
    private final CostConfig extractCost;
    private final ExtractReturn extractReturn;
    private final StageConfig stages;
    private final List<String> inlaySuccessActions;
    private final List<String> extractSuccessActions;

    public GemDefinition(String id,
            String displayName,
            List<String> lore,
            String gemType,
            int level,
            ItemSourceRef itemSource,
            Integer customModelData,
            Map<String, Object> stats,
            Map<String, Object> attributes,
            List<String> skillIds,
            Set<String> socketCompatibility,
            List<String> dependencies,
            List<String> conflicts,
            Object nameActions,
            Object loreActions,
            CostConfig inlayCost,
            CostConfig extractCost,
            ExtractReturn extractReturn,
            StageConfig stages,
            List<String> inlaySuccessActions,
            List<String> extractSuccessActions) {
        this.id = Texts.lower(id);
        this.displayName = Texts.isBlank(displayName) ? this.id : displayName;
        this.lore = lore == null ? List.of() : List.copyOf(lore);
        this.gemType = Texts.isBlank(gemType) ? "universal" : Texts.lower(gemType);
        this.level = Math.max(1, level);
        this.itemSource = itemSource;
        this.customModelData = customModelData;
        this.stats = stats == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(stats));
        this.attributes = attributes == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(attributes));
        this.skillIds = normalizeSkillIds(skillIds);
        this.socketCompatibility = socketCompatibility == null ? Set.of() : Set.copyOf(socketCompatibility);
        this.dependencies = normalizeGemIds(dependencies);
        this.conflicts = normalizeGemIds(conflicts);
        this.nameActions = ConfigNodes.toPlainData(nameActions);
        this.loreActions = ConfigNodes.toPlainData(loreActions);
        this.inlayCost = inlayCost == null ? CostConfig.none() : inlayCost;
        this.extractCost = extractCost == null ? CostConfig.none() : extractCost;
        this.extractReturn = extractReturn == null ? ExtractReturn.defaults() : extractReturn;
        this.stages = stages == null ? StageConfig.disabled() : stages;
        this.inlaySuccessActions = inlaySuccessActions == null ? List.of() : List.copyOf(inlaySuccessActions);
        this.extractSuccessActions = extractSuccessActions == null ? List.of() : List.copyOf(extractSuccessActions);
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public List<String> lore() {
        return lore;
    }

    public String gemType() {
        return gemType;
    }

    public int level() {
        return level;
    }

    public ItemSourceRef itemSource() {
        return itemSource;
    }

    public Integer customModelData() {
        return customModelData;
    }

    public Map<String, Object> stats() {
        return stats;
    }

    public Map<String, Object> attributes() {
        return attributes;
    }

    public List<String> skillIds() {
        return skillIds;
    }

    public Set<String> socketCompatibility() {
        return socketCompatibility;
    }

    public List<String> dependencies() {
        return dependencies;
    }

    public List<String> conflicts() {
        return conflicts;
    }

    public Object nameActions() {
        return nameActions;
    }

    public Object loreActions() {
        return loreActions;
    }

    public CostConfig inlayCost() {
        return inlayCost;
    }

    public CostConfig extractCost() {
        return extractCost;
    }

    public ExtractReturn extractReturn() {
        return extractReturn;
    }

    public StageConfig stages() {
        return stages;
    }

    public List<String> inlaySuccessActions() {
        return inlaySuccessActions;
    }

    public List<String> extractSuccessActions() {
        return extractSuccessActions;
    }

    public boolean supportsSocketType(String socketType) {
        if (socketCompatibility.isEmpty()) {
            return true;
        }
        String normalized = Texts.lower(socketType);
        return socketCompatibility.contains(normalized) || socketCompatibility.contains("universal");
    }

    public String displayNameForLevel(int level) {
        GemStage stage = stages.stage(level);
        return stage != null && Texts.isNotBlank(stage.displayName()) ? stage.displayName() : displayName;
    }

    public Map<String, Double> statsForLevel(int level) {
        return resolveRawValues(rawStatsForLevel(level), Map.of("level", (double) Math.max(1, level)));
    }

    public Map<String, Double> attributesForLevel(int level) {
        return resolveRawValues(rawAttributesForLevel(level), Map.of("level", (double) Math.max(1, level)));
    }

    private Map<String, Object> rawStatsForLevel(int level) {
        GemStage stage = stages.stage(level);
        return stage == null || stage.stats().isEmpty() ? stats : stage.stats();
    }

    private Map<String, Object> rawAttributesForLevel(int level) {
        GemStage stage = stages.stage(level);
        return stage == null || stage.attributes().isEmpty() ? attributes : stage.attributes();
    }

    public List<String> skillIdsForLevel(int level) {
        GemStage stage = stages.stage(level);
        return stage == null || stage.skillIds().isEmpty() ? skillIds : stage.skillIds();
    }

    public Object nameActionsForLevel(int level) {
        GemStage stage = stages.stage(level);
        return stage != null && stage.nameActions() != null ? stage.nameActions() : nameActions;
    }

    public Object loreActionsForLevel(int level) {
        GemStage stage = stages.stage(level);
        return stage != null && stage.loreActions() != null ? stage.loreActions() : loreActions;
    }

    public Map<String, String> matricesForLevel(int level) {
        GemStage stage = stages.stage(level);
        return stage == null ? Map.of() : stage.matrices();
    }

    public GemStage stage(int level) {
        return stages.stage(level);
    }

    public static GemDefinition fromConfig(YamlSection section) {
        return GemDefinitionParser.parse(section);
    }

    private Map<String, Double> resolveRawValues(Map<String, Object> rawValues, Map<String, ?> context) {
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
            String key = Texts.lower(entry.getKey());
            double value = resolveNumericValue(entry.getValue(), evalContext);
            resolved.put(key, value);
            evalContext.put(key, value);
        }
        return resolved;
    }

    private double resolveNumericValue(Object raw, Map<String, ?> variables) {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        return ExpressionEngine.evaluateRandomConfig(raw, variables);
    }

    static List<String> normalizeSkillIds(List<String> rawSkillIds) {
        return normalizeIds(rawSkillIds);
    }

    private static List<String> normalizeGemIds(List<String> rawGemIds) {
        return normalizeIds(rawGemIds);
    }

    private static List<String> normalizeIds(List<String> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String rawId : rawIds) {
            String id = Texts.normalizeId(rawId);
            if (Texts.isNotBlank(id) && seen.add(id)) {
                result.add(id);
            }
        }
        return List.copyOf(result);
    }

    public record CostConfig(List<CurrencyCost> currencies, List<MaterialCost> materials) {

        public CostConfig {
            currencies = currencies == null ? List.of() : List.copyOf(currencies);
            materials = materials == null ? List.of() : List.copyOf(materials);
        }

        public static CostConfig none() {
            return new CostConfig(List.of(), List.of());
        }

        public static CostConfig fromConfig(YamlSection section) {
            return GemDefinitionParser.parseCostConfig(section);
        }
    }

    public record CurrencyCost(String provider,
            String currencyId,
            double amount,
            double baseCost,
            String costFormula,
            String displayName) {

        public CurrencyCost {
            provider = Texts.isBlank(provider) ? "auto" : Texts.lower(provider);
            currencyId = Texts.toStringSafe(currencyId);
            amount = amount < 0D ? -1D : Math.max(0D, amount);
            baseCost = Math.max(0D, baseCost);
            costFormula = Texts.toStringSafe(costFormula).trim();
            displayName = Texts.toStringSafe(displayName).trim();
        }

        public double resolveAmount(Map<String, ?> variables) {
            if (amount >= 0D) {
                return amount;
            }
            Map<String, Object> evaluationContext = new LinkedHashMap<>();
            if (variables != null) {
                evaluationContext.putAll(variables);
            }
            evaluationContext.putIfAbsent("base_cost", baseCost);
            if (Texts.isBlank(costFormula)) {
                return baseCost;
            }
            return Math.max(0D, ExpressionEngine.evaluate(costFormula, evaluationContext));
        }

        public CurrencyCost resolve(Map<String, ?> variables) {
            double resolvedAmount = resolveAmount(variables);
            if (resolvedAmount <= 0D) {
                return null;
            }
            return new CurrencyCost(provider, currencyId, resolvedAmount, baseCost, costFormula, displayName);
        }

        public static CurrencyCost fromConfig(Object raw) {
            return GemDefinitionParser.parseCurrencyCost(raw);
        }
    }

    public record MaterialCost(ItemSourceRef itemSource, int amount) {

        public MaterialCost {
            amount = Math.max(1, amount);
        }

        public static MaterialCost fromConfig(Object raw) {
            return GemDefinitionParser.parseMaterialCost(raw);
        }
    }

    public record ExtractReturn(String mode, int downgradeLevels, double degradedChance) {

        public ExtractReturn {
            mode = normalizeExtractMode(mode);
            downgradeLevels = Math.max(1, downgradeLevels);
            degradedChance = Math.max(0D, Math.min(1D, degradedChance));
        }

        public static ExtractReturn defaults() {
            return new ExtractReturn("original", 1, 0D);
        }

        public static ExtractReturn fromConfig(YamlSection section) {
            return GemDefinitionParser.parseExtractReturn(section);
        }

        private static String normalizeExtractMode(String mode) {
            return switch (Texts.lower(mode)) {
                case "destroy", "destroyed" -> "destroy";
                case "degraded", "downgrade" -> "downgrade";
                default -> "original";
            };
        }
    }

    public record StageConfig(boolean enabled,
            int maxLevel,
            String guiTemplate,
            Map<Integer, GemStage> stages) {

        public StageConfig {
            maxLevel = Math.max(1, maxLevel);
            guiTemplate = Texts.toStringSafe(guiTemplate).trim();
            stages = stages == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(stages));
        }

        public static StageConfig disabled() {
            return new StageConfig(false, 1, "", Map.of());
        }

        public GemStage stage(int level) {
            return stages.get(Math.max(1, level));
        }

        public static StageConfig fromConfig(YamlSection section) {
            return GemDefinitionParser.parseStageConfig(section);
        }
    }

    public record GemStage(int targetLevel,
            String displayName,
            Map<String, Object> stats,
            Map<String, Object> attributes,
            List<String> skillIds,
            Object nameActions,
            Object loreActions,
            Map<String, String> matrices) {

        public GemStage {
            targetLevel = Math.max(2, targetLevel);
            displayName = Texts.toStringSafe(displayName);
            stats = stats == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(stats));
            attributes = attributes == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(attributes));
            skillIds = normalizeSkillIds(skillIds);
            nameActions = ConfigNodes.toPlainData(nameActions);
            loreActions = ConfigNodes.toPlainData(loreActions);
            matrices = matrices == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(matrices));
        }

        public static GemStage fromConfig(int targetLevel, YamlSection section) {
            return GemDefinitionParser.parseGemStage(targetLevel, section);
        }
    }

}
