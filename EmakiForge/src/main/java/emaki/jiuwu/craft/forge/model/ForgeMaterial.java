package emaki.jiuwu.craft.forge.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.matcher.ItemRequirement;
import emaki.jiuwu.craft.corelib.matcher.MatchContext;
import emaki.jiuwu.craft.corelib.matcher.Matcher;

public final class ForgeMaterial {

    public static final class MaterialEffect {
        private final String type;
        private final Map<String, Object> data;

        public MaterialEffect(String type, Map<String, Object> data) {
            this.type = type;
            this.data = Map.copyOf(data);
        }

        public static MaterialEffect fromConfig(Object raw) {
            return ForgeMaterialParser.parseMaterialEffect(raw);
        }

        public String type() {
            return type;
        }

        public Map<String, Object> data() {
            return data;
        }

        public Object get(String key) {
            return data.get(key);
        }
    }

    public record QualityModifier(String mode, String tier) {
        public static QualityModifier fromEffect(MaterialEffect effect) {
            if (effect == null || !"quality_modify".equals(Texts.lower(effect.type()))) {
                return null;
            }
            String mode = Texts.lower(effect.get("mode"));
            String tier = Texts.toStringSafe(effect.get("tier"));
            if (Texts.isBlank(mode) || Texts.isBlank(tier)) {
                return null;
            }
            return new QualityModifier(mode, tier);
        }

        public boolean forceMode() {
            return "force".equals(Texts.lower(mode));
        }

        public boolean minimumMode() {
            return "minimum".equals(Texts.lower(mode));
        }
    }

    private final String item;
    private final int amount;
    private final boolean optional;
    private final int capacityCost;
    private final List<MaterialEffect> effects;
    private final List<ItemSourceRef> itemSources;
    private final Matcher matcher;
    private final String matcherKey;
    private final String materialId;
    private final String countKey;
    private final String auditId;
    private final boolean materialIdDeclared;
    private final boolean countKeyDeclared;
    private final boolean auditIdDeclared;
    private final ItemRequirement requirement;

    public ForgeMaterial(String item, int amount, boolean optional, int capacityCost,
            List<MaterialEffect> effects, ItemSourceRef source) {
        this(item, amount, optional, capacityCost, effects,
                source == null ? List.of() : List.of(source), null, "", "", "", "");
    }

    public ForgeMaterial(String item, int amount, boolean optional, int capacityCost,
            List<MaterialEffect> effects, ItemSourceRef source, Matcher matcher, String matcherKey) {
        this(item, amount, optional, capacityCost, effects,
                source == null ? List.of() : List.of(source), matcher, matcherKey, "", "", "");
    }

    public ForgeMaterial(String item, int amount, boolean optional, int capacityCost,
            List<MaterialEffect> effects, ItemSourceRef source, Matcher matcher, String matcherKey,
            String materialId) {
        this(item, amount, optional, capacityCost, effects,
                source == null ? List.of() : List.of(source), matcher, matcherKey, materialId, materialId, materialId);
    }

    public ForgeMaterial(String item, int amount, boolean optional, int capacityCost,
            List<MaterialEffect> effects, List<ItemSourceRef> itemSources, Matcher matcher,
            String matcherKey, String materialId, String countKey, String auditId) {
        this(item, amount, optional, capacityCost, effects, itemSources, matcher, matcherKey,
                materialId, countKey, auditId, true, true, true);
    }

    public ForgeMaterial(String item, int amount, boolean optional, int capacityCost,
            List<MaterialEffect> effects, List<ItemSourceRef> itemSources, Matcher matcher,
            String matcherKey, String materialId, String countKey, String auditId,
            boolean materialIdDeclared, boolean countKeyDeclared, boolean auditIdDeclared) {
        this.item = Texts.toStringSafe(item);
        this.amount = amount;
        this.optional = optional;
        this.capacityCost = capacityCost;
        this.effects = List.copyOf(effects == null ? List.of() : effects);
        this.itemSources = List.copyOf(itemSources == null ? List.of() : itemSources);
        this.matcher = matcher;
        this.matcherKey = Texts.toStringSafe(matcherKey);
        this.materialIdDeclared = materialIdDeclared && Texts.isNotBlank(materialId);
        this.countKeyDeclared = countKeyDeclared && Texts.isNotBlank(countKey);
        this.auditIdDeclared = auditIdDeclared && Texts.isNotBlank(auditId);
        ForgeMaterialIdentity identity = ForgeMaterialIdentity.resolve(
                materialId, countKey, auditId, ItemRequirement.sourceIdentity(this.itemSources), this.matcherKey);
        this.materialId = identity.materialId();
        this.countKey = identity.countKey();
        this.auditId = identity.auditId();
        this.requirement = new ItemRequirement(this.itemSources, matcher, this.materialId);
    }

    public static ForgeMaterial fromConfig(YamlSection section) {
        return ForgeMaterialParser.parse((Object) section);
    }

    public static ForgeMaterial fromConfig(Object raw) {
        return ForgeMaterialParser.parse(raw);
    }

    public boolean matches(ItemSourceRef other) {
        return other != null && requirement.matchesSource(other);
    }

    public boolean matches(MatchContext context) {
        return requirement.test(context);
    }

    public ItemRequirement requirement() {
        return requirement;
    }

    public String key() {
        return materialId;
    }

    public String legacySourceKey() {
        String shorthand = itemSources.isEmpty() ? "" : ItemSourceUtil.toShorthand(itemSources.get(0));
        if (Texts.isNotBlank(shorthand)) {
            return Texts.lower(shorthand);
        }
        return Texts.isBlank(matcherKey) ? "" : "matcher:" + Texts.lower(matcherKey);
    }

    public int forgeCapacityBonus() {
        int total = 0;
        for (MaterialEffect effect : effects) {
            if (effect == null || !isForgeCapacityBonusEffect(effect.type())) {
                continue;
            }
            total += resolveEffectAmount(effect);
        }
        return Math.max(0, total);
    }

    public boolean expandsForgeCapacity() {
        return forgeCapacityBonus() > 0;
    }

    public int effectiveCapacityCost() {
        return expandsForgeCapacity() ? 0 : capacityCost;
    }

    public Map<String, Double> statContributions() {
        return resolveStatContributions();
    }

    public Map<String, Double> attributeContributions() {
        Map<String, Object> context = new LinkedHashMap<>();
        Map<String, Double> result = new LinkedHashMap<>();
        for (MaterialEffect effect : effects) {
            if (!"ea_attribute".equals(Texts.lower(effect.type()))) {
                continue;
            }
            for (Map.Entry<String, Object> entry : ConfigNodes.entries(effect.get("ea_attributes")).entrySet()) {
                Double value = resolveAttributeValue(entry.getValue(), context);
                if (value != null) {
                    String key = Texts.lower(entry.getKey());
                    result.merge(key, value, Double::sum);
                    context.put(key, value);
                }
            }
        }
        return result;
    }

    public List<String> skillIds() {
        List<String> result = new ArrayList<>();
        for (MaterialEffect effect : effects) {
            if (!"es_skill".equals(Texts.lower(effect.type()))) {
                continue;
            }
            for (Object rawSkill : ConfigNodes.asObjectList(effect.get("es_skills"))) {
                String skillId = Texts.normalizeId(Texts.toStringSafe(rawSkill));
                if (Texts.isNotBlank(skillId) && !result.contains(skillId)) {
                    result.add(skillId);
                }
            }
        }
        return List.copyOf(result);
    }

    public List<Map<String, Object>> nameModifications() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (MaterialEffect effect : effects) {
            if (!"name_action".equals(Texts.lower(effect.type()))) {
                continue;
            }
            Object actions = effect.get("name_actions");
            if (actions != null) {
                for (Object raw : ConfigNodes.asObjectList(actions)) {
                    Object plain = ConfigNodes.toPlainData(raw);
                    if (plain instanceof Map<?, ?> map) {
                        Map<String, Object> normalized = new LinkedHashMap<>();
                        for (Map.Entry<?, ?> entry : map.entrySet()) {
                            if (entry.getKey() != null) {
                                normalized.put(String.valueOf(entry.getKey()), entry.getValue());
                            }
                        }
                        result.add(normalized);
                    }
                }
            } else {
                result.add(effect.data());
            }
        }
        return result;
    }

    public List<Map<String, Object>> loreActions() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (MaterialEffect effect : effects) {
            if (!"lore_action".equals(Texts.lower(effect.type()))) {
                continue;
            }
            for (Object raw : ConfigNodes.asObjectList(effect.get("lore_actions"))) {
                Object plain = ConfigNodes.toPlainData(raw);
                if (!(plain instanceof Map<?, ?> map)) {
                    continue;
                }
                Map<String, Object> normalized = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    normalized.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                result.add(normalized);
            }
        }
        return result;
    }

    public List<QualityModifier> qualityModifiers() {
        List<QualityModifier> result = new ArrayList<>();
        for (MaterialEffect effect : effects) {
            QualityModifier modifier = QualityModifier.fromEffect(effect);
            if (modifier != null) {
                result.add(modifier);
            }
        }
        return result;
    }

    public Map<String, Object> definitionSignatureData() {
        List<Map<String, Object>> effectData = new ArrayList<>();
        for (MaterialEffect effect : effects) {
            if (effect == null) {
                continue;
            }
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", effect.type());
            map.put("data", effect.data());
            effectData.add(map);
        }
        List<String> sources = itemSources.stream().map(ItemSourceUtil::toShorthand).filter(Texts::isNotBlank).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        if (materialIdDeclared) {
            result.put("material_id", materialId);
        }
        if (countKeyDeclared) {
            result.put("count_key", countKey);
        }
        if (auditIdDeclared) {
            result.put("audit_id", auditId);
        }
        if (!materialIdDeclared || !countKeyDeclared || !auditIdDeclared) {
            result.put("legacy_identity", legacySourceKey());
            result.put("identity_diagnostic", "missing canonical identity field");
        }
        result.put("item_sources", sources);
        result.put("item", item);
        result.put("amount", amount);
        result.put("optional", optional);
        result.put("capacity_cost", capacityCost);
        result.put("effects", effectData);
        if (Texts.isNotBlank(matcherKey)) {
            result.put("matcher", matcherKey);
        }
        return result;
    }

    private Map<String, Double> resolveStatContributions() {
        Map<String, Object> variables = new LinkedHashMap<>();
        Map<String, Double> result = new LinkedHashMap<>();
        for (MaterialEffect effect : effects) {
            if (!"variables".equals(Texts.lower(effect.type()))) {
                continue;
            }
            for (Map.Entry<String, Object> entry : ConfigNodes.entries(effect.get("variables")).entrySet()) {
                String key = Texts.lower(entry.getKey());
                double value = resolveStatValue(entry.getValue(), variables);
                result.merge(key, value, Double::sum);
                variables.put(key, result.get(key));
            }
        }
        return result;
    }

    private static double resolveStatValue(Object raw, Map<String, ?> variables) {
        return ExpressionEngine.evaluateRandomConfig(raw, variables);
    }

    private static Double resolveAttributeValue(Object raw, Map<String, ?> variables) {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        if (raw instanceof Map<?, ?>) {
            return ExpressionEngine.evaluateRandomConfig(raw, variables);
        }
        return Numbers.tryParseDouble(ExpressionEngine.evaluateStringConfig(raw, variables), null);
    }

    private static boolean isForgeCapacityBonusEffect(String type) {
        return "capacity_bonus".equals(Texts.lower(type));
    }

    private static int resolveEffectAmount(MaterialEffect effect) {
        if (effect == null) {
            return 0;
        }
        Object raw = effect.get("value");
        return raw == null ? 0 : Numbers.roundToInt(ExpressionEngine.evaluateRandomConfig(raw));
    }

    public String item() { return item; }
    public String id() { return materialId; }
    public String materialId() { return materialId; }
    public String countKey() { return countKey; }
    public String auditId() { return auditId; }
    public boolean materialIdDeclared() { return materialIdDeclared; }
    public boolean countKeyDeclared() { return countKeyDeclared; }
    public boolean auditIdDeclared() { return auditIdDeclared; }
    public String matcherKey() { return matcherKey; }
    public String displayName() { return item; }
    public int amount() { return amount; }
    public boolean optional() { return optional; }
    public int capacityCost() { return capacityCost; }
    public int priority() { return 0; }
    public List<MaterialEffect> effects() { return effects; }
    public List<ItemSourceRef> itemSources() { return itemSources; }
    public ItemSourceRef source() { return itemSources.isEmpty() ? null : itemSources.get(0); }
    public Matcher matcher() { return matcher; }
}
