package emaki.jiuwu.craft.gem.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

public final class GemDefinition {

    private final String id;
    private final String displayName;
    private final List<String> lore;
    private final String gemType;
    private final int tier;
    private final ItemSource itemSource;
    private final Integer customModelData;
    private final Map<String, Double> stats;
    private final Map<String, Double> eaAttributes;
    private final Set<String> socketCompatibility;
    private final Object structuredPresentation;
    private final CostConfig inlayCost;
    private final CostConfig extractCost;
    private final ExtractReturn extractReturn;
    private final UpgradeConfig upgrade;
    private final List<String> inlaySuccessActions;
    private final List<String> extractSuccessActions;

    public GemDefinition(String id,
            String displayName,
            List<String> lore,
            String gemType,
            int tier,
            ItemSource itemSource,
            Integer customModelData,
            Map<String, Double> stats,
            Map<String, Double> eaAttributes,
            Set<String> socketCompatibility,
            Object structuredPresentation,
            CostConfig inlayCost,
            CostConfig extractCost,
            ExtractReturn extractReturn,
            UpgradeConfig upgrade,
            List<String> inlaySuccessActions,
            List<String> extractSuccessActions) {
        this.id = Texts.lower(id);
        this.displayName = Texts.isBlank(displayName) ? this.id : displayName;
        this.lore = lore == null ? List.of() : List.copyOf(lore);
        this.gemType = Texts.isBlank(gemType) ? "universal" : Texts.lower(gemType);
        this.tier = Math.max(1, tier);
        this.itemSource = itemSource;
        this.customModelData = customModelData;
        this.stats = stats == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(stats));
        this.eaAttributes = eaAttributes == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(eaAttributes));
        this.socketCompatibility = socketCompatibility == null ? Set.of() : Set.copyOf(socketCompatibility);
        this.structuredPresentation = ConfigNodes.toPlainData(structuredPresentation);
        this.inlayCost = inlayCost == null ? CostConfig.none() : inlayCost;
        this.extractCost = extractCost == null ? CostConfig.none() : extractCost;
        this.extractReturn = extractReturn == null ? ExtractReturn.defaults() : extractReturn;
        this.upgrade = upgrade == null ? UpgradeConfig.disabled() : upgrade;
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

    public int tier() {
        return tier;
    }

    public ItemSource itemSource() {
        return itemSource;
    }

    public Integer customModelData() {
        return customModelData;
    }

    public Map<String, Double> stats() {
        return stats;
    }

    public Map<String, Double> eaAttributes() {
        return eaAttributes;
    }

    public Set<String> socketCompatibility() {
        return socketCompatibility;
    }

    public Object structuredPresentation() {
        return structuredPresentation;
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

    public UpgradeConfig upgrade() {
        return upgrade;
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
        GemUpgradeLevel upgradeLevel = upgrade.level(level);
        return upgradeLevel != null && Texts.isNotBlank(upgradeLevel.displayName()) ? upgradeLevel.displayName() : displayName;
    }

    public Map<String, Double> statsForLevel(int level) {
        GemUpgradeLevel upgradeLevel = upgrade.level(level);
        return upgradeLevel == null || upgradeLevel.stats().isEmpty() ? stats : upgradeLevel.stats();
    }

    public Map<String, Double> eaAttributesForLevel(int level) {
        GemUpgradeLevel upgradeLevel = upgrade.level(level);
        return upgradeLevel == null || upgradeLevel.eaAttributes().isEmpty() ? eaAttributes : upgradeLevel.eaAttributes();
    }

    public Object structuredPresentationForLevel(int level) {
        GemUpgradeLevel upgradeLevel = upgrade.level(level);
        return mergeStructuredPresentations(structuredPresentation, upgradeLevel == null ? null : upgradeLevel.structuredPresentation());
    }

    public GemUpgradeLevel upgradeLevel(int level) {
        return upgrade.level(level);
    }

    public static GemDefinition fromConfig(String fallbackId, YamlSection section) {
        if (section == null) {
            return null;
        }
        String id = Texts.lower(section.getString("id", fallbackId));
        if (Texts.isBlank(id)) {
            return null;
        }
        ItemSource itemSource = ItemSourceUtil.parse(section.get("item_source"));
        if (itemSource == null) {
            return null;
        }
        Set<String> socketCompatibility = new LinkedHashSet<>();
        for (String value : section.getStringList("socket_compatibility")) {
            if (Texts.isNotBlank(value)) {
                socketCompatibility.add(Texts.lower(value));
            }
        }
        return new GemDefinition(
                id,
                section.getString("display_name", id),
                section.getStringList("lore"),
                section.getString("gem_type", "universal"),
                section.getInt("tier", 1),
                itemSource,
                Numbers.tryParseInt(section.get("custom_model_data"), null),
                parseStatMap(section.getSection("stats")),
                parseStatMap(section.getSection("ea_attributes")),
                socketCompatibility,
                section.get("structured_presentation"),
                CostConfig.fromConfig(section.getSection("inlay_cost")),
                CostConfig.fromConfig(section.getSection("extract_cost")),
                ExtractReturn.fromConfig(section.getSection("extract_return")),
                UpgradeConfig.fromConfig(section.getSection("upgrade")),
                section.getStringList("inlay_success_actions"),
                section.getStringList("extract_success_actions")
        );
    }

    private static Object mergeStructuredPresentations(Object base, Object override) {
        Object basePlain = ConfigNodes.toPlainData(base);
        Object overridePlain = ConfigNodes.toPlainData(override);
        if (!(basePlain instanceof Map<?, ?>) && !(overridePlain instanceof Map<?, ?>)) {
            return null;
        }
        Map<String, Object> merged = new LinkedHashMap<>();
        mergeStructuredPresentationMap(merged, basePlain);
        mergeStructuredPresentationMap(merged, overridePlain);
        return merged;
    }

    private static void mergeStructuredPresentationMap(Map<String, Object> target, Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return;
        }
        Object baseNamePolicy = map.get("base_name_policy");
        if (baseNamePolicy != null) {
            target.put("base_name_policy", baseNamePolicy);
        }
        Object baseNameTemplate = map.get("base_name_template");
        if (baseNameTemplate != null) {
            target.put("base_name_template", baseNameTemplate);
        }
        List<Object> existingNames = new ArrayList<>(ConfigNodes.asObjectList(target.get("name_contributions")));
        existingNames.addAll(ConfigNodes.asObjectList(map.get("name_contributions")));
        if (!existingNames.isEmpty()) {
            target.put("name_contributions", existingNames);
        }
        List<Object> existingLore = new ArrayList<>(ConfigNodes.asObjectList(target.get("lore_sections")));
        existingLore.addAll(ConfigNodes.asObjectList(map.get("lore_sections")));
        if (!existingLore.isEmpty()) {
            target.put("lore_sections", existingLore);
        }
    }

    private static Map<String, Double> parseStatMap(YamlSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, Double> stats = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Double value = Numbers.tryParseDouble(section.get(key), null);
            if (value != null) {
                stats.put(Texts.lower(key), value);
            }
        }
        return Map.copyOf(stats);
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
            if (section == null) {
                return none();
            }
            List<CurrencyCost> currencies = new ArrayList<>();
            for (Map<?, ?> map : section.getMapList("currencies")) {
                CurrencyCost currencyCost = CurrencyCost.fromConfig(map);
                if (currencyCost != null) {
                    currencies.add(currencyCost);
                }
            }
            List<MaterialCost> materials = new ArrayList<>();
            for (Map<?, ?> map : section.getMapList("materials")) {
                MaterialCost materialCost = MaterialCost.fromConfig(map);
                if (materialCost != null) {
                    materials.add(materialCost);
                }
            }
            return new CostConfig(currencies, materials);
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
            return Math.max(0D, emaki.jiuwu.craft.corelib.expression.ExpressionEngine.evaluate(costFormula, evaluationContext));
        }

        public CurrencyCost resolve(Map<String, ?> variables) {
            double resolvedAmount = resolveAmount(variables);
            if (resolvedAmount <= 0D) {
                return null;
            }
            return new CurrencyCost(provider, currencyId, resolvedAmount, baseCost, costFormula, displayName);
        }

        public static CurrencyCost fromConfig(Object raw) {
            Double configuredAmount = Numbers.tryParseDouble(ConfigNodes.get(raw, "amount"), null);
            double baseCost = Numbers.tryParseDouble(ConfigNodes.get(raw, "base_cost"), 0D);
            String costFormula = ConfigNodes.string(raw, "cost_formula", "");
            if ((configuredAmount == null || configuredAmount <= 0D) && baseCost <= 0D && Texts.isBlank(costFormula)) {
                return null;
            }
            return new CurrencyCost(
                    ConfigNodes.string(raw, "provider", "auto"),
                    ConfigNodes.string(raw, "currency_id", ""),
                    configuredAmount == null ? -1D : configuredAmount,
                    baseCost,
                    costFormula,
                    ConfigNodes.string(raw, "display_name", "")
            );
        }
    }

    public record MaterialCost(ItemSource itemSource, int amount) {

        public MaterialCost {
            amount = Math.max(1, amount);
        }

        public static MaterialCost fromConfig(Object raw) {
            ItemSource source = ItemSourceUtil.parse(raw);
            if (source == null) {
                return null;
            }
            return new MaterialCost(source, Numbers.tryParseInt(ConfigNodes.get(raw, "amount"), 1));
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
            if (section == null) {
                return defaults();
            }
            String mode = section.getString("mode", "original");
            return new ExtractReturn(
                    mode,
                    section.getInt("downgrade_levels", 1),
                    section.getDouble("degraded_chance", 0D)
            );
        }

        private static String normalizeExtractMode(String mode) {
            return switch (Texts.lower(mode)) {
                case "destroy", "destroyed" -> "destroy";
                case "degraded", "downgrade" -> "downgrade";
                default -> "original";
            };
        }
    }

    public record UpgradeConfig(boolean enabled,
            int maxLevel,
            List<CurrencyCost> currencies,
            Map<Integer, Double> successRates,
            String guiTemplate,
            String failurePenalty,
            Map<Integer, GemUpgradeLevel> levels) {

        public UpgradeConfig {
            maxLevel = Math.max(1, maxLevel);
            currencies = currencies == null ? List.of() : List.copyOf(currencies);
            successRates = successRates == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(successRates));
            guiTemplate = Texts.toStringSafe(guiTemplate).trim();
            failurePenalty = Texts.isBlank(failurePenalty) ? "none" : Texts.lower(failurePenalty);
            levels = levels == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(levels));
        }

        public static UpgradeConfig disabled() {
            return new UpgradeConfig(false, 1, List.of(), Map.of(), "", "none", Map.of());
        }

        public GemUpgradeLevel level(int level) {
            return levels.get(Math.max(1, level));
        }

        public static UpgradeConfig fromConfig(YamlSection section) {
            if (section == null) {
                return disabled();
            }
            YamlSection economySection = section.getSection("economy");
            List<CurrencyCost> currencies = new ArrayList<>();
            List<Map<?, ?>> configuredCurrencies = economySection != null ? economySection.getMapList("currencies") : section.getMapList("currencies");
            for (Map<?, ?> map : configuredCurrencies) {
                CurrencyCost currencyCost = CurrencyCost.fromConfig(map);
                if (currencyCost != null) {
                    currencies.add(currencyCost);
                }
            }
            Map<Integer, Double> successRates = new LinkedHashMap<>();
            YamlSection successRatesSection = section.getSection("success_rates");
            if (successRatesSection != null) {
                for (String key : successRatesSection.getKeys(false)) {
                    Integer targetLevel = Numbers.tryParseInt(key, null);
                    Double rate = Numbers.tryParseDouble(successRatesSection.get(key), null);
                    if (targetLevel != null && targetLevel > 1 && rate != null) {
                        successRates.put(targetLevel, rate);
                    }
                }
            }
            Map<Integer, GemUpgradeLevel> levels = new LinkedHashMap<>();
            YamlSection levelsSection = section.getSection("levels");
            if (levelsSection != null) {
                for (String key : levelsSection.getKeys(false)) {
                    Integer targetLevel = Numbers.tryParseInt(key, null);
                    if (targetLevel == null || targetLevel <= 1) {
                        continue;
                    }
                    GemUpgradeLevel level = GemUpgradeLevel.fromConfig(targetLevel, levelsSection.getSection(key));
                    if (level != null) {
                        levels.put(level.targetLevel(), level);
                    }
                }
            }
            return new UpgradeConfig(
                    section.getBoolean("enabled", false),
                    section.getInt("max_level", levels.isEmpty() ? 1 : levels.keySet().stream().mapToInt(Integer::intValue).max().orElse(1)),
                    currencies,
                    successRates,
                    section.getString("gui_template", ""),
                    section.getString("failure_penalty", "none"),
                    levels
            );
        }
    }

    public record GemUpgradeLevel(int targetLevel,
            String displayName,
            Map<String, Double> stats,
            Map<String, Double> eaAttributes,
            Object structuredPresentation,
            double successChance,
            List<CurrencyCost> currencies,
            String failurePenalty,
            List<MaterialCost> materials,
            List<String> successActions,
            List<String> failureActions) {

        public GemUpgradeLevel {
            targetLevel = Math.max(2, targetLevel);
            displayName = Texts.toStringSafe(displayName);
            stats = stats == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(stats));
            eaAttributes = eaAttributes == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(eaAttributes));
            structuredPresentation = ConfigNodes.toPlainData(structuredPresentation);
            successChance = successChance < 0D ? -1D : Math.max(0D, Math.min(100D, successChance));
            currencies = currencies == null ? List.of() : List.copyOf(currencies);
            failurePenalty = Texts.toStringSafe(failurePenalty).trim();
            materials = materials == null ? List.of() : List.copyOf(materials);
            successActions = successActions == null ? List.of() : List.copyOf(successActions);
            failureActions = failureActions == null ? List.of() : List.copyOf(failureActions);
        }

        public static GemUpgradeLevel fromConfig(int targetLevel, YamlSection section) {
            if (section == null) {
                return null;
            }
            List<MaterialCost> materials = new ArrayList<>();
            for (Map<?, ?> map : section.getMapList("materials")) {
                MaterialCost materialCost = MaterialCost.fromConfig(map);
                if (materialCost != null) {
                    materials.add(materialCost);
                }
            }
            YamlSection economySection = section.getSection("economy");
            List<CurrencyCost> currencies = new ArrayList<>();
            List<Map<?, ?>> configuredCurrencies = economySection != null ? economySection.getMapList("currencies") : section.getMapList("currencies");
            for (Map<?, ?> map : configuredCurrencies) {
                CurrencyCost currencyCost = CurrencyCost.fromConfig(map);
                if (currencyCost != null) {
                    currencies.add(currencyCost);
                }
            }
            double successChance = section.contains("success_rate")
                    ? section.getDouble("success_rate", 100D)
                    : section.contains("success_chance")
                            ? section.getDouble("success_chance", 100D)
                            : -1D;
            return new GemUpgradeLevel(
                    targetLevel,
                    section.getString("display_name", ""),
                    parseStatMap(section.getSection("stats")),
                    parseStatMap(section.getSection("ea_attributes")),
                    section.get("structured_presentation"),
                    successChance,
                    currencies,
                    section.getString("failure_penalty", ""),
                    materials,
                    section.getStringList("success_actions"),
                    section.getStringList("failure_actions")
            );
        }
    }
}
